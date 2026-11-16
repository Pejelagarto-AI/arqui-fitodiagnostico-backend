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
 */
public final class EvaluadorDeEstado {

    private final ClasificadorDeParametros clasificador;
    private final ReglaDeAgregacion regla;
    private final RedactorDeRecomendaciones redactor;
    private final ReglaDeLuzAcumulada reglaDeLuz;

    public EvaluadorDeEstado(ClasificadorDeParametros clasificador, ReglaDeAgregacion regla,
                              RedactorDeRecomendaciones redactor, ReglaDeLuzAcumulada reglaDeLuz) {
        this.clasificador = clasificador;
        this.regla = regla;
        this.redactor = redactor;
        this.reglaDeLuz = reglaDeLuz;
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
     * {@code tomadaEn} es cuándo se tomó esta medición.
     */
    public Diagnostico evaluar(Especie especie, Medicion medicion, Instant tomadaEn, SerieTemporal luzAnterior) {
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

        List<ResultadoParametro> queCuentan = reglaDeLuz.queCuentanParaElEstado(parametros, periodo);
        EstadoGlobal estado = queCuentan.isEmpty() ? EstadoGlobal.SALUDABLE : regla.agregar(queCuentan);
        List<String> recomendaciones = redactor.redactar(queCuentan);
        return new Diagnostico(especie, medicion, estado, parametros, recomendaciones, Instant.now(),
                periodo, exposicion);
    }
}
