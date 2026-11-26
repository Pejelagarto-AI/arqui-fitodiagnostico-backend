package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.ExposicionLuminica;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.Periodo;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import com.vivero.fitodiagnostico.dominio.modelo.SerieTemporal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orquesta el diagnóstico (RF2 + RF3): clasifica cada parámetro y luego
 * agrega esas clasificaciones en un estado global. No conoce ninguna regla
 * de agregación concreta, solo la interfaz {@link ReglaDeAgregacion}.
 *
 * <p>Fase 8: con historial, la luz la clasifica {@link ReglaDeLuzAcumulada} por lo
 * acumulado en 24 h, y de noche no entra al estado global ni a las recomendaciones.
 * El evaluador no obtiene ese historial: lo recibe ya armado como {@link SerieTemporal}
 * (D120). Sin historial todo sigue como en el corte 1.
 *
 * <p>Fase 15: con historial de humedad, {@link ReglaDeHumedadTrasRiego} detecta si la planta se
 * regó en las últimas 24 h; si es así, la humedad ALTO no entra al estado global y su recomendación
 * dice que quedó hidratada (D200, D201). Igual que con la noche, el evaluador recibe la serie ya
 * armada y no sabe de dónde salió.
 */
public final class EvaluadorDeEstado {

    private final ClasificadorDeParametros clasificador;
    private final ReglaDeAgregacion regla;
    private final RedactorDeRecomendaciones redactor;
    private final ReglaDeLuzAcumulada reglaDeLuz;
    private final ReglaDeHumedadTrasRiego reglaDeRiego;

    public EvaluadorDeEstado(ClasificadorDeParametros clasificador, ReglaDeAgregacion regla,
                              RedactorDeRecomendaciones redactor, ReglaDeLuzAcumulada reglaDeLuz,
                              ReglaDeHumedadTrasRiego reglaDeRiego) {
        this.clasificador = clasificador;
        this.regla = regla;
        this.redactor = redactor;
        this.reglaDeLuz = reglaDeLuz;
        this.reglaDeRiego = reglaDeRiego;
    }

    /** Con la regla de riego de la fase 15; conservarlo evita tocar a quienes lo armaban en la fase 8. */
    public EvaluadorDeEstado(ClasificadorDeParametros clasificador, ReglaDeAgregacion regla,
                              RedactorDeRecomendaciones redactor, ReglaDeLuzAcumulada reglaDeLuz) {
        this(clasificador, regla, redactor, reglaDeLuz, new ReglaDeHumedadTrasRiego());
    }

    /** Con la regla de luz de la fase 8; conservarlo evita tocar a quienes lo armaban antes. */
    public EvaluadorDeEstado(ClasificadorDeParametros clasificador, ReglaDeAgregacion regla,
                              RedactorDeRecomendaciones redactor) {
        this(clasificador, regla, redactor, new ReglaDeLuzAcumulada());
    }

    /** Sin historial (RF-F6): clasificación instantánea, de día. */
    public Diagnostico evaluar(Especie especie, Medicion medicion) {
        return evaluar(especie, medicion, Instant.now(), SerieTemporal.vacia());
    }

    /**
     * Con historial: {@code luzAnterior} son los lux de las lecturas previas de la planta
     * (la aplicación los trae por el puerto; aquí se decide qué significan) y
     * {@code tomadaEn} es cuándo se tomó esta medición. Sin historial de humedad (fase 8).
     */
    public Diagnostico evaluar(Especie especie, Medicion medicion, Instant tomadaEn, SerieTemporal luzAnterior) {
        return evaluar(especie, medicion, tomadaEn, luzAnterior, SerieTemporal.vacia());
    }

    /**
     * Con historial de luz y de humedad: {@code humedadAnterior} son los valores de humedad de
     * las lecturas previas de la planta, con los que se ve si hubo un riego (fase 15).
     */
    public Diagnostico evaluar(Especie especie, Medicion medicion, Instant tomadaEn, SerieTemporal luzAnterior,
                               SerieTemporal humedadAnterior) {
        List<ResultadoParametro> parametros = new ArrayList<>(clasificador.clasificar(especie, medicion));
        Periodo periodo = Periodo.DIA;
        ExposicionLuminica exposicion = null;

        Magnitud luz = reglaDeLuz.magnitud();
        if (medicion.contiene(luz)) {
            double luxAhora = medicion.valor(luz);
            Optional<ExposicionLuminica> medida = reglaDeLuz.medir(luzAnterior, tomadaEn, luxAhora);
            if (medida.isPresent()) {
                exposicion = medida.get();
                periodo = reglaDeLuz.periodo(exposicion);
                ResultadoParametro acumulado = reglaDeLuz.clasificar(especie.rango(luz), luxAhora, exposicion);
                parametros.replaceAll(p -> p.magnitud() == luz ? acumulado : p);
            }
        }

        Instant regadaEn = null;
        Magnitud humedad = reglaDeRiego.magnitud();
        if (medicion.contiene(humedad)) {
            regadaEn = reglaDeRiego.ultimoRiego(humedadAnterior, tomadaEn, medicion.valor(humedad)).orElse(null);
        }

        List<ResultadoParametro> queCuentan = reglaDeRiego.queCuentanParaElEstado(
                reglaDeLuz.queCuentanParaElEstado(parametros, periodo), regadaEn);
        EstadoGlobal estado = queCuentan.isEmpty() ? EstadoGlobal.SALUDABLE : regla.agregar(queCuentan);
        List<String> recomendaciones = recomendaciones(parametros, queCuentan, regadaEn, tomadaEn);
        return new Diagnostico(especie, medicion, estado, parametros, recomendaciones, Instant.now(),
                periodo, exposicion, regadaEn);
    }

    /**
     * Una por cada parámetro que cuenta y está fuera de rango, en el orden de los parámetros; la
     * humedad tolerada tras un riego lleva la suya en su lugar. Así quien las presenta puede
     * seguir emparejándolas con los parámetros fuera de rango por posición.
     */
    private List<String> recomendaciones(List<ResultadoParametro> parametros, List<ResultadoParametro> queCuentan,
                                         Instant regadaEn, Instant tomadaEn) {
        List<String> recomendaciones = new ArrayList<>(redactor.redactar(queCuentan));
        if (regadaEn == null) {
            return recomendaciones;
        }
        int posicion = 0;
        for (ResultadoParametro parametro : parametros) {
            if (reglaDeRiego.tolerada(parametro)) {
                recomendaciones.add(posicion, reglaDeRiego.recomendacionTrasRiego(parametro, regadaEn, tomadaEn));
                break;
            }
            if (parametro.fueraDeRango() && queCuentan.contains(parametro)) {
                posicion++;
            }
        }
        return recomendaciones;
    }
}
