package com.vivero.fitodiagnostico.salud;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Puerto de salida: dónde se guarda la salud de cada dispositivo (una fila por serial). */
public interface RepositorioDeSalud {

    void guardar(SaludDelDispositivo salud);

    Optional<SaludDelDispositivo> de(String serial);

    List<SaludDelDispositivo> de(Collection<String> seriales);

    /**
     * La salud de las materas vinculadas a esas plantas, en una consulta (fase 13, D185). Solo trae las materas
     * que alguna vez dejaron noticia.
     */
    java.util.Map<java.util.UUID, List<SaludDelDispositivo>> dePlantas(Collection<java.util.UUID> plantaIds);

    /** Borra la salud de un dispositivo (al desvincularlo, D173). Sin fila no hace nada. */
    void eliminar(String serial);
}
