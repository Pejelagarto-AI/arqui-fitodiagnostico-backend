package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dobles.RelojManual;
import com.vivero.fitodiagnostico.historial.AlmacenDeHistorial;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La política de retención (D176) con un almacén que solo anota las tandas que le piden. */
class ServicioDeRetencionTest {

    private record Tanda(UUID planta, Instant desde, Instant hasta) { }

    private static class AlmacenAnotador implements AlmacenDeHistorial {
        final List<UUID> conViejas = new ArrayList<>();
        Instant primera = Instant.parse("2026-09-01T10:20:00Z");
        final List<Tanda> tandas = new ArrayList<>();
        UUID quePuedeFallar;

        @Override
        public List<UUID> plantasConLecturasAnterioresA(Instant corte) {
            return conViejas;
        }

        @Override
        public Optional<Instant> primeraLecturaDe(UUID plantaId) {
            return Optional.of(primera);
        }

        @Override
        public int resumirYBorrar(UUID plantaId, Instant desde, Instant hasta) {
            if (plantaId.equals(quePuedeFallar)) {
                throw new IllegalStateException("base caída");
            }
            tandas.add(new Tanda(plantaId, desde, hasta));
            return 10;
        }
    }

    private final RelojManual reloj = new RelojManual(Instant.parse("2026-11-16T15:30:07Z"));
    private final AlmacenAnotador almacen = new AlmacenAnotador();

    @Test
    void cortaEnHoraEnPuntoATreintaDiasYPodaPorTandasDeUnDiaDesdeLaPrimeraLectura() {
        UUID planta = UUID.randomUUID();
        almacen.conViejas.add(planta);
        almacen.primera = Instant.parse("2026-10-15T10:20:00Z");

        int borradas = new ServicioDeRetencion(almacen, reloj, Duration.ofDays(30)).podar();

        // Corte: 16-nov 15:30 menos 30 días = 17-oct 15:30, a la hora en punto: 17-oct 15:00.
        Instant corte = Instant.parse("2026-10-17T15:00:00Z");
        assertThat(almacen.tandas).hasSize(3);
        assertThat(almacen.tandas.get(0).desde()).isEqualTo(Instant.parse("2026-10-15T10:00:00Z"));   // la hora de la primera
        assertThat(almacen.tandas.get(0).hasta()).isEqualTo(Instant.parse("2026-10-16T10:00:00Z"));
        assertThat(almacen.tandas.get(2).hasta()).isEqualTo(corte);                                    // la última, hasta el corte
        assertThat(almacen.tandas).allSatisfy(t -> assertThat(t.desde().isBefore(corte)).isTrue());
        assertThat(borradas).isEqualTo(30);
    }

    @Test
    void sinPlantasConLecturasViejasNoHaceNada() {
        assertThat(new ServicioDeRetencion(almacen, reloj, Duration.ofDays(30)).podar()).isZero();
        assertThat(almacen.tandas).isEmpty();
    }

    @Test
    void unaPlantaQueFallaNoFrenaALasDemas() {
        UUID mala = UUID.randomUUID();
        UUID buena = UUID.randomUUID();
        almacen.conViejas.addAll(List.of(mala, buena));
        almacen.quePuedeFallar = mala;
        almacen.primera = Instant.parse("2026-10-16T00:00:00Z");

        int borradas = new ServicioDeRetencion(almacen, reloj, Duration.ofDays(30)).podar();

        assertThat(almacen.tandas).extracting(Tanda::planta).containsOnly(buena);
        assertThat(borradas).isEqualTo(20);   // dos tandas de la buena (16 y 17 de oct hasta el corte)
    }

    @Test
    void laRetencionNoPuedeSerMenorDeCuarentaYOchoHoras() {
        assertThatThrownBy(() -> new ServicioDeRetencion(almacen, reloj, Duration.ofHours(24)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("48");
    }
}
