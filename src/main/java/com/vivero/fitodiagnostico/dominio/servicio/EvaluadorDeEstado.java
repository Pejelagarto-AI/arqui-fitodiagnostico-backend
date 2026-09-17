package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.estado.EstadoPlanta;
import com.vivero.fitodiagnostico.dominio.modelo.*;
import java.time.Instant;
import java.util.List;

/**
 * Recorre las estrategias en orden de prioridad y devuelve la primera que aplique.
 * No conoce ninguna implementación concreta: recibe la lista ya ordenada.
 */
public final class EvaluadorDeEstado {

    private final List<EstadoPlanta> estadosPorPrioridad;
    private final ClasificadorDeParametros clasificador;

    public EvaluadorDeEstado(List<EstadoPlanta> estadosPorPrioridad, ClasificadorDeParametros clasificador) {
        this.estadosPorPrioridad = List.copyOf(estadosPorPrioridad);
        this.clasificador = clasificador;
    }

    public Diagnostico evaluar(Especie especie, Medicion medicion) {
        EstadoPlanta estado = estadosPorPrioridad.stream()
                .filter(e -> e.evaluarEstado(especie, medicion))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("cadena de estados sin terminal"));

        return new Diagnostico(
                especie, medicion,
                estado.obtenerEstado(),
                estado.describir(especie, medicion),
                clasificador.clasificar(especie, medicion),
                Instant.now());
    }
}
