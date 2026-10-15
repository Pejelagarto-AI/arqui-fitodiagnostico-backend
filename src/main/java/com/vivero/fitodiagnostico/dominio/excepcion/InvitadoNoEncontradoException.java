package com.vivero.fitodiagnostico.dominio.excepcion;

import java.util.UUID;

/** RF-E4: el usuario no es invitado de esa planta. Quien pregunta es el dueño, así que no filtra nada. */
public class InvitadoNoEncontradoException extends RuntimeException {

    private final UUID id;

    public InvitadoNoEncontradoException(UUID id) {
        super("el usuario " + id + " no es invitado de esta planta");
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
