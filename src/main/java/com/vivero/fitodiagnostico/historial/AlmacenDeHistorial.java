package com.vivero.fitodiagnostico.historial;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida de la retención (D176): lo que hace falta de la base para podar sin perder el resumen por hora. */
public interface AlmacenDeHistorial {

    /** Las plantas con alguna lectura cruda tomada antes de {@code corte}. */
    List<UUID> plantasConLecturasAnterioresA(Instant corte);

    /** Cuándo se tomó la lectura cruda más antigua de la planta, si hay alguna. */
    Optional<Instant> primeraLecturaDe(UUID plantaId);

    /**
     * En una sola transacción: asegura el resumen por hora de cada hora entera de {@code [desde, hasta)} (y el de la
     * hora siguiente, cuyo primer tramo depende de la última lectura de la anterior) y borra las lecturas crudas tomadas
     * en ese rango. Si el resumen falla no se borra nada. {@code desde} y {@code hasta} caen en hora en punto.
     *
     * @return cuántas lecturas crudas se borraron
     */
    int resumirYBorrar(UUID plantaId, Instant desde, Instant hasta);
}
