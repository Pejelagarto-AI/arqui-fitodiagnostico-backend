package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dominio.excepcion.SolicitudYaResueltaException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las transiciones de una solicitud de especie (RF-H1, RF-H4). */
class SolicitudEspecieTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private final UUID solicitante = UUID.randomUUID();

    private SolicitudEspecie nueva() {
        return SolicitudEspecie.nueva(solicitante, "  Monstera  ", "la tengo en casa y no está", AHORA);
    }

    @Test
    void nacePendienteSinRespuestaYConElNombreLimpio() {
        SolicitudEspecie solicitud = nueva();

        assertThat(solicitud.estado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        assertThat(solicitud.nombrePropuesto()).isEqualTo("Monstera");
        assertThat(solicitud.respuesta()).isNull();
        assertThat(solicitud.resueltaEn()).isNull();
        assertThat(solicitud.estaPendiente()).isTrue();
    }

    @Test
    void aprobarGuardaRespuestaYFechaYLaRespuestaEsOpcional() {
        SolicitudEspecie conRespuesta = nueva().aprobada("la agregamos esta semana", AHORA.plusSeconds(5));
        SolicitudEspecie sinRespuesta = nueva().aprobada(null, AHORA.plusSeconds(5));

        assertThat(conRespuesta.estado()).isEqualTo(EstadoSolicitud.APROBADA);
        assertThat(conRespuesta.respuesta()).isEqualTo("la agregamos esta semana");
        assertThat(conRespuesta.resueltaEn()).isEqualTo(AHORA.plusSeconds(5));
        assertThat(sinRespuesta.respuesta()).isNull();
    }

    @Test
    void rechazarExigeUnaRespuestaConElMotivo() {
        assertThat(nueva().rechazada("ya está como otra especie", AHORA).estado()).isEqualTo(EstadoSolicitud.RECHAZADA);

        assertThatThrownBy(() -> nueva().rechazada(null, AHORA)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("respuesta");
        assertThatThrownBy(() -> nueva().rechazada("   ", AHORA)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaResolucionNoSeCambiaNiSeRepite() {
        SolicitudEspecie aprobada = nueva().aprobada("ok", AHORA);
        SolicitudEspecie rechazada = nueva().rechazada("no", AHORA);

        assertThatThrownBy(() -> aprobada.rechazada("mejor no", AHORA)).isInstanceOf(SolicitudYaResueltaException.class);
        assertThatThrownBy(() -> aprobada.aprobada("otra vez", AHORA)).isInstanceOf(SolicitudYaResueltaException.class);
        assertThatThrownBy(() -> rechazada.aprobada("mejor sí", AHORA)).isInstanceOf(SolicitudYaResueltaException.class)
                .satisfies(e -> assertThat(((SolicitudYaResueltaException) e).getEstado()).isEqualTo(EstadoSolicitud.RECHAZADA));
    }

    @Test
    void validaNombreYLargos() {
        assertThatThrownBy(() -> SolicitudEspecie.nueva(solicitante, " ", "j", AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SolicitudEspecie.nueva(solicitante, null, "j", AHORA)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SolicitudEspecie.nueva(solicitante, "x".repeat(121), "j", AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SolicitudEspecie.nueva(solicitante, "Monstera", "j".repeat(1001), AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(SolicitudEspecie.nueva(solicitante, "Monstera", null, AHORA).justificacion()).isNull();
    }
}
