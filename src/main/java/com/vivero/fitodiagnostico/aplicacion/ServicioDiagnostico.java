package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.excepcion.EspecieNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.Ambiente;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioEspecies;
import com.vivero.fitodiagnostico.dominio.servicio.EvaluadorDeEstado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso único: diagnosticar. Orquesta —busca la especie por el puerto de
 * dominio, delega la decisión al {@link EvaluadorDeEstado}— y no compara ningún
 * umbral por su cuenta (RA-05). Depende sólo de la interfaz de dominio
 * {@link RepositorioEspecies}, nunca de Spring Data ni de la entidad JPA (RA-02, RA-04).
 */
@Service
public class ServicioDiagnostico {

    private final RepositorioEspecies repositorioEspecies;
    private final EvaluadorDeEstado evaluador;

    public ServicioDiagnostico(RepositorioEspecies repositorioEspecies,
                                EvaluadorDeEstado evaluador) {
        this.repositorioEspecies = repositorioEspecies;
        this.evaluador = evaluador;
    }

    @Transactional(readOnly = true)
    public Diagnostico diagnosticar(String nombreCientifico, Ambiente ambiente) {

        Especie especie = repositorioEspecies
                .buscarPorNombreCientifico(nombreCientifico)
                .orElseThrow(() -> new EspecieNoEncontradaException(nombreCientifico));

        return evaluador.evaluar(especie, ambiente);
    }
}
