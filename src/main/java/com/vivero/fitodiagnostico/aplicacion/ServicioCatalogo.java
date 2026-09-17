package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.puerto.CatalogoDeEspecies;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Caso de uso: listar las especies soportadas (RF5). Depende sólo de
 * {@link CatalogoDeEspecies} (ISP: no de {@code RangosPorEspecie}, que no usa),
 * nunca de Spring Data ni de la entidad JPA (RA-02, RA-04).
 */
@Service
public class ServicioCatalogo {

    private final CatalogoDeEspecies catalogoDeEspecies;

    public ServicioCatalogo(CatalogoDeEspecies catalogoDeEspecies) {
        this.catalogoDeEspecies = catalogoDeEspecies;
    }

    @Transactional(readOnly = true)
    public List<Especie> listar() {
        return catalogoDeEspecies.listar();
    }
}
