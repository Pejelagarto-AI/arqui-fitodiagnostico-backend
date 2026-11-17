package com.vivero.fitodiagnostico.mascota.modelo;

import java.util.Set;
import java.util.UUID;

/**
 * Algo de la mascota cambió sin ser un evento de la línea de tiempo (fase 13, D184): hoy, el accesorio. Se avisa
 * a las personas (otra pestaña abierta); la matera se entera por su siguiente lectura.
 */
public record ActualizacionDeMascota(UUID plantaId, String apodo, Accesorio accesorio, Set<UUID> destinatarios) {

    public ActualizacionDeMascota {
        destinatarios = Set.copyOf(destinatarios);
    }
}
