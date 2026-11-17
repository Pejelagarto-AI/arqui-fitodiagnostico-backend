package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoSaludJpaRepository extends JpaRepository<DispositivoSaludEntity, String> {

    /** Fase 13 (D185): la salud de las materas de un lote de plantas en UNA sentencia (dispositivo.planta_id, clave del serial). */
    @org.springframework.data.jpa.repository.Query("select d.plantaId, s from DispositivoSaludEntity s, DispositivoEntity d "
            + "where d.serial = s.serial and d.plantaId in :plantaIds")
    java.util.List<Object[]> dePlantas(@org.springframework.data.repository.query.Param("plantaIds") java.util.Collection<java.util.UUID> plantaIds);
}
