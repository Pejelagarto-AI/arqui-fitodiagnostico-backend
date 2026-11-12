package com.vivero.fitodiagnostico.dominio.excepcion;

/**
 * El solicitante SÍ puede ver la planta (es invitado) pero la operación es
 * solo del dueño: editar, eliminar, registrar mediciones (RN5). Aquí un 403
 * no filtra nada, porque quien lo recibe ya sabe que la planta existe.
 */
public class OperacionNoPermitidaException extends RuntimeException {

    public OperacionNoPermitidaException(String mensaje) {
        super(mensaje);
    }
}
