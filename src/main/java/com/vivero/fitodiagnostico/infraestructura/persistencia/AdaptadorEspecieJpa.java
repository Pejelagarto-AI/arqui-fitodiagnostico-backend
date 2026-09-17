package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.puerto.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.RangosPorEspecie;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador: traduce entre el mundo JPA y el modelo de dominio. Implementa los dos puertos.
 * Activo solo con {@code fitodiagnostico.tabla-referencia=bd}; por defecto la
 * fuente es el CSV ({@link com.vivero.fitodiagnostico.infraestructura.referencia.AdaptadorEspeciesCsv}).
 */
@Repository
@ConditionalOnProperty(prefix = "fitodiagnostico", name = "tabla-referencia", havingValue = "bd")
public class AdaptadorEspecieJpa implements RangosPorEspecie, CatalogoDeEspecies {

    private final EspecieJpaRepository jpa;
    private final MapeadorEspecie mapeador;

    public AdaptadorEspecieJpa(EspecieJpaRepository jpa, MapeadorEspecie mapeador) {
        this.jpa = jpa;
        this.mapeador = mapeador;
    }

    @Override
    public Optional<Especie> buscar(String nombre) {
        return jpa.findByNombreIgnoreCase(nombre)
                  .map(mapeador::aDominio);
    }

    @Override
    public List<Especie> listar() {
        return jpa.findAllByOrderByNombreAsc().stream()
                  .map(mapeador::aDominio)
                  .toList();
    }
}
