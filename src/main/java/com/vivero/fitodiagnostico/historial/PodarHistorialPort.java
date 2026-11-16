package com.vivero.fitodiagnostico.historial;

/**
 * Puerto de entrada de la retención de lecturas (fase 12, D176): lo llama el reloj una vez al día. Fuera de
 * {@code dominio/} a propósito: cuánto tiempo se guardan las lecturas crudas es una política operativa, no una regla de
 * agronomía.
 */
public interface PodarHistorialPort {

    /**
     * Borra las lecturas crudas más viejas que la retención, habiendo resumido antes cada hora que se va (el resumen por
     * hora se conserva para siempre).
     *
     * @return cuántas lecturas se borraron
     */
    int podar();
}
