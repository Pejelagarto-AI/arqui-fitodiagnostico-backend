package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dominio.excepcion.InvitacionNoVigenteException;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Enlace de invitación a una planta (RF-E1, RN13, D12): un token de UN solo
 * uso con caducidad. Aceptarlo o revocarlo lo consume. Del token solo se
 * guarda su huella ({@code huellaDelToken}); el valor en claro existe una vez,
 * en la respuesta que lo crea, y nadie puede recuperarlo después (D51).
 *
 * <p>Inmutable: cada transición devuelve una copia. Todas las decisiones
 * dependientes del tiempo reciben {@code ahora} como parámetro; el dominio no
 * llama al reloj, así que se prueba con fechas fijas.
 *
 * <p>{@code correoDestino} es opcional: si el dueño dio un correo, se le manda
 * el enlace (aviso INVITACION); si no, el dueño copia el enlace y lo reparte
 * como prefiera.
 */
public record Invitacion(
        UUID id,
        UUID plantaId,
        String huellaDelToken,
        EstadoInvitacion estado,
        Instant creadaEn,
        Instant expiraEn,
        String correoDestino,
        Instant aceptadaEn) {

    /** Cuánto vive un enlace sin usar (D12: caduca). Una semana alcanza para que alguien lo abra sin dejarlo abierto para siempre. */
    public static final Duration VIGENCIA = Duration.ofDays(7);

    public Invitacion {
        if (id == null || plantaId == null) {
            throw new IllegalArgumentException("la invitación requiere id y planta");
        }
        if (huellaDelToken == null || huellaDelToken.isBlank()) {
            throw new IllegalArgumentException("la invitación requiere la huella de su token");
        }
        if (estado == null || creadaEn == null || expiraEn == null) {
            throw new IllegalArgumentException("la invitación requiere estado y fechas");
        }
        if (!expiraEn.isAfter(creadaEn)) {
            throw new IllegalArgumentException("la invitación debe caducar después de crearse");
        }
        correoDestino = correoDestino == null || correoDestino.isBlank()
                ? null : Usuario.normalizarCorreo(correoDestino);
    }

    /** RF-E1: nace PENDIENTE y vigente por {@link #VIGENCIA}. */
    public static Invitacion nueva(UUID plantaId, String huellaDelToken, Instant ahora, String correoDestino) {
        return new Invitacion(UUID.randomUUID(), plantaId, huellaDelToken, EstadoInvitacion.PENDIENTE,
                ahora, ahora.plus(VIGENCIA), correoDestino, null);
    }

    /** El estado efectivo: una PENDIENTE cuya fecha pasó es CADUCADA aunque la base siga diciendo PENDIENTE. */
    public EstadoInvitacion estadoEn(Instant ahora) {
        if (estado == EstadoInvitacion.PENDIENTE && !ahora.isBefore(expiraEn)) {
            return EstadoInvitacion.CADUCADA;
        }
        return estado;
    }

    public boolean estaVigenteEn(Instant ahora) {
        return estadoEn(ahora) == EstadoInvitacion.PENDIENTE;
    }

    /**
     * RF-E2: consume la invitación por aceptación.
     *
     * @throws InvitacionNoVigenteException si ya se aceptó, se revocó o caducó
     */
    public Invitacion aceptada(Instant ahora) {
        exigirVigente(ahora);
        return new Invitacion(id, plantaId, huellaDelToken, EstadoInvitacion.ACEPTADA,
                creadaEn, expiraEn, correoDestino, ahora);
    }

    /**
     * RF-E3: consume la invitación por revocación del dueño.
     *
     * @throws InvitacionNoVigenteException si ya no estaba pendiente
     */
    public Invitacion revocada(Instant ahora) {
        exigirVigente(ahora);
        return new Invitacion(id, plantaId, huellaDelToken, EstadoInvitacion.REVOCADA,
                creadaEn, expiraEn, correoDestino, aceptadaEn);
    }

    /** Falla con el estado efectivo, para que el mensaje diga qué le pasó al enlace. */
    public void exigirVigente(Instant ahora) {
        EstadoInvitacion efectivo = estadoEn(ahora);
        if (efectivo != EstadoInvitacion.PENDIENTE) {
            throw new InvitacionNoVigenteException(efectivo);
        }
    }
}
