package com.vivero.fitodiagnostico.mascota.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;

/**
 * Llega una lectura nueva (ya guardada en el historial). La composición lo conecta con
 * {@code ObservadorDeMediciones} después del commit de la lectura (D140): el caso de uso de medir no sabe que la
 * mascota existe.
 */
public interface AlimentarMascotaPort {

    void alRegistrarLectura(DiagnosticoDePlanta registro);
}
