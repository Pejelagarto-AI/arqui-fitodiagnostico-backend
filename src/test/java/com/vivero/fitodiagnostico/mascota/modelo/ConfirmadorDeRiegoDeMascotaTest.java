package com.vivero.fitodiagnostico.mascota.modelo;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** "La regué" (D138): el sensor confirma o, al vencer el plazo de 15 minutos, desmiente. */
class ConfirmadorDeRiegoDeMascotaTest {

    private final ConfirmadorDeRiego confirmador = new ConfirmadorDeRiego(ParametrosDeMascota.POR_DEFECTO);
    private final UUID planta = UUID.randomUUID();
    private final Instant t0 = Instant.parse("2026-11-16T08:00:00Z");
    private final AccionDeMascota pendiente = AccionDeMascota.riegoDeclarado(planta, t0, t0.plus(Duration.ofMinutes(15)), 30.0);

    private Muestra a(int minutos, double humedad) {
        return new Muestra(t0.plus(Duration.ofMinutes(minutos)), humedad, 500);
    }

    @Test
    void unaRegadaDetectadaPocoAntesDeDeclararLaConfirma() {
        var r = confirmador.evaluar(pendiente, List.of(), t0.minus(Duration.ofMinutes(5)), t0);

        assertThat(r).hasValueSatisfying(x -> {
            assertThat(x.accion().estado()).isEqualTo(EstadoDeAccion.CONFIRMADA);
            assertThat(x.accion().resueltaEn()).isEqualTo(t0);
            assertThat(x.evento().tipo()).isEqualTo(TipoDeEvento.RIEGO_CONFIRMADO);
            assertThat(x.evento().detalle()).containsEntry("motivo", "REGADA_DETECTADA")
                    .containsEntry("accionId", pendiente.id().toString());
        });
    }

    @Test
    void unaRegadaViejaNoCuenta() {
        assertThat(confirmador.evaluar(pendiente, List.of(), t0.minus(Duration.ofMinutes(40)), t0.plusSeconds(60))).isEmpty();
    }

    @Test
    void dosLecturasSeguidasSobreLaReferenciaLaConfirman() {
        assertThat(confirmador.evaluar(pendiente, List.of(a(1, 31), a(2, 46)), null, t0.plus(Duration.ofMinutes(2)))).isEmpty();

        var r = confirmador.evaluar(pendiente, List.of(a(1, 31), a(2, 46), a(3, 47)), null, t0.plus(Duration.ofMinutes(3)));
        assertThat(r).hasValueSatisfying(x -> {
            assertThat(x.accion().estado()).isEqualTo(EstadoDeAccion.CONFIRMADA);
            assertThat(x.evento().detalle()).containsEntry("motivo", "HUMEDAD_SUBIO");
        });
    }

    @Test
    void lasLecturasAnterioresALaDeclaracionNoConfirman() {
        assertThat(confirmador.evaluar(pendiente, List.of(a(-2, 46), a(-1, 47)), null, t0.plusSeconds(30))).isEmpty();
    }

    @Test
    void alVencerSinLecturasSeDesmienteConEseMotivo() {
        var r = confirmador.evaluar(pendiente, List.of(), null, t0.plus(Duration.ofMinutes(15)));

        assertThat(r).hasValueSatisfying(x -> {
            assertThat(x.accion().estado()).isEqualTo(EstadoDeAccion.DESMENTIDA);
            assertThat(x.evento().tipo()).isEqualTo(TipoDeEvento.RIEGO_DESMENTIDO);
            assertThat(x.evento().detalle()).containsEntry("motivo", "SIN_LECTURAS");
        });
    }

    @Test
    void alVencerConLecturasQueNoSubieronSeDesmiente() {
        var r = confirmador.evaluar(pendiente, List.of(a(3, 31), a(6, 33), a(9, 32)), null, t0.plus(Duration.ofMinutes(16)));

        assertThat(r.orElseThrow().evento().detalle()).containsEntry("motivo", "LA_HUMEDAD_NO_SUBIO");
    }

    @Test
    void unaAccionYaResueltaNoSeVuelveAEvaluar() {
        AccionDeMascota resuelta = pendiente.resuelta(EstadoDeAccion.CONFIRMADA, t0);

        assertThat(confirmador.evaluar(resuelta, List.of(), null, t0.plus(Duration.ofHours(1)))).isEmpty();
    }
}
