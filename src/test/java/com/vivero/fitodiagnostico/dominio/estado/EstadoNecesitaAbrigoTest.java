package com.vivero.fitodiagnostico.dominio.estado;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EstadoNecesitaAbrigoTest {

    private final EstadoNecesitaAbrigo estado = new EstadoNecesitaAbrigo();

    // Monstera deliciosa: temperatura 18.0 - 29.0 °C
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
    void noAplicaExactamenteEnElMinimoDeTemperatura() {
        Medicion medicion = medicion(18.0, 60.0, 1500);
        assertThat(estado.evaluarEstado(especie, medicion)).isFalse();
    }

    @Test
    void noAplicaExactamenteEnElMaximoDeTemperatura() {
        Medicion medicion = medicion(29.0, 60.0, 1500);
        assertThat(estado.evaluarEstado(especie, medicion)).isFalse();
    }

    @Test
    void aplicaJustoBajoElMinimoDeTemperatura() {
        Medicion medicion = medicion(17.9, 60.0, 1500);
        assertThat(estado.evaluarEstado(especie, medicion)).isTrue();
    }

    @Test
    void aplicaJustoSobreElMaximoDeTemperatura() {
        Medicion medicion = medicion(29.1, 60.0, 1500);
        assertThat(estado.evaluarEstado(especie, medicion)).isTrue();
    }

    @Test
    void obtenerEstadoDevuelveElNombreConstante() {
        assertThat(estado.obtenerEstado()).isEqualTo("NECESITA_ABRIGO");
    }

    @Test
    void describirDistingueCuandoEstaPorDebajoDelMinimo() {
        Medicion medicion = medicion(10.0, 60.0, 1500);
        assertThat(estado.describir(especie, medicion))
                .contains("10.0").contains("por debajo").contains("18.0");
    }

    @Test
    void describirDistingueCuandoEstaPorEncimaDelMaximo() {
        Medicion medicion = medicion(35.0, 60.0, 1500);
        assertThat(estado.describir(especie, medicion))
                .contains("35.0").contains("por encima").contains("29.0");
    }
}
