package com.vivero.fitodiagnostico.salud;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * La salud de las materas de varias plantas a la vez, resumida en una por planta, para el tablero (fase 13, D185).
 * Sin autorización: los ids salen de un listado que ya filtró lo que el solicitante puede ver.
 */
public interface ConsultarSaludDePlantasPort {

    /**
     * La peor salud entre las materas de cada planta: {@code FALLA}, luego {@code SIN_NOTICIAS}, luego {@code OK}
     * (a igual gravedad, la del serial menor). Una planta sin materas, o cuyas materas nunca reportaron, no aparece.
     */
    Map<UUID, ConsultarSaludPort.Vista> laPeorDeLasPlantas(Collection<UUID> plantaIds);
}
