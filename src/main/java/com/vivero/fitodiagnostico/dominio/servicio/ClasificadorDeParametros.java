package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import java.util.ArrayList;
import java.util.List;

/**
 * Clasifica cada lectura de una medición contra el rango de la especie para su
 * magnitud (RF2). Sin estado y sin conocer ninguna magnitud concreta: por eso
 * agregar una magnitud nueva (p. ej. pH) no toca esta clase (OCP) — no aparece
 * aquí ninguna constante de Magnitud.TEMPERATURA/HUMEDAD/LUZ.
 */
public final class ClasificadorDeParametros {

    public List<ResultadoParametro> clasificar(Especie especie, Medicion medicion) {
        List<ResultadoParametro> resultados = new ArrayList<>();
        for (Lectura lectura : medicion.lecturas()) {
            Rango rangoOptimo = especie.rango(lectura.magnitud());
            resultados.add(new ResultadoParametro(
                    lectura.magnitud(),
                    lectura.valor(),
                    rangoOptimo,
                    rangoOptimo.clasificar(lectura.valor())));
        }
        return List.copyOf(resultados);
    }
}
