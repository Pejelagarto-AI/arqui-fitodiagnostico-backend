package com.vivero.fitodiagnostico.mascota.modelo;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** REGADA y SALIO_AL_SOL (D134, D135): salto sobre la mediana, confirmado por la lectura siguiente. */
class DetectorDeSaltosDeMascotaTest {

    private final DetectorDeSaltos detector = new DetectorDeSaltos(ParametrosDeMascota.POR_DEFECTO);
    private final UUID planta = UUID.randomUUID();
    private final Instant t0 = Instant.parse("2026-11-16T08:00:00Z");

    /** Una lectura por minuto con estas humedades y luz fija de 500 lux. */
    private List<LecturaReciente> humedades(double... valores) {
        List<LecturaReciente> m = new ArrayList<>();
        for (int i = 0; i < valores.length; i++) {
            m.add(new LecturaReciente(t0.plus(Duration.ofMinutes(i)), valores[i], 500));
        }
        return m;
    }

    private List<LecturaReciente> luces(double... valores) {
        List<LecturaReciente> m = new ArrayList<>();
        for (int i = 0; i < valores.length; i++) {
            m.add(new LecturaReciente(t0.plus(Duration.ofMinutes(i)), 40, valores[i]));
        }
        return m;
    }

    @Test
    void subirQuincePuntosSobreLaMedianaYSostenerloEsRegada() {
        var evento = detector.riego(planta, humedades(30, 31, 29, 30, 30, 48, 50), null);

        assertThat(evento).hasValueSatisfying(e -> {
            assertThat(e.tipo()).isEqualTo(TipoDeEvento.REGADA);
            assertThat(e.ocurridoEn()).isEqualTo(t0.plus(Duration.ofMinutes(5)));   // la candidata, no la confirmación
            assertThat(e.detalle()).containsEntry("referencia", "30.0").containsEntry("valor", "48.0")
                    .containsEntry("confirmacion", "50.0").containsEntry("magnitud", "humedad");
        });
    }

    @Test
    void unaSolaLecturaAltaNuncaDispara() {
        assertThat(detector.riego(planta, humedades(30, 31, 29, 30, 30, 48), null)).isEmpty();
    }

    @Test
    void unPicoQueSubeYBajaSeIgnora() {
        assertThat(detector.riego(planta, humedades(30, 31, 29, 30, 30, 48, 31), null)).isEmpty();
    }

    @Test
    void laMesetaDespuesDelRiegoNoVuelveADisparar() {
        assertThat(detector.riego(planta, humedades(30, 31, 29, 30, 30, 48, 50, 51), null)).isEmpty();
    }

    @Test
    void dentroDelEnfriamientoNoHayOtraRegada() {
        Instant hace10min = t0.plus(Duration.ofMinutes(5)).minus(Duration.ofMinutes(10));
        Instant hace40min = t0.plus(Duration.ofMinutes(5)).minus(Duration.ofMinutes(40));

        assertThat(detector.riego(planta, humedades(30, 31, 29, 30, 30, 48, 50), hace10min)).isEmpty();
        assertThat(detector.riego(planta, humedades(30, 31, 29, 30, 30, 48, 50), hace40min)).isPresent();
    }

    @Test
    void conMenosDeTresLecturasDeReferenciaNoSeDetectaNada() {
        assertThat(detector.riego(planta, humedades(30, 30, 48, 50), null)).isEmpty();
    }

    @Test
    void lasLecturasFueraDeLaVentanaNoCuentanComoReferencia() {
        List<LecturaReciente> m = new ArrayList<>(List.of(
                new LecturaReciente(t0, 30, 500), new LecturaReciente(t0.plusSeconds(60), 30, 500), new LecturaReciente(t0.plusSeconds(120), 30, 500)));
        Instant tarde = t0.plus(Duration.ofHours(2));
        m.add(new LecturaReciente(tarde, 30, 500));
        m.add(new LecturaReciente(tarde.plusSeconds(60), 48, 500));
        m.add(new LecturaReciente(tarde.plusSeconds(120), 50, 500));

        assertThat(detector.riego(planta, m, null)).isEmpty();   // en la última hora solo hay una lectura de referencia
    }

    @Test
    void elOrdenDeLlegadaNoImporta() {
        List<LecturaReciente> m = new ArrayList<>(humedades(30, 31, 29, 30, 30, 48, 50));
        java.util.Collections.reverse(m);   // el historial llega del más reciente al más antiguo

        assertThat(detector.riego(planta, m, null)).isPresent();
    }

    @Test
    void laLuzQueSeDuplicaYSubeDosMilLuxYSeSostieneEsSalirAlSol() {
        assertThat(detector.sol(planta, luces(500, 520, 480, 500, 3000, 3200), null))
                .hasValueSatisfying(e -> assertThat(e.tipo()).isEqualTo(TipoDeEvento.SALIO_AL_SOL));
    }

    @Test
    void unaSubidaPequenaNoEsSol() {
        assertThat(detector.sol(planta, luces(500, 520, 480, 500, 1100, 1200), null)).isEmpty();   // x2 pero < +2000
        assertThat(detector.sol(planta, luces(10, 12, 9, 10, 40, 45), null)).isEmpty();             // la noche: x4 pero +30 lux
        assertThat(detector.sol(planta, luces(5000, 5100, 4900, 5000, 8000, 8100), null)).isEmpty(); // +3000 pero < x2
    }

    @Test
    void laReferenciaDeHumedadEsLaMedianaDeLaUltimaHora() {
        assertThat(detector.humedadDeReferencia(humedades(30, 31, 90, 29, 30), t0.plus(Duration.ofMinutes(4))))
                .contains(30.0);
        assertThat(detector.humedadDeReferencia(List.of(), t0)).isEmpty();
    }
}
