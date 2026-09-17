package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import java.time.Instant;
import java.util.List;

/**
 * Orquesta el diagnóstico (RF2 + RF3): clasifica cada parámetro y luego
 * agrega esas clasificaciones en un estado global. No conoce ninguna regla
 * de agregación concreta, solo la interfaz {@link ReglaDeAgregacion}.
 */
public final class EvaluadorDeEstado {

    private final ClasificadorDeParametros clasificador;
    private final ReglaDeAgregacion regla;
    private final RedactorDeRecomendaciones redactor;

    public EvaluadorDeEstado(ClasificadorDeParametros clasificador, ReglaDeAgregacion regla,
                              RedactorDeRecomendaciones redactor) {
        this.clasificador = clasificador;
        this.regla = regla;
        this.redactor = redactor;
    }

    public Diagnostico evaluar(Especie especie, Medicion medicion) {
        List<ResultadoParametro> parametros = clasificador.clasificar(especie, medicion);
        EstadoGlobal estado = regla.agregar(parametros);
        List<String> recomendaciones = redactor.redactar(parametros);
        return new Diagnostico(especie, medicion, estado, parametros, recomendaciones, Instant.now());
    }
}
