package com.vivero.fitodiagnostico.dominio.modelo;

/**
 * Los motivos por los que el sistema avisa a alguien. Están todos declarados
 * desde ahora aunque este bloque solo emita {@link #CAMBIO_DE_ESTADO} y
 * {@link #ACCESO_PERDIDO}: el catálogo de avisos es del dominio, y cada módulo
 * que llegue emite el suyo sin tocar el puerto.
 */
public enum TipoDeAviso {
    CAMBIO_DE_ESTADO,
    DIAGNOSTICO_NUEVO,
    ACCESO_PERDIDO,
    INVITACION,
    RESTABLECIMIENTO,
    SOLICITUD_RESUELTA
}
