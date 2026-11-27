package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.Muestra;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import com.vivero.fitodiagnostico.dominio.modelo.SerieTemporal;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** El evaluador con la regla de riego (fase 15): el caso de la suculenta recién regada que motivó el cambio. */
class EvaluadorConRiegoTest {

    private static final Instant AHORA = Instant.parse("2026-11-26T17:00:00Z");
    private static final Duration MINUTO = Duration.ofMinutes(1);

    // Suculenta del catálogo: humedad 10-30 %, luz 800-2500 lux, temperatura 15-32 °C.
    private final Especie suculenta = new Especie("suculenta", Map.of(
            Magnitud.HUMEDAD, new Rango(10, 30),
            Magnitud.LUZ, new Rango(800, 2500),
            Magnitud.TEMPERATURA, new Rango(15, 32)));

    private final EvaluadorDeEstado evaluador = new EvaluadorDeEstado(new ClasificadorDeParametros(),
            new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO), new RedactorDeRecomendaciones());

    private static Medicion medicion(double humedad, double luz, double temperatura) {
        return Medicion.de(new Lectura(Magnitud.HUMEDAD, humedad), new Lectura(Magnitud.LUZ, luz),
                new Lectura(Magnitud.TEMPERATURA, temperatura));
    }

    /** Media hora de tierra seca (20 %) y el riego hace {@code minutos}: 95 % desde entonces. */
    private static SerieTemporal regadaHace(int minutos) {
        List<Muestra> muestras = new ArrayList<>();
        for (int k = minutos + 30; k > minutos; k--) {
            muestras.add(new Muestra(AHORA.minus(MINUTO.multipliedBy(k)), 20));
        }
        for (int k = minutos; k >= 1; k--) {
            muestras.add(new Muestra(AHORA.minus(MINUTO.multipliedBy(k)), 95));
        }
        return SerieTemporal.de(muestras);
    }

    /** Dos horas en 95 %, sin ninguna subida: encharcada desde antes de que hubiera historial. */
    private static SerieTemporal siempreEmpapada() {
        List<Muestra> muestras = new ArrayList<>();
        for (int k = 120; k >= 1; k--) {
            muestras.add(new Muestra(AHORA.minus(MINUTO.multipliedBy(k)), 95));
        }
        return SerieTemporal.de(muestras);
    }

    private static ResultadoParametro humedad(Diagnostico diagnostico) {
        return diagnostico.parametros().stream().filter(p -> p.magnitud() == Magnitud.HUMEDAD).findFirst()
                .orElseThrow();
    }

    @Test
    void recienRegadaEstaSaludableAunqueLaHumedadPaseDelMaximo() {
        Diagnostico diagnostico = evaluador.evaluar(suculenta, medicion(97, 1000, 22), AHORA,
                SerieTemporal.vacia(), regadaHace(3));

        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.SALUDABLE);
        assertThat(diagnostico.recienRegada()).isTrue();
        assertThat(diagnostico.regadaEn()).isEqualTo(AHORA.minus(MINUTO.multipliedBy(3)));
        // El dato no se maquilla: la humedad sigue diciendo ALTO.
        assertThat(humedad(diagnostico).clasificacion()).isEqualTo(Clasificacion.ALTO);
        assertThat(diagnostico.recomendaciones()).containsExactly(
                "Humedad del sustrato 97 % por encima del rango óptimo 10–30 %: la regaste hace 3 min"
                        + " y quedó bien hidratada; no volver a regar hasta que baje de 30 %.");
    }

    @Test
    void empapadaSinUnRiegoQueLaExpliqueSigueSiendoExceso() {
        Diagnostico diagnostico = evaluador.evaluar(suculenta, medicion(95, 1000, 22), AHORA,
                SerieTemporal.vacia(), siempreEmpapada());

        assertThat(diagnostico.recienRegada()).isFalse();
        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.CRITICO);
        assertThat(diagnostico.recomendaciones()).singleElement().asString()
                .contains("suspender el riego y revisar el drenaje");
    }

    @Test
    void laNotaDelRiegoVaEnElLugarDeLaHumedadYLaLuzSigueContando() {
        Diagnostico diagnostico = evaluador.evaluar(suculenta, medicion(97, 200, 22), AHORA,
                SerieTemporal.vacia(), regadaHace(10));

        // El estado lo pone la luz (200 lux contra 800-2500), no la humedad.
        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.CRITICO);
        assertThat(diagnostico.recomendaciones()).hasSize(2);
        assertThat(diagnostico.recomendaciones().get(0)).contains("la regaste hace 10 min");
        assertThat(diagnostico.recomendaciones().get(1)).startsWith("Luz 200 lux por debajo");
    }

    @Test
    void siYaSecoHastaElRangoNoHayNadaQueDecir() {
        Diagnostico diagnostico = evaluador.evaluar(suculenta, medicion(25, 1000, 22), AHORA,
                SerieTemporal.vacia(), regadaHace(3));

        assertThat(diagnostico.recienRegada()).isTrue();
        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.SALUDABLE);
        assertThat(diagnostico.recomendaciones()).isEmpty();
    }

    @Test
    void sinHistorialDeHumedadTodoSigueComoAntes() {
        Diagnostico conLaFirmaDeLaFase8 = evaluador.evaluar(suculenta, medicion(97, 1000, 22), AHORA,
                SerieTemporal.vacia());
        Diagnostico sinNada = evaluador.evaluar(suculenta, medicion(97, 1000, 22));

        assertThat(conLaFirmaDeLaFase8.recienRegada()).isFalse();
        assertThat(conLaFirmaDeLaFase8.estado()).isEqualTo(EstadoGlobal.CRITICO);
        assertThat(sinNada.recienRegada()).isFalse();
        assertThat(sinNada.estado()).isEqualTo(EstadoGlobal.CRITICO);
    }
}
