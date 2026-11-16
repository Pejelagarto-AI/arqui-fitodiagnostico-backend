package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.ExposicionLuminica;
import com.vivero.fitodiagnostico.dominio.modelo.Muestra;
import com.vivero.fitodiagnostico.dominio.modelo.SerieTemporal;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaDeLuzAcumulada;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** El resumen por hora, puro (D175): calcula los números de la regla y los vuelve a convertir en muestras. */
class ResumenDeLuzPorHoraTest {

    private final UUID planta = UUID.randomUUID();
    private final Instant hora = Instant.parse("2026-11-16T10:00:00Z");

    private List<Muestra> cada30s(Instant desde, int cuantas, double lux) {
        List<Muestra> m = new ArrayList<>();
        for (int i = 0; i < cuantas; i++) {
            m.add(new Muestra(desde.plusSeconds(30L * i), lux));
        }
        return m;
    }

    @Test
    void sumaLosTramosConLuzYGuardaLaOscuridadDelFinal() {
        List<Muestra> deLaHora = new ArrayList<>(cada30s(hora, 60, 800));                       // 10:00 a 10:29:30, con luz
        deLaHora.addAll(cada30s(hora.plusSeconds(1800), 60, 5));                                // 10:30 a 10:59:30, oscuro
        Muestra previa = new Muestra(hora.minusSeconds(30), 800);

        ResumenDeLuzPorHora r = ResumenDeLuzPorHora.de(planta, hora, previa, deLaHora);

        assertThat(r.lecturas()).isEqualTo(120);
        assertThat(r.segundosObservados()).isEqualTo(3600);
        assertThat(r.segundosConLuz()).isEqualTo(1800);
        assertThat(r.luxSegundos()).isEqualTo(800 * 1800);
        assertThat(r.segundosEnOscuridadFinal()).isEqualTo(1800);
        assertThat(r.primerTramoOscuro()).isEqualTo(30);
        assertThat(r.ultimaLecturaEn()).isEqualTo(hora.plusSeconds(3570));
    }

    @Test
    void elPrimerTramoSeCuentaDesdeLaPreviaConElTopeYSinPreviaEsCero() {
        List<Muestra> una = List.of(new Muestra(hora.plusSeconds(10), 800));

        assertThat(ResumenDeLuzPorHora.de(planta, hora, new Muestra(hora.minusSeconds(5), 0), una)
                .segundosObservados()).isEqualTo(15);
        assertThat(ResumenDeLuzPorHora.de(planta, hora, new Muestra(hora.minus(Duration.ofHours(3)), 0), una)
                .segundosObservados()).isEqualTo(ReglaDeLuzAcumulada.HUECO_MAXIMO.toSeconds());
        assertThat(ResumenDeLuzPorHora.de(planta, hora, null, una).segundosObservados()).isZero();
    }

    @Test
    void unaHoraSinLecturasNoAportaMuestras() {
        ResumenDeLuzPorHora vacia = ResumenDeLuzPorHora.de(planta, hora, null, List.of());

        assertThat(vacia.lecturas()).isZero();
        assertThat(vacia.ultimaLecturaEn()).isNull();
        assertThat(ResumenDeLuzPorHora.comoSerie(List.of(vacia), hora)).isEmpty();
    }

    @Test
    void lasMuestrasSinteticasReproducenLosNumerosDeLaRegla() {
        // Dos horas: la primera con 40 min de sol a 1000 lux, la segunda oscura. Cada 30 s.
        List<ResumenDeLuzPorHora> resumenes = new ArrayList<>();
        List<Muestra> crudas = new ArrayList<>();
        for (int h = 0; h < 2; h++) {
            Instant inicio = hora.plus(Duration.ofHours(h));
            List<Muestra> deLaHora = new ArrayList<>();
            for (int i = 0; i < 120; i++) {
                Instant t = inicio.plusSeconds(30L * i);
                deLaHora.add(new Muestra(t, h == 0 && i < 80 ? 1000 : 0));
            }
            Muestra previa = crudas.isEmpty() ? null : crudas.get(crudas.size() - 1);
            resumenes.add(ResumenDeLuzPorHora.de(planta, inicio, previa, deLaHora));
            crudas.addAll(deLaHora);
        }
        Instant ahora = hora.plusSeconds(2 * 3600 + 15 * 60);   // 12:15
        ReglaDeLuzAcumulada regla = new ReglaDeLuzAcumulada();

        List<Muestra> sinteticas = ResumenDeLuzPorHora.comoSerie(resumenes, hora);
        ExposicionLuminica conResumen = regla.medir(SerieTemporal.de(sinteticas), ahora, 0).orElseThrow();
        ExposicionLuminica conCrudas = regla.medir(SerieTemporal.de(crudas), ahora, 0).orElseThrow();

        assertThat(sinteticas.size()).isLessThan(15);   // y no 240
        assertThat(conResumen.horasDeLuz()).isCloseTo(conCrudas.horasDeLuz(), within(0.01));
        assertThat(conResumen.luxEfectivo()).isCloseTo(conCrudas.luxEfectivo(), within(1.0));
        assertThat(conResumen.horasEnOscuridad()).isCloseTo(conCrudas.horasEnOscuridad(), within(0.01));
        // Desde las 10:40 (primera lectura oscura) hasta las 12:15.
        assertThat(conResumen.horasEnOscuridad()).isCloseTo(1 + 35 / 60.0, within(0.005));
    }

    @Test
    void nadaQuedaAntesDelInicioDeLaVentana() {
        ResumenDeLuzPorHora r = ResumenDeLuzPorHora.de(planta, hora, new Muestra(hora.minusSeconds(30), 0),
                cada30s(hora, 120, 800));
        Instant desde = hora.plusSeconds(600);   // la ventana empieza 10 min adentro de la hora

        List<Muestra> serie = ResumenDeLuzPorHora.comoSerie(List.of(r), desde);

        assertThat(serie).allSatisfy(m -> assertThat(m.tomadaEn().isBefore(desde)).isFalse());
        assertThat(serie).isSortedAccordingTo((a, b) -> a.tomadaEn().compareTo(b.tomadaEn()));
    }
}
