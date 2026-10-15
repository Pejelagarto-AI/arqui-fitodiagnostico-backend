package com.vivero.fitodiagnostico.dominio.modelo;

/**
 * Permisos de un usuario (D1). Un enum y no una jerarquía de clases: ser
 * administrador es un permiso que se otorga y se revoca, no un tipo de
 * persona distinto. Todo usuario tiene {@link #CLIENTE}; {@link #ADMINISTRADOR}
 * se suma, no sustituye (RN2).
 */
public enum Rol {
    CLIENTE,
    ADMINISTRADOR
}
