package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Muestra;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaDeLuzAcumulada;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El resumen de luz por hora (D175): lo lee, lo calcula cuando falta y lo invalida si llega una lectura atrasada.
 *
 * <p><b>Cuándo se llena:</b> la primera vez que alguien lo necesita para una hora ya cerrada, que es la primera lectura
 * de la planta después de que la hora termina (cada lectura pide la luz de las últimas 24 h). No hay tarea cada hora:
 * una tarea tendría que recorrer todas las plantas aunque no hayan hablado y fallaría en silencio si se cae, y el
 * cálculo es una sola consulta de unas 720 filas por planta y hora. Una hora sin lecturas también queda anotada
 * (lecturas = 0), para no recalcularla. La retención (D176) usa el mismo camino para resumir antes de borrar.
 */
@Component
class ResumenesDeLuz {

    private final DiagnosticoDePlantaJpaRepository diagnosticos;
    private final ResumenLuzHoraJpaRepository resumenes;
    private final Clock reloj;

    ResumenesDeLuz(DiagnosticoDePlantaJpaRepository diagnosticos, ResumenLuzHoraJpaRepository resumenes, Clock reloj) {
        this.diagnosticos = diagnosticos;
        this.resumenes = resumenes;
        this.reloj = reloj;
    }

    static Instant inicioDeLaHora(Instant instante) {
        return instante.truncatedTo(ChronoUnit.HOURS);
    }

    /** La primera hora entera que empieza en {@code instante} o después. */
    static Instant primeraHoraDesde(Instant instante) {
        Instant hora = inicioDeLaHora(instante);
        return hora.equals(instante) ? hora : hora.plus(ResumenDeLuzPorHora.UNA_HORA);
    }

    /** Los resúmenes de las horas enteras {@code desde <= hora < hasta} (alineadas), calculando los que falten. */
    @Transactional
    List<ResumenDeLuzPorHora> deLasHoras(UUID plantaId, Instant desde, Instant hasta) {
        Map<Instant, ResumenDeLuzPorHora> existentes = new HashMap<>();
        for (ResumenLuzHoraEntity fila : resumenes.entre(plantaId, desde, hasta)) {
            ResumenDeLuzPorHora r = fila.aResumen();
            existentes.put(r.hora(), r);
        }
        List<ResumenDeLuzPorHora> todos = new ArrayList<>();
        List<ResumenLuzHoraEntity> nuevos = new ArrayList<>();
        Instant ahora = reloj.instant();
        for (Instant hora = desde; hora.isBefore(hasta); hora = hora.plus(ResumenDeLuzPorHora.UNA_HORA)) {
            ResumenDeLuzPorHora r = existentes.get(hora);
            if (r == null) {
                r = calcular(plantaId, hora);
                nuevos.add(new ResumenLuzHoraEntity(r, ahora));
            }
            todos.add(r);
        }
        if (!nuevos.isEmpty()) {
            resumenes.saveAll(nuevos);
            resumenes.flush();
        }
        return todos;
    }

    /** Desde las lecturas crudas de la hora y la última anterior (que da el primer tramo; si quedó lejos, vale el tope). */
    private ResumenDeLuzPorHora calcular(UUID plantaId, Instant hora) {
        Instant desde = hora.minus(ReglaDeLuzAcumulada.HUECO_MAXIMO);
        Instant hasta = hora.plus(ResumenDeLuzPorHora.UNA_HORA);
        Muestra previa = null;
        List<Muestra> deLaHora = new ArrayList<>();
        for (Object[] fila : diagnosticos.serie(plantaId, Magnitud.LUZ, desde, hasta)) {
            Muestra muestra = new Muestra(aInstante(fila[0]), ((Number) fila[1]).doubleValue());
            if (muestra.tomadaEn().isBefore(hora)) {
                previa = muestra;
            } else {
                deLaHora.add(muestra);
            }
        }
        if (previa == null && diagnosticos.existsByPlantaIdAndTomadaEnBefore(plantaId, desde)) {
            // La anterior quedó a más del tope de un tramo: el primer tramo de la hora vale el tope, como con las crudas.
            previa = new Muestra(desde, 0.0);
        }
        return ResumenDeLuzPorHora.de(plantaId, hora, previa, deLaHora);
    }

    /** Una lectura atrasada cambió las lecturas de esa hora: su resumen y el de la siguiente ya no valen. */
    @Transactional
    void invalidar(UUID plantaId, Instant tomadaEn) {
        Instant hora = inicioDeLaHora(tomadaEn);
        resumenes.borrarEntre(plantaId, hora, hora.plus(ResumenDeLuzPorHora.UNA_HORA));
    }

    private static Instant aInstante(Object valor) {
        return valor instanceof Timestamp t ? t.toInstant() : (Instant) valor;
    }
}
