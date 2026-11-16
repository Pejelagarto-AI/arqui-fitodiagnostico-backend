package com.vivero.fitodiagnostico.avisos;

/**
 * La clave {@code motivo} de los datos de un aviso y sus valores (fase 12, D171). Vive en su propio paquete, fuera del
 * dominio, porque la escribe la aplicación ({@code ServicioMediciones}) y la leen los adaptadores de notificación,
 * y ninguno de los dos puede depender del otro.
 */
public final class MotivoDelAviso {

    /** Clave en {@code Aviso.datos()}. Ausente: el aviso no tiene un motivo particular. */
    public static final String CLAVE = "motivo";

    /**
     * El estado global cambió solo porque entró o salió la noche (la luz dejó de contar o volvió a contar). El correo no
     * sale; la vista en vivo sí, con este motivo para que el front lo diga ("durmió", "amaneció").
     */
    public static final String PERIODO = "PERIODO";

    private MotivoDelAviso() { }
}
