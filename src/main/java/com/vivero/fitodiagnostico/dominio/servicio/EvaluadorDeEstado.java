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

    public EvaluadorDeEstado(List<EstadoPlanta> estadosPorPrioridad) {
        this.estadosPorPrioridad = List.copyOf(estadosPorPrioridad);
    }

    public Diagnostico evaluar(Especie especie, Ambiente ambiente) {
        EstadoPlanta estado = estadosPorPrioridad.stream()
                .filter(e -> e.evaluarEstado(especie, ambiente))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("cadena de estados sin terminal"));

        return new Diagnostico(
                especie, ambiente,
                estado.obtenerEstado(),
                estado.describir(especie, ambiente),
                Instant.now());
    }
}
