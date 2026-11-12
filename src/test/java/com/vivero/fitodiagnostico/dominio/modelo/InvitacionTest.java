package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dominio.excepcion.InvitacionNoVigenteException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El ciclo de vida de una invitación (RN13): puro, con fechas fijas, sin base ni reloj. */
class InvitacionTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");
    private final UUID planta = UUID.randomUUID();

    private Invitacion nueva() {
        return Invitacion.nueva(planta, "huella-1", AHORA, null);
    }

    @Test
    void nacePendienteYCaducaALaSemana() {
        Invitacion invitacion = nueva();

        assertThat(invitacion.estado()).isEqualTo(EstadoInvitacion.PENDIENTE);
        assertThat(invitacion.expiraEn()).isEqualTo(AHORA.plus(Invitacion.VIGENCIA)).isEqualTo(AHORA.plus(Duration.ofDays(7)));
        assertThat(invitacion.estaVigenteEn(AHORA)).isTrue();
        assertThat(invitacion.aceptadaEn()).isNull();
    }

    @Test
    void caducadaEsUnEstadoDerivadoDeLaFechaYNoSeEscribe() {
        Invitacion invitacion = nueva();

        assertThat(invitacion.estadoEn(AHORA.plus(Invitacion.VIGENCIA).minusSeconds(1))).isEqualTo(EstadoInvitacion.PENDIENTE);
        assertThat(invitacion.estadoEn(AHORA.plus(Invitacion.VIGENCIA))).isEqualTo(EstadoInvitacion.CADUCADA);
        assertThat(invitacion.estadoEn(AHORA.plus(Duration.ofDays(30)))).isEqualTo(EstadoInvitacion.CADUCADA);
        assertThat(invitacion.estado()).as("el estado guardado no cambia solo").isEqualTo(EstadoInvitacion.PENDIENTE);
    }

    @Test
    void aceptarlaLaConsumeYRegistraCuando() {
        Instant despues = AHORA.plusSeconds(60);

        Invitacion aceptada = nueva().aceptada(despues);

        assertThat(aceptada.estado()).isEqualTo(EstadoInvitacion.ACEPTADA);
        assertThat(aceptada.aceptadaEn()).isEqualTo(despues);
        assertThat(aceptada.estaVigenteEn(despues)).isFalse();
    }

    @Test
    void aceptarDosVecesFallaDiciendoQueYaFueAceptada() {
        Invitacion aceptada = nueva().aceptada(AHORA);

        assertThatThrownBy(() -> aceptada.aceptada(AHORA.plusSeconds(1)))
                .isInstanceOf(InvitacionNoVigenteException.class)
                .satisfies(e -> assertThat(((InvitacionNoVigenteException) e).getEstado()).isEqualTo(EstadoInvitacion.ACEPTADA))
                .hasMessageContaining("ya fue aceptada");
    }

    @Test
    void aceptarUnaCaducadaFallaDiciendoQueCaduco() {
        assertThatThrownBy(() -> nueva().aceptada(AHORA.plus(Invitacion.VIGENCIA)))
                .isInstanceOf(InvitacionNoVigenteException.class)
                .satisfies(e -> assertThat(((InvitacionNoVigenteException) e).getEstado()).isEqualTo(EstadoInvitacion.CADUCADA))
                .hasMessageContaining("caducó");
    }

    @Test
    void revocarlaLaConsumeYYaNoSePuedeAceptarNiRevocarDeNuevo() {
        Invitacion revocada = nueva().revocada(AHORA);

        assertThat(revocada.estado()).isEqualTo(EstadoInvitacion.REVOCADA);
        assertThatThrownBy(() -> revocada.aceptada(AHORA)).isInstanceOf(InvitacionNoVigenteException.class)
                .hasMessageContaining("revocada");
        assertThatThrownBy(() -> revocada.revocada(AHORA)).isInstanceOf(InvitacionNoVigenteException.class);
    }

    @Test
    void unaYaAceptadaNoSePuedeRevocar() {
        // RF-E3 es para invitaciones NO aceptadas; quitar a quien ya entró es RF-E4.
        assertThatThrownBy(() -> nueva().aceptada(AHORA).revocada(AHORA))
                .isInstanceOf(InvitacionNoVigenteException.class);
    }

    @Test
    void elCorreoDestinoSeNormalizaYEsOpcional() {
        assertThat(Invitacion.nueva(planta, "h", AHORA, "  Ana@Vivero.CO ").correoDestino()).isEqualTo("ana@vivero.co");
        assertThat(Invitacion.nueva(planta, "h", AHORA, "   ").correoDestino()).isNull();
        assertThat(Invitacion.nueva(planta, "h", AHORA, null).correoDestino()).isNull();
        assertThatThrownBy(() -> Invitacion.nueva(planta, "h", AHORA, "sin-arroba"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaDatosIncoherentes() {
        assertThatThrownBy(() -> Invitacion.nueva(null, "h", AHORA, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Invitacion.nueva(planta, " ", AHORA, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Invitacion(UUID.randomUUID(), planta, "h", EstadoInvitacion.PENDIENTE,
                AHORA, AHORA, null, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
