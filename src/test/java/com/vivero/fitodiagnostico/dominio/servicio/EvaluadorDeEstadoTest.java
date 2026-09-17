package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluadorDeEstadoTest {

    // especie de prueba: temp 18-29 °C, humedad 55-80 % HR, luz 1000-2500 lux
    private final Especie especie = new Especie("especie-de-prueba", rangosDePrueba());

    private static Map<Magnitud, Rango> rangosDePrueba() {
        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        rangos.put(Magnitud.TEMPERATURA, new Rango(18.0, 29.0));
        rangos.put(Magnitud.HUMEDAD, new Rango(55.0, 80.0));
        rangos.put(Magnitud.LUZ, new Rango(1000, 2500));
        return rangos;
    }

    private static Medicion medicion(double temperaturaC, double humedadRelativa, int luzLux) {
        return Medicion.de(
                new Lectura(Magnitud.TEMPERATURA, temperaturaC),
                new Lectura(Magnitud.HUMEDAD, humedadRelativa),
                new Lectura(Magnitud.LUZ, luzLux));
    }

    @Test
    void delegaElEstadoGlobalEnLaReglaDeAgregacionSinConocerCualEs() {
        // Doble de ReglaDeAgregacion: pase lo que pase en la clasificación,
        // esta regla siempre dice CRITICO. Si el evaluador devuelve CRITICO
        // aquí, es porque delega en la regla y no decide nada por su cuenta.
        ReglaDeAgregacion reglaFija = parametros -> EstadoGlobal.CRITICO;
        EvaluadorDeEstado evaluador = new EvaluadorDeEstado(
                new ClasificadorDeParametros(), reglaFija, new RedactorDeRecomendaciones());

        Diagnostico diagnostico = evaluador.evaluar(especie, medicion(20.0, 60.0, 1500));

        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.CRITICO);
    }

    @Test
    void laReglaFijaNoImpideQueLosParametrosSeClasifiquenDeVerdad() {
        ReglaDeAgregacion reglaFija = parametros -> EstadoGlobal.SALUDABLE;
        EvaluadorDeEstado evaluador = new EvaluadorDeEstado(
                new ClasificadorDeParametros(), reglaFija, new RedactorDeRecomendaciones());

        // Humedad fuera de rango: la clasificación real debe reflejarlo
        // aunque la regla (doble) ignore el detalle y siempre diga SALUDABLE.
        // Orden del enum Magnitud: HUMEDAD, LUZ, TEMPERATURA.
        Diagnostico diagnostico = evaluador.evaluar(especie, medicion(20.0, 40.0, 1500));

        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.SALUDABLE);
        assertThat(diagnostico.parametros()).extracting(ResultadoParametro::clasificacion)
                .containsExactly(Clasificacion.BAJO, Clasificacion.OPTIMO, Clasificacion.OPTIMO);
    }

    @Test
    void conLaReglaRealYLasTresLecturasDentroDeRangoDevuelveSaludable() {
        EvaluadorDeEstado evaluador = new EvaluadorDeEstado(
                new ClasificadorDeParametros(),
                new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO),
                new RedactorDeRecomendaciones());

        Diagnostico diagnostico = evaluador.evaluar(especie, medicion(20.0, 60.0, 1500));

        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.SALUDABLE);
        assertThat(diagnostico.recomendaciones()).isEmpty();
    }

    @Test
    void conLaReglaRealYUnaDesviacionSevereDevuelveCritico() {
        EvaluadorDeEstado evaluador = new EvaluadorDeEstado(
                new ClasificadorDeParametros(),
                new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO),
                new RedactorDeRecomendaciones());

        // humedad 42 -> desviación 0.52 sobre el ejemplo de referencia.
        Diagnostico diagnostico = evaluador.evaluar(especie, medicion(20.0, 42.0, 1500));

        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.CRITICO);
    }

    @Test
    void elDiagnosticoConservaLaEspecieYLaMedicionRecibidas() {
        EvaluadorDeEstado evaluador = new EvaluadorDeEstado(
                new ClasificadorDeParametros(),
                new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO),
                new RedactorDeRecomendaciones());

        Medicion medicion = medicion(20.0, 60.0, 1500);
        Diagnostico diagnostico = evaluador.evaluar(especie, medicion);

        assertThat(diagnostico.especie()).isEqualTo(especie);
        assertThat(diagnostico.medicion()).isEqualTo(medicion);
        assertThat(diagnostico.evaluadoEn()).isNotNull();
    }

    @Test
    void elDiagnosticoTraeLosParametrosClasificados() {
        EvaluadorDeEstado evaluador = new EvaluadorDeEstado(
                new ClasificadorDeParametros(),
                new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO),
                new RedactorDeRecomendaciones());

        // Solo la humedad está fuera de rango (BAJO); luz y temperatura OPTIMO.
        // Orden del enum Magnitud: HUMEDAD, LUZ, TEMPERATURA.
        Diagnostico diagnostico = evaluador.evaluar(especie, medicion(20.0, 40.0, 1500));

        assertThat(diagnostico.parametros()).extracting(ResultadoParametro::magnitud)
                .containsExactly(Magnitud.HUMEDAD, Magnitud.LUZ, Magnitud.TEMPERATURA);
        assertThat(diagnostico.parametros()).extracting(ResultadoParametro::clasificacion)
                .containsExactly(Clasificacion.BAJO, Clasificacion.OPTIMO, Clasificacion.OPTIMO);
    }

    @Test
    void elDiagnosticoTraeUnaRecomendacionPorCadaParametroFueraDeRango() {
        EvaluadorDeEstado evaluador = new EvaluadorDeEstado(
                new ClasificadorDeParametros(),
                new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO),
                new RedactorDeRecomendaciones());

        // Solo la humedad está fuera de rango (BAJO, 40 < 55).
        Diagnostico diagnostico = evaluador.evaluar(especie, medicion(20.0, 40.0, 1500));

        assertThat(diagnostico.recomendaciones()).containsExactly(
                "Humedad del sustrato 40 % por debajo del rango óptimo 55–80 %: regar moderadamente.");
    }

    @Test
    void listaVaciaDeParametrosPropagaLaExcepcionDeLaReglaReal() {
        // clasificar() nunca produce lista vacía con una Medicion válida (que
        // exige al menos una lectura), pero el contrato de la regla real ante
        // lista vacía se prueba directamente en ReglaPorDesviacionTest.
        ReglaDeAgregacion regla = new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> regla.agregar(List.of()));
    }
}
