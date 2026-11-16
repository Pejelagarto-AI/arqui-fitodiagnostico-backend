package com.vivero.fitodiagnostico.mascota.modelo;

import java.util.Set;
import java.util.UUID;

/**
 * Un evento listo para salir: a qué personas (dueño e invitados, por la vista en vivo) y a qué materas (los seriales
 * vinculados, por MQTT), con la vitalidad y el ánimo del momento para que el que lo recibe no tenga que preguntar.
 */
public record AvisoDeMascota(EventoDeMascota evento, String apodo, int vitalidad, Animo animo,
                             Set<UUID> destinatarios, Set<String> seriales) {

    public AvisoDeMascota {
        destinatarios = Set.copyOf(destinatarios);
        seriales = Set.copyOf(seriales);
    }

    public String mensaje() {
        return evento.tipo().frase(apodo);
    }
}
