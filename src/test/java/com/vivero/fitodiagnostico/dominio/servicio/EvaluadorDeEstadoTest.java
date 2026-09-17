package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.estado.*;
import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
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

    // Orden de prioridad tal como lo ensamblaría ConfiguracionDominio (sección 8).
    private final EvaluadorDeEstado evaluador = new EvaluadorDeEstado(List.of(
            new EstadoNecesitaAbrigo(),
            new EstadoNecesitaAgua(),
            new EstadoNecesitaLuz(),
            new EstadoOptimo()),
            new ClasificadorDeParametros());

    // Monstera deliciosa: temp 18-29 °C, humedad 55-80 % HR, luz 1000-2500 lux
    private final Especie especie = new Especie(
            "Monstera deliciosa", "costilla de Adán", rangosMonstera());

    private static Map<Magnitud, Rango> rangosMonstera() {
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
    void conLasTresLecturasDentroDeRangoDevuelveOptimo() {
        Medicion medicion = medicion(20.0, 60.0, 1500);
        Diagnostico diagnostico = evaluador.evaluar(especie, medicion);
        assertThat(diagnostico.estado()).isEqualTo("OPTIMO");
    }

    @Test
    void devuelveElPrimerEstadoQueAplicaCuandoSoloUnoFalla() {
        // Solo la humedad está fuera de rango: debe ganar NECESITA_AGUA.
        Medicion medicion = medicion(20.0, 40.0, 1500);
        Diagnostico diagnostico = evaluador.evaluar(especie, medicion);
        assertThat(diagnostico.estado()).isEqualTo("NECESITA_AGUA");
    }

    @Test
    void laTemperaturaFueraDeRangoTienePrioridadSobreLaHumedadYLaLuz() {
        // Las tres variables están fuera de rango a la vez: debe ganar la de
        // mayor prioridad en la lista, NECESITA_ABRIGO, no las otras dos.
        Medicion medicion = medicion(5.0, 20.0, 200);
        Diagnostico diagnostico = evaluador.evaluar(especie, medicion);
        assertThat(diagnostico.estado()).isEqualTo("NECESITA_ABRIGO");
    }

    @Test
    void laHumedadTienePrioridadSobreLaLuzCuandoAmbasFallan() {
        // Temperatura ok, humedad y luz fuera de rango: debe ganar NECESITA_AGUA
        // sobre NECESITA_LUZ porque así está ordenada la lista.
        Medicion medicion = medicion(20.0, 40.0, 200);
        Diagnostico diagnostico = evaluador.evaluar(especie, medicion);
        assertThat(diagnostico.estado()).isEqualTo("NECESITA_AGUA");
    }

    @Test
    void elDiagnosticoConservaLaEspecieYLaMedicionRecibidas() {
        Medicion medicion = medicion(20.0, 60.0, 1500);
        Diagnostico diagnostico = evaluador.evaluar(especie, medicion);
        assertThat(diagnostico.especie()).isEqualTo(especie);
        assertThat(diagnostico.medicion()).isEqualTo(medicion);
        assertThat(diagnostico.detalle()).isNotBlank();
        assertThat(diagnostico.evaluadoEn()).isNotNull();
    }

    @Test
    void elDiagnosticoTraeLosParametrosClasificados() {
        // Solo la humedad está fuera de rango (BAJO); temperatura y luz OPTIMO.
        Medicion medicion = medicion(20.0, 40.0, 1500);
        Diagnostico diagnostico = evaluador.evaluar(especie, medicion);

        assertThat(diagnostico.parametros()).extracting(ResultadoParametro::magnitud)
                .containsExactly(Magnitud.TEMPERATURA, Magnitud.HUMEDAD, Magnitud.LUZ);
        assertThat(diagnostico.parametros()).extracting(ResultadoParametro::clasificacion)
                .containsExactly(Clasificacion.OPTIMO, Clasificacion.BAJO, Clasificacion.OPTIMO);
    }

    @Test
    void siNingunEstadoAplicaLanzaIllegalStateException() {
        // Sin un estado terminal en la lista, la cadena debe fallar explícitamente.
        EvaluadorDeEstado evaluadorSinTerminal =
                new EvaluadorDeEstado(List.of(new EstadoNecesitaAgua()), new ClasificadorDeParametros());
        Medicion medicion = medicion(20.0, 60.0, 1500);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> evaluadorSinTerminal.evaluar(especie, medicion));
    }
}
