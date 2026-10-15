package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioPlantas;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador JPA del puerto {@link RepositorioPlantas}. */
@Repository
public class AdaptadorPlantaJpa implements RepositorioPlantas {

    private final PlantaJpaRepository jpa;
    private final EspecieJpaRepository especies;
    private final MapeadorPlanta mapeador;

    public AdaptadorPlantaJpa(PlantaJpaRepository jpa, EspecieJpaRepository especies, MapeadorPlanta mapeador) {
        this.jpa = jpa;
        this.especies = especies;
        this.mapeador = mapeador;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Planta> buscar(UUID id) {
        return jpa.findById(id).map(mapeador::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Planta> deDueno(UUID duenoId) {
        return jpa.findByDuenoIdOrderByRegistradaEnDescIdDesc(duenoId).stream().map(mapeador::aDominio).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Planta> compartidasCon(UUID usuarioId) {
        return jpa.compartidasCon(usuarioId).stream().map(mapeador::aDominio).toList();
    }

    @Override
    @Transactional
    public void guardar(Planta planta) {
        // Una planta solo puede apuntar a una especie que exista: si no, es un bug del
        // llamador (el servicio ya la buscó por el puerto), no un error de negocio.
        EspecieEntity especie = especies.findByNombreIgnoreCase(planta.especie().nombre())
                .orElseThrow(() -> new IllegalStateException(
                        "la especie '" + planta.especie().nombre() + "' no existe en el catálogo"));
        PlantaEntity entidad = jpa.findById(planta.id()).orElseGet(mapeador::nuevaEntidad);
        mapeador.volcarEn(entidad, planta, especie);
        jpa.saveAndFlush(entidad);
    }

    @Override
    @Transactional
    public void eliminar(UUID id) {
        jpa.deleteById(id);
        jpa.flush();   // que una clave foránea pendiente falle aquí y no al cerrar la transacción
    }
}
