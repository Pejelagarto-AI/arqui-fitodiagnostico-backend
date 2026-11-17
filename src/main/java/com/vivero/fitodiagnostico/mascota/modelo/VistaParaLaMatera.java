package com.vivero.fitodiagnostico.mascota.modelo;

import java.util.List;

/**
 * Lo que la matera lee de su mascota en la respuesta de cada lectura (fase 13, D180): el resumen y los eventos que
 * pasaron desde su lectura anterior, del más antiguo al más reciente.
 */
public record VistaParaLaMatera(ResumenDeMascota resumen, List<EventoDeMascota> eventos) {

    public VistaParaLaMatera {
        eventos = List.copyOf(eventos);
    }
}
