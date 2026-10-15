package com.vivero.fitodiagnostico.dominio.excepcion;

/** No existe un usuario con ese identificador. La capa web la traduce a 404. */
public class UsuarioNoEncontradoException extends RuntimeException {

    private final java.util.UUID id;

    public UsuarioNoEncontradoException(java.util.UUID id) {
        super("no existe un usuario con id " + id);
        this.id = id;
    }

    public java.util.UUID getId() {
        return id;
    }
}
