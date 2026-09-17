package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.excepcion.EspecieNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.puerto.RangosPorEspecie;
import com.vivero.fitodiagnostico.dominio.servicio.EvaluadorDeEstado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Caso de uso único: diagnosticar. Orquesta —busca la especie por el puerto de
 * dominio, delega la decisión al {@link EvaluadorDeEstado}— y no compara ningún
 * umbral por su cuenta (RA-05). Depende sólo de la interfaz de dominio
 * {@link RangosPorEspecie} (ISP: no de {@code CatalogoDeEspecies}, que no usa),
 * nunca de Spring Data ni de la entidad JPA (RA-02, RA-04).
 */
@Service
public class ServicioDiagnostico {

    private final RangosPorEspecie rangosPorEspecie;
    private final EvaluadorDeEstado evaluador;

    public ServicioDiagnostico(RangosPorEspecie rangosPorEspecie,
                                EvaluadorDeEstado evaluador) {
        this.rangosPorEspecie = rangosPorEspecie;
        this.evaluador = evaluador;
    }

    @Transactional(readOnly = true)
    public Diagnostico diagnosticar(String nombre, Medicion medicion) {

        Especie especie = rangosPorEspecie
                .buscar(nombre)
                .orElseThrow(() -> new EspecieNoEncontradaException(nombre));

        return evaluador.evaluar(especie, medicion);
    }
}
