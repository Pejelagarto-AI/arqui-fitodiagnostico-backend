package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import java.util.List;

/**
 * Deriva el {@link EstadoGlobal} de la planta a partir de sus parámetros ya
 * clasificados (RF3). Punto de extensión OCP: una regla de agregación nueva
 * es una clase nueva que implementa esta interfaz, no una modificación de
 * {@link EvaluadorDeEstado} ni de esta interfaz.
 */
public interface ReglaDeAgregacion {

    EstadoGlobal agregar(List<ResultadoParametro> parametros);
}
