package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dominio.excepcion.RestablecimientoNoVigenteException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Enlace de restablecimiento de contraseña (RF-A7, RF-A8, RN14, D13): un token
 * de un solo uso, con caducidad, que se invalida si la contraseña cambia por
 * otra vía. Es la misma figura que {@link Invitacion} (token de un solo uso
 * con caducidad), pero las dos NO comparten abstracción (D52): tienen estados
 * distintos, la invitación se revoca y esta se invalida por un hecho externo.
 *
 * <p>"Se invalida si la contraseña cambia por otra vía" se resuelve sin
 * acoplarse a ninguna de esas vías: al pedir el enlace se guarda la huella de
 * la credencial vigente ({@code huellaDeCredencial}); al usarlo se compara con
 * la de ahora. Si difieren, la contraseña cambió desde entonces, sea por el
 * cambio autenticado (RF-A5) o por un camino que aún no existe.
 */
public record SolicitudRestablecimiento(
        UUID id,
        UUID usuarioId,
        String huellaDelToken,
        String huellaDeCredencial,
        Instant creadaEn,
        Instant expiraEn,
        boolean usado) {

    /** Corta a propósito: el enlace llega por un canal (el correo) que se supone abierto en ese momento. */
    public static final Duration VIGENCIA = Duration.ofHours(1);

    public SolicitudRestablecimiento {
        if (id == null || usuarioId == null) {
            throw new IllegalArgumentException("la solicitud de restablecimiento requiere id y usuario");
        }
        if (huellaDelToken == null || huellaDelToken.isBlank()
                || huellaDeCredencial == null || huellaDeCredencial.isBlank()) {
            throw new IllegalArgumentException("la solicitud de restablecimiento requiere sus huellas");
        }
        if (creadaEn == null || expiraEn == null || !expiraEn.isAfter(creadaEn)) {
            throw new IllegalArgumentException("la solicitud de restablecimiento requiere fechas coherentes");
        }
    }

    public static SolicitudRestablecimiento nueva(UUID usuarioId, String huellaDelToken,
                                                  String huellaDeCredencial, Instant ahora) {
        return new SolicitudRestablecimiento(UUID.randomUUID(), usuarioId, huellaDelToken,
                huellaDeCredencial, ahora, ahora.plus(VIGENCIA), false);
    }

    /** ¿Se puede usar ahora? No usada, no caducada y con la misma credencial con la que se pidió. */
    public boolean esValidaEn(Instant ahora, String huellaDeCredencialActual) {
        return !usado && ahora.isBefore(expiraEn) && huellaDeCredencial.equals(huellaDeCredencialActual);
    }

    /**
     * Consume el enlace (RN14: un solo uso).
     *
     * @throws RestablecimientoNoVigenteException si ya no es válido
     */
    public SolicitudRestablecimiento consumida(Instant ahora, String huellaDeCredencialActual) {
        if (!esValidaEn(ahora, huellaDeCredencialActual)) {
            throw new RestablecimientoNoVigenteException();
        }
        return new SolicitudRestablecimiento(id, usuarioId, huellaDelToken, huellaDeCredencial,
                creadaEn, expiraEn, true);
    }
}
