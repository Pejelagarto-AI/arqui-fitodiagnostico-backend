package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;

/**
 * Puerto de la capa de aplicación (fase 9, D140): "se registró una lectura". {@link ServicioMediciones} lo llama con
 * cada registro guardado, sin saber quién escucha. Lo implementa la composición para conectar contextos que
 * CONSUMEN el historial, como la mascota, sin que el caso de uso de medir dependa de ellos.
 *
 * <p>Vive en {@code aplicacion} y no en {@code dominio.puerto}: no es una regla del negocio del diagnóstico sino una
 * costura entre casos de uso, y el dominio no se toca en esta fase.
 *
 * <p>Quien lo implemente decide si actúa en la misma transacción o después del commit; el de la mascota actúa
 * después, para que una falla de la mascota nunca deshaga una lectura.
 */
public interface ObservadorDeMediciones {

    void alRegistrar(DiagnosticoDePlanta registro);
}
