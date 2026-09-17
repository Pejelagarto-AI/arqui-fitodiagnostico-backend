package com.vivero.fitodiagnostico.dominio.modelo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class ResultadoParametroTest {

    @Test
    void desviacionEsCeroEnOptimo() {
        Rango rango = new Rango(10.0, 20.0);
        ResultadoParametro resultado = new ResultadoParametro(
                Magnitud.TEMPERATURA, 15.0, rango, Clasificacion.OPTIMO);

        assertThat(resultado.desviacionRelativa()).isEqualTo(0.0);
    }

    @Test
    void desviacionEnBajoEsLaFraccionDelAnchoQueFaltaHastaElMinimo() {
        Rango rango = new Rango(10.0, 20.0); // ancho 10
        ResultadoParametro resultado = new ResultadoParametro(
                Magnitud.TEMPERATURA, 7.0, rango, Clasificacion.BAJO);

        // (10 - 7) / 10 = 0.30
        assertThat(resultado.desviacionRelativa()).isCloseTo(0.30, offset(1e-9));
    }

    @Test
    void desviacionEnAltoEsLaFraccionDelAnchoQueSobrepasaElMaximo() {
        Rango rango = new Rango(10.0, 20.0); // ancho 10
        ResultadoParametro resultado = new ResultadoParametro(
                Magnitud.TEMPERATURA, 25.0, rango, Clasificacion.ALTO);

        // (25 - 20) / 10 = 0.50
        assertThat(resultado.desviacionRelativa()).isCloseTo(0.50, offset(1e-9));
    }

    @Test
    void ejemploDeReferenciaMonsteraHumedad50DaDesviacion020() {
        // Monstera: humedad óptima 55-80, ancho 25. humedad 50 -> BAJO.
        Rango rango = new Rango(55.0, 80.0);
        ResultadoParametro resultado = new ResultadoParametro(
                Magnitud.HUMEDAD, 50.0, rango, Clasificacion.BAJO);

        assertThat(resultado.desviacionRelativa()).isCloseTo(0.20, offset(1e-9));
    }

    @Test
    void ejemploDeReferenciaMonsteraHumedad42DaDesviacion052() {
        // humedad 42 -> BAJO.
        Rango rango = new Rango(55.0, 80.0);
        ResultadoParametro resultado = new ResultadoParametro(
                Magnitud.HUMEDAD, 42.0, rango, Clasificacion.BAJO);

        assertThat(resultado.desviacionRelativa()).isCloseTo(0.52, offset(1e-9));
    }
}
