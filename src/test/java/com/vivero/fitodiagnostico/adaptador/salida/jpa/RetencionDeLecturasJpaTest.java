package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.aplicacion.ServicioDeRetencion;
import com.vivero.fitodiagnostico.dobles.RelojManual;
import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaDeLuzAcumulada;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La retención (D176) contra H2 con Flyway real (V22): se siembran tres días viejos y uno reciente, se poda con 30 días
 * y se comprueba que las crudas viejas se fueron, las recientes se quedaron, y que cada hora podada dejó su resumen.
 */
class RetencionDeLecturasJpaTest extends BaseDePersistenciaDePlantas {

    private static final Instant AHORA = Instant.parse("2026-11-16T15:30:07Z");

    @Autowired private AdaptadorHistorialJpa historial;

    private Especie especie;
    private Planta planta;
    private ServicioDeRetencion retencion;

    @BeforeEach
    void preparar() {
        especie = especie();
        planta = planta(usuario("retencion"), especie);
        retencion = new ServicioDeRetencion(historial, new RelojManual(AHORA), Duration.ofDays(30));
        // 14, 15 y 16 de octubre (40 días atrás, viejos), cada minuto, sol entre las 6 y las 18; y el 15 de noviembre.
        for (int dia : new int[]{14, 15, 16}) {
            Instant inicio = Instant.parse("2026-10-" + dia + "T00:00:00Z");
            SiembraDeLecturas.sembrar(jdbc, planta.id(), especie.nombre(), inicio, inicio.plus(Duration.ofDays(1)), 60,
                    t -> t.atZone(ZoneOffset.UTC).getHour() >= 6 && t.atZone(ZoneOffset.UTC).getHour() < 18 ? 800.0 : 0.0);
        }
        Instant ayer = Instant.parse("2026-11-15T00:00:00Z");
        SiembraDeLecturas.sembrar(jdbc, planta.id(), especie.nombre(), ayer, ayer.plus(Duration.ofDays(1)), 60, t -> 800.0);
        sincronizar();
    }

    private int crudas() {
        return jdbc.queryForObject("select count(*) from diagnostico_planta where planta_id = ?", Integer.class, planta.id());
    }

    @Test
    void borraLasCrudasMasViejasQueLaRetencionYDejaLasRecientes() {
        assertThat(crudas()).isEqualTo(3 * 1440 + 1440);

        int borradas = retencion.podar();
        sincronizar();

        assertThat(borradas).isEqualTo(3 * 1440);
        assertThat(crudas()).isEqualTo(1440);
        assertThat(jdbc.queryForObject("select count(*) from diagnostico_parametro p where not exists "
                + "(select 1 from diagnostico_planta d where d.id = p.diagnostico_id)", Integer.class)).isZero();   // cascada
    }

    @Test
    void cadaHoraPodadaDejaSuResumenYLosResumenesNoSeBorran() {
        retencion.podar();
        sincronizar();

        // 72 horas podadas (3 días) más la hora siguiente al corte, que se asegura antes de borrar.
        assertThat(jdbc.queryForObject("select count(*) from resumen_luz_hora where planta_id = ? and lecturas > 0",
                Integer.class, planta.id())).isEqualTo(72);
        assertThat(jdbc.queryForObject("select sum(lecturas) from resumen_luz_hora where planta_id = ?",
                Integer.class, planta.id())).isEqualTo(3 * 1440);
        assertThat(jdbc.queryForObject("select sum(segundos_con_luz) from resumen_luz_hora where planta_id = ? "
                + "and hora >= ? and hora < ?", Double.class, planta.id(), Timestamp.from(Instant.parse("2026-10-15T00:00:00Z")),
                Timestamp.from(Instant.parse("2026-10-16T00:00:00Z")))).isEqualTo(12 * 3600.0);   // las 12 h de sol del 15
    }

    @Test
    void podarDosVecesNoBorraNadaMasNiRompeLosResumenes() {
        retencion.podar();
        int resumenes = jdbc.queryForObject("select count(*) from resumen_luz_hora", Integer.class);

        assertThat(retencion.podar()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from resumen_luz_hora", Integer.class)).isEqualTo(resumenes);
    }

    @Test
    void despuesDePodarLaLuzDeHoySigueSaliendoDeLosResumenes() {
        // La planta sigue reportando: la serie de luz de las últimas 24 h no depende de las crudas viejas.
        Instant hoy = Instant.parse("2026-11-16T00:00:00Z");
        SiembraDeLecturas.sembrar(jdbc, planta.id(), especie.nombre(), hoy, AHORA, 60, t -> 800.0);
        retencion.podar();
        sincronizar();

        var serie = diagnosticos.serieDe(planta.id(), Magnitud.LUZ, AHORA.minus(ReglaDeLuzAcumulada.VENTANA), AHORA);
        var exposicion = new ReglaDeLuzAcumulada().medir(serie, AHORA, 800).orElseThrow();

        assertThat(new Rango(500, 1000).clasificar(exposicion.luxEfectivo())).isEqualTo(Clasificacion.OPTIMO);
        assertThat(exposicion.horasObservadas()).isGreaterThan(22.0);
    }
}
