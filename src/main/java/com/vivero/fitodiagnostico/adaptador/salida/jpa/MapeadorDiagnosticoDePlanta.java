package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Convierte entre {@link DiagnosticoDePlantaEntity} y {@link DiagnosticoDePlanta}.
 *
 * <p>El diagnóstico se reconstruye desde SU PROPIA fila, no desde el catálogo:
 * la {@code Especie} y la {@code Medicion} se arman con los parámetros guardados
 * (valor y rango óptimo con que se clasificó). Así un diagnóstico viejo no
 * cambia aunque la especie se edite, se desactive o la planta cambie de especie.
 * Consecuencia: la {@code Especie} reconstruida es un retrato de lo que se usó
 * para evaluar y su {@code activa} no es parte de ese retrato (queda en true);
 * nadie lee ese campo de un diagnóstico del historial.
 */
@Component
public class MapeadorDiagnosticoDePlanta {

    public DiagnosticoDePlanta aDominio(DiagnosticoDePlantaEntity entidad) {
        List<ParametroEmbebido> guardados = entidad.getParametros().stream()
                .sorted(Comparator.comparing(ParametroEmbebido::magnitud))   // orden del enum, el de Medicion
                .toList();

        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        for (ParametroEmbebido p : guardados) {
            rangos.put(p.magnitud(), new Rango(p.rangoMinimo(), p.rangoMaximo()));
        }
        Especie especie = new Especie(entidad.getEspecieNombre(), rangos);
        Medicion medicion = Medicion.de(guardados.stream()
                .map(p -> new Lectura(p.magnitud(), p.valor())).toList());
        List<ResultadoParametro> parametros = guardados.stream()
                .map(p -> new ResultadoParametro(p.magnitud(), p.valor(),
                        new Rango(p.rangoMinimo(), p.rangoMaximo()), p.clasificacion()))
                .toList();

        Diagnostico diagnostico = new Diagnostico(especie, medicion, entidad.getEstado(), parametros,
                List.copyOf(entidad.getRecomendaciones()), entidad.getEvaluadoEn());
        return new DiagnosticoDePlanta(entidad.getId(), entidad.getPlantaId(), entidad.getOrigen(),
                entidad.getRegistradoEn(), diagnostico);
    }

    DiagnosticoDePlantaEntity nuevaEntidad() {
        return new DiagnosticoDePlantaEntity();
    }

    void volcarEn(DiagnosticoDePlantaEntity entidad, DiagnosticoDePlanta registro) {
        Diagnostico diagnostico = registro.diagnostico();
        entidad.setId(registro.id());
        entidad.setPlantaId(registro.plantaId());
        entidad.setOrigen(registro.origen());
        entidad.setRegistradoEn(registro.registradoEn());
        entidad.setEvaluadoEn(diagnostico.evaluadoEn());
        entidad.setEspecieNombre(diagnostico.especie().nombre());
        entidad.setEstado(diagnostico.estado());
        entidad.getParametros().clear();
        for (ResultadoParametro r : diagnostico.parametros()) {
            entidad.getParametros().add(new ParametroEmbebido(r.magnitud(), r.valor(),
                    r.rangoOptimo().minimo(), r.rangoOptimo().maximo(), r.clasificacion()));
        }
        entidad.getRecomendaciones().clear();
        entidad.getRecomendaciones().addAll(diagnostico.recomendaciones());
    }
}
