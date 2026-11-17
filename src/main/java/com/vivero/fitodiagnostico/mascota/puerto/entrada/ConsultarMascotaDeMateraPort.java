package com.vivero.fitodiagnostico.mascota.puerto.entrada;

import com.vivero.fitodiagnostico.mascota.modelo.VistaParaLaMatera;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que la matera lee de su mascota al reportar una lectura (fase 13, D180). Quien llama ya verificó el vínculo
 * del dispositivo con la planta, igual que en las lecturas: aquí no hay autorización de personas.
 */
public interface ConsultarMascotaDeMateraPort {

    /**
     * El resumen y los eventos registrados después de {@code ultimaLecturaAnterior} (hora del servidor de la lectura
     * anterior de esa matera), del más antiguo al más reciente y a lo sumo 10. Con {@code null}, los de los últimos 60 s.
     * Sin fila de mascota: la vitalidad inicial, {@code SIN_DATOS}, racha 0, sin accesorio y sin eventos.
     */
    VistaParaLaMatera paraLaMatera(UUID plantaId, Instant ultimaLecturaAnterior);
}
