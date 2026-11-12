package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dominio.excepcion.SolicitudYaResueltaException;

import java.time.Instant;
import java.util.UUID;

/**
 * Petición de un cliente para que el catálogo incluya una especie nueva
 * (RF-H1). Nace PENDIENTE y el administrador la resuelve UNA vez (RF-H4):
 * aprobar o rechazar con una respuesta que el solicitante recibirá (RF-H5).
 *
 * <p>Aprobar NO crea la especie (D55): la solicitud solo trae un nombre y una
 * justificación, y una especie válida exige las tres magnitudes con rangos
 * (D31) que define el administrador con RF-C3. La aprobación es el compromiso
 * de agregarla; el alta es otro acto.
 */
public record SolicitudEspecie(
        UUID id,
        UUID solicitanteId,
        String nombrePropuesto,
        String justificacion,
        EstadoSolicitud estado,
        String respuesta,
        Instant creadaEn,
        Instant resueltaEn) {

    public static final int LARGO_MAXIMO_DEL_NOMBRE = 120;
    public static final int LARGO_MAXIMO_DEL_TEXTO = 1000;

    public SolicitudEspecie {
        if (id == null || solicitanteId == null) {
            throw new IllegalArgumentException("la solicitud requiere id y solicitante");
        }
        if (estado == null || creadaEn == null) {
            throw new IllegalArgumentException("la solicitud requiere estado y fecha");
        }
        nombrePropuesto = limpiar(nombrePropuesto, "nombre de la especie", LARGO_MAXIMO_DEL_NOMBRE, true);
        justificacion = limpiar(justificacion, "justificación", LARGO_MAXIMO_DEL_TEXTO, false);
        respuesta = limpiar(respuesta, "respuesta", LARGO_MAXIMO_DEL_TEXTO, false);
    }

    /** RF-H1. */
    public static SolicitudEspecie nueva(UUID solicitanteId, String nombrePropuesto, String justificacion,
                                         Instant ahora) {
        return new SolicitudEspecie(UUID.randomUUID(), solicitanteId, nombrePropuesto, justificacion,
                EstadoSolicitud.PENDIENTE, null, ahora, null);
    }

    /**
     * RF-H4 (aprobar). La respuesta es opcional: si falta, el solicitante recibe un texto estándar.
     *
     * @throws SolicitudYaResueltaException si ya estaba resuelta
     */
    public SolicitudEspecie aprobada(String respuesta, Instant ahora) {
        exigirPendiente();
        return new SolicitudEspecie(id, solicitanteId, nombrePropuesto, justificacion,
                EstadoSolicitud.APROBADA, respuesta, creadaEn, ahora);
    }

    /**
     * RF-H4 (rechazar). La respuesta es OBLIGATORIA: un rechazo sin motivo no le sirve a quien lo recibe.
     *
     * @throws SolicitudYaResueltaException si ya estaba resuelta
     * @throws IllegalArgumentException si la respuesta está vacía
     */
    public SolicitudEspecie rechazada(String respuesta, Instant ahora) {
        exigirPendiente();
        if (respuesta == null || respuesta.isBlank()) {
            throw new IllegalArgumentException("rechazar una solicitud requiere una respuesta con el motivo");
        }
        return new SolicitudEspecie(id, solicitanteId, nombrePropuesto, justificacion,
                EstadoSolicitud.RECHAZADA, respuesta, creadaEn, ahora);
    }

    public boolean estaPendiente() {
        return estado == EstadoSolicitud.PENDIENTE;
    }

    private void exigirPendiente() {
        if (!estaPendiente()) {
            throw new SolicitudYaResueltaException(estado);
        }
    }

    private static String limpiar(String texto, String campo, int maximo, boolean obligatorio) {
        if (texto == null || texto.isBlank()) {
            if (obligatorio) {
                throw new IllegalArgumentException("la solicitud requiere " + campo);
            }
            return null;
        }
        String limpio = texto.trim();
        if (limpio.length() > maximo) {
            throw new IllegalArgumentException("la " + campo + " no puede pasar de " + maximo + " caracteres");
        }
        return limpio;
    }
}
