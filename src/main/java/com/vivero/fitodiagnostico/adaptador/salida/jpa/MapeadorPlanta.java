package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import org.springframework.stereotype.Component;

/**
 * Convierte entre {@link PlantaEntity} y {@link Planta}. La especie se arma con
 * {@link MapeadorEspecie}: la planta lleva los rangos VIGENTES de su especie, de
 * modo que editar la especie (RF-C4) se refleja en las plantas que la usan.
 */
@Component
public class MapeadorPlanta {

    private final MapeadorEspecie mapeadorEspecie;

    public MapeadorPlanta(MapeadorEspecie mapeadorEspecie) {
        this.mapeadorEspecie = mapeadorEspecie;
    }

    public Planta aDominio(PlantaEntity entidad) {
        return new Planta(entidad.getId(), entidad.getDuenoId(), entidad.getApodo(),
                mapeadorEspecie.aDominio(entidad.getEspecie()), entidad.getRegistradaEn());
    }

    PlantaEntity nuevaEntidad() {
        return new PlantaEntity();
    }

    void volcarEn(PlantaEntity entidad, Planta planta, EspecieEntity especie) {
        entidad.setId(planta.id());
        entidad.setDuenoId(planta.duenoId());
        entidad.setApodo(planta.apodo());
        entidad.setEspecie(especie);
        entidad.setRegistradaEn(planta.registradaEn());
    }
}
