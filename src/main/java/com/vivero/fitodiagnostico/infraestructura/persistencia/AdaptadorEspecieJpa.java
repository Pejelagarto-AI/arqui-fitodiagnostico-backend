package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioEspecies;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/** Adaptador: traduce entre el mundo JPA y el modelo de dominio. */
@Repository
public class AdaptadorEspecieJpa implements RepositorioEspecies {

    private final EspecieJpaRepository jpa;
    private final MapeadorEspecie mapeador;

    public AdaptadorEspecieJpa(EspecieJpaRepository jpa, MapeadorEspecie mapeador) {
        this.jpa = jpa;
        this.mapeador = mapeador;
    }

    @Override
    public Optional<Especie> buscarPorNombreCientifico(String nombreCientifico) {
        return jpa.findByNombreCientificoIgnoreCase(nombreCientifico)
                  .map(mapeador::aDominio);
    }
}
