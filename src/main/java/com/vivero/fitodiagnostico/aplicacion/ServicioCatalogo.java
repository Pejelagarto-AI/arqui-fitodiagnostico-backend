package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.puerto.CatalogoDeEspecies;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Casos de uso de consulta del catálogo. Depende sólo de
 * {@link CatalogoDeEspecies} (ISP: no de {@code RangosPorEspecie}, que no usa),
 * nunca de Spring Data ni de la entidad JPA (RA-02, RA-04).
 *
 * <p>Son dos consultas con dos públicos (RF-C1 y RF-C2). El filtro de activas
 * vive aquí y no en el puerto (D8): {@code Especie} ya sabe si está activa,
 * así que partir el puerto en {@code listarActivas()}/{@code listarTodas()}
 * obligaría a tocar los dos adaptadores y sus pruebas de contrato.
 */
@Service
public class ServicioCatalogo {

    private final CatalogoDeEspecies catalogoDeEspecies;

    public ServicioCatalogo(CatalogoDeEspecies catalogoDeEspecies) {
        this.catalogoDeEspecies = catalogoDeEspecies;
    }

    /** RF-C1: las especies que se ofrecen al registrar una planta (RN11). */
    @Transactional(readOnly = true)
    public List<Especie> listar() {
        return catalogoDeEspecies.listar().stream()
                .filter(Especie::activa)
                .toList();
    }

    /** RF-C2: vista de administración, incluye las descontinuadas. */
    @Transactional(readOnly = true)
    public List<Especie> listarTodas() {
        return catalogoDeEspecies.listar();
    }
}
