package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.puerto.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.CatalogoEditable;
import com.vivero.fitodiagnostico.dominio.puerto.RangosPorEspecie;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador: traduce entre el mundo JPA y el modelo de dominio. Implementa los
 * tres puertos del catálogo y es el único adaptador de producción (D14): ya no
 * hay modo CSV en caliente, el CSV solo siembra esta base al arrancar.
 *
 * <p>{@link #buscar} y {@link #listar} devuelven también las especies
 * inactivas: filtrar es decisión de la aplicación (D8), y una especie
 * descontinuada debe seguir siendo diagnosticable (RN11).
 */
@Repository
public class AdaptadorEspecieJpa implements RangosPorEspecie, CatalogoDeEspecies, CatalogoEditable {

    private final EspecieJpaRepository jpa;
    private final MapeadorEspecie mapeador;

    public AdaptadorEspecieJpa(EspecieJpaRepository jpa, MapeadorEspecie mapeador) {
        this.jpa = jpa;
        this.mapeador = mapeador;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Especie> buscar(String nombre) {
        return jpa.findByNombreIgnoreCase(nombre)
                  .map(mapeador::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Especie> listar() {
        return jpa.findAllByOrderByNombreAsc().stream()
                  .map(mapeador::aDominio)
                  .toList();
    }

    @Override
    @Transactional
    public void guardar(Especie especie) {
        EspecieEntity entidad = jpa.findByNombreIgnoreCase(especie.nombre())
                                   .orElseGet(mapeador::nuevaEntidad);
        mapeador.volcarEn(entidad, especie);
        jpa.saveAndFlush(entidad);
    }

    @Override
    @Transactional
    public void cambiarActivacion(String nombre, boolean activa) {
        jpa.findByNombreIgnoreCase(nombre).ifPresent(entidad -> {
            entidad.setActiva(activa);
            jpa.saveAndFlush(entidad);
        });
    }
}
