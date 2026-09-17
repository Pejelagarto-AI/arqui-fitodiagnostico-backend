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

class EstadoNecesitaAguaTest {

    private final EstadoNecesitaAgua estado = new EstadoNecesitaAgua();

    // Monstera deliciosa: humedad 55.0 - 80.0 % HR
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
    void noAplicaExactamenteEnElMinimoDeHumedad() {
        Medicion medicion = medicion(20.0, 55.0, 1500);
        assertThat(estado.evaluarEstado(especie, medicion)).isFalse();
    }

    @Test
    void aplicaJustoBajoElMinimoDeHumedad() {
        Medicion medicion = medicion(20.0, 54.9, 1500);
        assertThat(estado.evaluarEstado(especie, medicion)).isTrue();
    }

    @Test
    void noAplicaExactamenteEnElMaximoDeHumedad() {
        Medicion medicion = medicion(20.0, 80.0, 1500);
        assertThat(estado.evaluarEstado(especie, medicion)).isFalse();
    }

    @Test
    void obtenerEstadoDevuelveElNombreConstante() {
        assertThat(estado.obtenerEstado()).isEqualTo("NECESITA_AGUA");
    }

    @Test
    void describirIncluyeElValorLeidoYElMinimoDeLaEspecie() {
        Medicion medicion = medicion(20.0, 42.0, 1500);
        assertThat(estado.describir(especie, medicion))
                .contains("42.0").contains("55.0");
    }
}
