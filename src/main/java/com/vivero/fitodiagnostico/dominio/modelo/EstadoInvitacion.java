package com.vivero.fitodiagnostico.dominio.modelo;

/**
 * Ciclo de vida de una {@link Invitacion}. {@link #CADUCADA} no se escribe
 * nunca en la base: se DERIVA de la fecha (ver {@link Invitacion#estadoEn}),
 * porque guardarla exigiría un proceso periódico que recorra y marque, y no
 * hay ninguno en el sistema.
 */
public enum EstadoInvitacion {
    PENDIENTE,
    ACEPTADA,
    CADUCADA,
    REVOCADA
}
