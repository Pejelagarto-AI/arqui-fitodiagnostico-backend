package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoEditable;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RangosPorEspecie;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador: traduce entre el mundo JPA y el modelo de dominio. Implementa los
 * tres puertos del catálogo. Es la fuente cuando
 * {@code fitodiagnostico.catalogo.fuente=bd} (por defecto); con {@code csv} la
 * lectura sale de {@code AdaptadorEspecieCsv} y esta clase solo sirve de espejo
 * para la clave foránea de las plantas. No lleva anotación de Spring: quien
 * decide si es un bean de los puertos o solo el espejo es {@code config.ConfiguracionCatalogo}.
 *
 * <p>{@link #buscar} y {@link #listar} devuelven también las especies
 * inactivas: filtrar es decisión de la aplicación (D8), y una especie
 * descontinuada debe seguir siendo diagnosticable (RN11).
 */
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
