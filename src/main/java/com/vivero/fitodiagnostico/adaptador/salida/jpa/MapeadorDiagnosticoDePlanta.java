package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.ExposicionLuminica;
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
 *
 * <p>Fase 8: el periodo, la exposición a la luz y el valor evaluado de cada parámetro
 * también salen de la fila. Las filas anteriores a V15 se leen como lo que fueron:
 * de día, con la luz evaluada al instante.
 *
 * <p>Fase 15: también el último riego detectado ({@code regada_en}); las filas anteriores a V24
 * se leen sin riego.
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
                .map(MapeadorDiagnosticoDePlanta::parametroDesde)
                .toList();

        Diagnostico diagnostico = new Diagnostico(especie, medicion, entidad.getEstado(), parametros,
                List.copyOf(entidad.getRecomendaciones()), entidad.getEvaluadoEn(), entidad.getPeriodo(),
                exposicionDesde(entidad), entidad.getRegadaEn());
        return new DiagnosticoDePlanta(entidad.getId(), entidad.getPlantaId(), entidad.getOrigen(),
                entidad.getTomadaEn(), entidad.getRegistradoEn(), diagnostico);
    }

    private static ResultadoParametro parametroDesde(ParametroEmbebido p) {
        Rango rango = new Rango(p.rangoMinimo(), p.rangoMaximo());
        if (p.base() == null || p.valorEvaluado() == null) {
            return new ResultadoParametro(p.magnitud(), p.valor(), rango, p.clasificacion());
        }
        return new ResultadoParametro(p.magnitud(), p.valor(), rango, p.clasificacion(), p.valorEvaluado(), p.base());
    }

    private static ExposicionLuminica exposicionDesde(DiagnosticoDePlantaEntity e) {
        if (e.getLuzLuxEfectivo() == null) {
            return null;
        }
        return new ExposicionLuminica(e.getLuzHorasObservadas(), e.getLuzHorasDeLuz(), e.getLuzLuxPromedioEnLuz(),
                e.getLuzLuxEfectivo(), e.getLuzHorasEnOscuridad());
    }

    DiagnosticoDePlantaEntity nuevaEntidad() {
        return new DiagnosticoDePlantaEntity();
    }

    void volcarEn(DiagnosticoDePlantaEntity entidad, DiagnosticoDePlanta registro) {
        Diagnostico diagnostico = registro.diagnostico();
        entidad.setId(registro.id());
        entidad.setPlantaId(registro.plantaId());
        entidad.setOrigen(registro.origen());
        entidad.setTomadaEn(registro.tomadaEn());
        entidad.setRegistradoEn(registro.registradoEn());
        entidad.setEvaluadoEn(diagnostico.evaluadoEn());
        entidad.setEspecieNombre(diagnostico.especie().nombre());
        entidad.setEstado(diagnostico.estado());
        entidad.setPeriodo(diagnostico.periodo());
        entidad.setRegadaEn(diagnostico.regadaEn());
        ExposicionLuminica e = diagnostico.exposicion();
        if (e == null) {
            entidad.setExposicion(null, null, null, null, null);
        } else {
            entidad.setExposicion(e.horasObservadas(), e.horasDeLuz(), e.luxPromedioEnLuz(), e.luxEfectivo(),
                    e.horasEnOscuridad());
        }
        entidad.getParametros().clear();
        for (ResultadoParametro r : diagnostico.parametros()) {
            entidad.getParametros().add(new ParametroEmbebido(r.magnitud(), r.valor(),
                    r.rangoOptimo().minimo(), r.rangoOptimo().maximo(), r.clasificacion(),
                    r.valorEvaluado(), r.base()));
        }
        entidad.getRecomendaciones().clear();
        entidad.getRecomendaciones().addAll(diagnostico.recomendaciones());
    }
}
