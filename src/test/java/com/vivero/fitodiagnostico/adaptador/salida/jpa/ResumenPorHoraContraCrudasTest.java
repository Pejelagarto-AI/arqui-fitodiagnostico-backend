package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.ExposicionLuminica;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Muestra;
import com.vivero.fitodiagnostico.dominio.modelo.Periodo;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.SerieTemporal;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaDeLuzAcumulada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Fase 12 (D175): el resumen por hora tiene que dar la MISMA luz que las lecturas crudas. Se siembra un día de lecturas
 * cada 30 s (suficiente para que cada hora tenga 120 puntos y la prueba corra en segundos) y se pasa la serie por la
 * regla real del dominio por los dos caminos: las crudas de la misma ventana y {@code serieDe} del adaptador (resúmenes
 * más la hora en curso). Luego, contra las crudas de las 24 h completas: la aproximación de la hora parcial más antigua
 * no cambia la clasificación en estos casos.
 */
class ResumenPorHoraContraCrudasTest extends BaseDePersistenciaDePlantas {

    private static final Instant TARDE = Instant.parse("2026-11-16T15:30:07Z");
    private final ReglaDeLuzAcumulada regla = new ReglaDeLuzAcumulada();
    private final Rango rango = new Rango(500, 1000);   // la especie de las pruebas

    private Especie especie;
    private Planta planta;

    @BeforeEach
    void preparar() {
        especie = especie();
        planta = planta(usuario("resumen"), especie);
    }

    private static int hora(Instant t) {
        return t.atZone(ZoneOffset.UTC).getHour();
    }

    private void sembrar(Instant hasta, Function<Instant, Double> lux) {
        SiembraDeLecturas.sembrar(jdbc, planta.id(), especie.nombre(), hasta.minus(Duration.ofHours(25)), hasta, 30, lux);
        sincronizar();
    }

    private SerieTemporal crudas(Instant desde, Instant hasta) {
        return SerieTemporal.de(jdbc.query("select d.tomada_en, p.valor from diagnostico_planta d "
                        + "join diagnostico_parametro p on p.diagnostico_id = d.id where d.planta_id = ? and p.magnitud = 'LUZ' "
                        + "and d.tomada_en >= ? and d.tomada_en < ? order by d.tomada_en",
                (rs, n) -> new Muestra(rs.getTimestamp(1).toInstant(), rs.getDouble(2)),
                planta.id(), Timestamp.from(desde), Timestamp.from(hasta)));
    }

    private ExposicionLuminica medir(SerieTemporal serie, Instant hasta, double luxAhora) {
        return regla.medir(serie, hasta, luxAhora).orElseThrow();
    }

    /** Las dos mediciones: crudas de la misma ventana (lo exacto), resumen, y crudas de las 24 h (lo que hacía antes). */
    private void comparar(Instant hasta, double luxAhora, double toleranciaRelativa) {
        Instant desde = hasta.minus(ReglaDeLuzAcumulada.VENTANA);
        Instant primeraCerrada = ResumenesDeLuz.primeraHoraDesde(desde);
        SerieTemporal delResumen = diagnosticos.serieDe(planta.id(), Magnitud.LUZ, desde, hasta);
        ExposicionLuminica conResumen = medir(delResumen, hasta, luxAhora);
        ExposicionLuminica mismaVentana = medir(crudas(primeraCerrada, hasta), hasta, luxAhora);
        ExposicionLuminica de24h = medir(crudas(desde, hasta), hasta, luxAhora);

        assertThat(delResumen.muestras().size()).isLessThan(crudas(desde, hasta).muestras().size() / 4);
        // Lo exacto: las mismas horas, los mismos números (a segundos de diferencia por hora).
        assertThat(conResumen.horasObservadas()).isCloseTo(mismaVentana.horasObservadas(), within(0.05));
        assertThat(conResumen.horasDeLuz()).isCloseTo(mismaVentana.horasDeLuz(), within(0.05));
        assertThat(conResumen.luxEfectivo()).isCloseTo(mismaVentana.luxEfectivo(),
                within(Math.max(1.0, mismaVentana.luxEfectivo() * toleranciaRelativa)));
        assertThat(conResumen.horasEnOscuridad()).isCloseTo(mismaVentana.horasEnOscuridad(), within(0.01));
        // Lo que importa: la misma clasificación y el mismo periodo que con las 24 h completas.
        assertThat(rango.clasificar(conResumen.luxEfectivo())).isEqualTo(rango.clasificar(de24h.luxEfectivo()));
        assertThat(regla.periodo(conResumen)).isEqualTo(regla.periodo(de24h));
    }

    @Test
    void unDiaDeSolDeSeisAseisDaLaMismaLuzOptima() {
        sembrar(TARDE, t -> hora(t) >= 6 && hora(t) < 18 ? 800.0 : 0.0);

        comparar(TARDE, 800, 0.005);
        assertThat(rango.clasificar(medir(diagnosticos.serieDe(planta.id(), Magnitud.LUZ,
                TARDE.minus(Duration.ofHours(24)), TARDE), TARDE, 800).luxEfectivo())).isEqualTo(Clasificacion.OPTIMO);
    }

    @Test
    void deNocheDespuesDeUnBuenDiaEsNocheConLasDosSeries() {
        Instant noche = Instant.parse("2026-11-16T22:30:07Z");
        sembrar(noche, t -> hora(t) >= 6 && hora(t) < 18 ? 800.0 : 0.0);

        comparar(noche, 0, 0.005);
        ExposicionLuminica e = medir(diagnosticos.serieDe(planta.id(), Magnitud.LUZ, noche.minus(Duration.ofHours(24)),
                noche), noche, 0);
        assertThat(regla.periodo(e)).isEqualTo(Periodo.NOCHE);
        assertThat(e.horasEnOscuridad()).isCloseTo(4.5, within(0.01));
    }

    @Test
    void trasHorasDeSolPocasLaLuzSigueSiendoBajaConLasDosSeries() {
        sembrar(TARDE, t -> hora(t) >= 9 && hora(t) < 12 ? 800.0 : 0.0);

        comparar(TARDE, 0, 0.005);
        assertThat(rango.clasificar(medir(diagnosticos.serieDe(planta.id(), Magnitud.LUZ,
                TARDE.minus(Duration.ofHours(24)), TARDE), TARDE, 0).luxEfectivo())).isEqualTo(Clasificacion.BAJO);
    }

    @Test
    void conSolDirectoFuerteLaLuzEsAltaConLasDosSeries() {
        sembrar(TARDE, t -> hora(t) >= 8 && hora(t) < 16 ? 4000.0 : 0.0);

        comparar(TARDE, 4000, 0.005);
        assertThat(rango.clasificar(medir(diagnosticos.serieDe(planta.id(), Magnitud.LUZ,
                TARDE.minus(Duration.ofHours(24)), TARDE), TARDE, 4000).luxEfectivo())).isEqualTo(Clasificacion.ALTO);
    }

    @Test
    void unaMateraApagadaHorasYUnaLamparaDeNocheDanLoMismo() {
        // Sol de 7 a 17, apagada de 11:10 a 12:20 (hueco de más de 30 min: el tope de la regla cuenta solo 30), y una
        // lámpara de 150 lux entre las 19 y las 22 de la noche anterior.
        sembrar(TARDE, t -> {
            Instant apagadaDesde = Instant.parse("2026-11-16T11:10:00Z");
            if (!t.isBefore(apagadaDesde) && t.isBefore(Instant.parse("2026-11-16T12:20:00Z"))) {
                return null;
            }
            int h = hora(t);
            boolean ayer = t.isBefore(Instant.parse("2026-11-16T00:00:00Z"));
            if (ayer && h >= 19 && h < 22) {
                return 150.0;
            }
            return !ayer && h >= 7 && h < 17 ? 900.0 : 0.0;
        });

        comparar(TARDE, 900, 0.01);
    }

    @Test
    void laNocheSeDeclaraAlLlegarALasDosHorasExactasYNoAntes() {
        // Oscuridad desde las 13:30:00 (primera lectura oscura): a las 15:30:00 son 2 h exactas, hay luz antes.
        sembrar(Instant.parse("2026-11-16T15:30:30Z"), t -> t.isBefore(Instant.parse("2026-11-16T13:30:00Z"))
                && hora(t) >= 6 ? 700.0 : 0.0);
        Instant dosHoras = Instant.parse("2026-11-16T15:30:00Z");
        Instant casi = Instant.parse("2026-11-16T15:29:00Z");

        ExposicionLuminica enElBorde = medir(diagnosticos.serieDe(planta.id(), Magnitud.LUZ,
                dosHoras.minus(Duration.ofHours(24)), dosHoras), dosHoras, 0);
        ExposicionLuminica unMinutoAntes = medir(diagnosticos.serieDe(planta.id(), Magnitud.LUZ,
                casi.minus(Duration.ofHours(24)), casi), casi, 0);

        assertThat(regla.periodo(enElBorde)).isEqualTo(Periodo.NOCHE);
        assertThat(regla.periodo(unMinutoAntes)).isEqualTo(Periodo.DIA);
    }

    @Test
    void unaLecturaAtrasadaInvalidaElResumenDeSuHoraYElDeLaSiguiente() {
        sembrar(TARDE, t -> hora(t) >= 6 && hora(t) < 18 ? 800.0 : 0.0);
        diagnosticos.serieDe(planta.id(), Magnitud.LUZ, TARDE.minus(Duration.ofHours(24)), TARDE);   // llena los resúmenes
        Instant hora9 = Instant.parse("2026-11-16T09:00:00Z");
        assertThat(filasDeResumen(hora9)).isEqualTo(1);

        // Llega ahora una lectura de las 9:10 (cola sin red): su resumen y el de las 10 ya no valen.
        var atrasada = com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta.deUnaEvaluacion(planta.id(),
                com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion.DISPOSITIVO,
                Instant.parse("2026-11-16T09:10:15Z"), evaluar(especie, com.vivero.fitodiagnostico.dobles.Muestras
                        .medicionSaludable()));
        diagnosticos.guardar(atrasada);
        sincronizar();

        assertThat(filasDeResumen(hora9)).isZero();
        assertThat(filasDeResumen(Instant.parse("2026-11-16T10:00:00Z"))).isZero();
        assertThat(filasDeResumen(Instant.parse("2026-11-16T11:00:00Z"))).isEqualTo(1);
        diagnosticos.serieDe(planta.id(), Magnitud.LUZ, TARDE.minus(Duration.ofHours(24)), TARDE);   // se recalculan
        assertThat(filasDeResumen(hora9)).isEqualTo(1);
    }

    @Test
    void unaPlantaNuevaOSinLecturasNoDejaResumenesBasura() {
        List<Muestra> vacia = diagnosticos.serieDe(planta.id(), Magnitud.LUZ, TARDE.minus(Duration.ofHours(24)), TARDE)
                .muestras();

        assertThat(vacia).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from resumen_luz_hora where planta_id = ? and lecturas > 0",
                Integer.class, planta.id())).isZero();
    }

    private int filasDeResumen(Instant hora) {
        return jdbc.queryForObject("select count(*) from resumen_luz_hora where planta_id = ? and hora = ?",
                Integer.class, planta.id(), Timestamp.from(hora));
    }

}
