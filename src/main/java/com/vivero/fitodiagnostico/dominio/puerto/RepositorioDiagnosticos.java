package com.vivero.fitodiagnostico.dominio.puerto;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia del historial de diagnósticos. Es de solo agregar:
 * no hay "actualizar" un diagnóstico, el historial refleja la vida de la
 * planta (D3). La única forma de quitar registros es eliminar la planta entera.
 */
public interface RepositorioDiagnosticos {

    /** El más reciente de la planta, o vacío si nunca se midió (RN9, RF-F4). */
    Optional<DiagnosticoDePlanta> ultimoDe(UUID plantaId);

    /** El historial, del más reciente al más antiguo (RF-F5). */
    Pagina<DiagnosticoDePlanta> historialDe(UUID plantaId, Paginacion paginacion);

    void guardar(DiagnosticoDePlanta diagnostico);

    /** Elimina todo el historial de la planta (parte de eliminarla, RN12). */
    void eliminarDe(UUID plantaId);
}
