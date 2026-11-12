package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dominio.excepcion.RestablecimientoNoVigenteException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RN14: un solo uso, caduca y se invalida si la contraseña cambia por otra vía. */
class SolicitudRestablecimientoTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    private SolicitudRestablecimiento nueva() {
        return SolicitudRestablecimiento.nueva(UUID.randomUUID(), "huella-token", "huella-cred-1", AHORA);
    }

    @Test
    void esValidaAntesDeCaducarConLaMismaCredencial() {
        SolicitudRestablecimiento solicitud = nueva();

        assertThat(solicitud.expiraEn()).isEqualTo(AHORA.plus(Duration.ofHours(1)));
        assertThat(solicitud.esValidaEn(AHORA, "huella-cred-1")).isTrue();
        assertThat(solicitud.esValidaEn(AHORA.plus(Duration.ofMinutes(59)), "huella-cred-1")).isTrue();
    }

    @Test
    void caducaALaHora() {
        assertThat(nueva().esValidaEn(AHORA.plus(SolicitudRestablecimiento.VIGENCIA), "huella-cred-1")).isFalse();
    }

    @Test
    void consumirlaLaMarcaUsadaYYaNoSirve() {
        SolicitudRestablecimiento consumida = nueva().consumida(AHORA, "huella-cred-1");

        assertThat(consumida.usado()).isTrue();
        assertThat(consumida.esValidaEn(AHORA, "huella-cred-1")).isFalse();
        assertThatThrownBy(() -> consumida.consumida(AHORA, "huella-cred-1"))
                .isInstanceOf(RestablecimientoNoVigenteException.class);
    }

    @Test
    void siLaContrasenaCambioPorOtraViaDejaDeSerValida() {
        // RN14: la huella de la credencial vigente ya no es la de cuando se pidió el enlace.
        SolicitudRestablecimiento solicitud = nueva();

        assertThat(solicitud.esValidaEn(AHORA, "huella-cred-2")).isFalse();
        assertThatThrownBy(() -> solicitud.consumida(AHORA, "huella-cred-2"))
                .isInstanceOf(RestablecimientoNoVigenteException.class);
    }

    @Test
    void rechazaDatosIncoherentes() {
        UUID usuario = UUID.randomUUID();
        assertThatThrownBy(() -> SolicitudRestablecimiento.nueva(null, "h", "c", AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SolicitudRestablecimiento.nueva(usuario, " ", "c", AHORA))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SolicitudRestablecimiento.nueva(usuario, "h", null, AHORA))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
