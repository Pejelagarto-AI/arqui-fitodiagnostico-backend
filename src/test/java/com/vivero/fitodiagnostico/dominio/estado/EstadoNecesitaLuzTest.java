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

class EstadoNecesitaLuzTest {

    private final EstadoNecesitaLuz estado = new EstadoNecesitaLuz();

    // Monstera deliciosa: luz 1000 - 2500 lux
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
    void noAplicaExactamenteEnElMinimoDeLuz() {
        Medicion medicion = medicion(20.0, 60.0, 1000);
        assertThat(estado.evaluarEstado(especie, medicion)).isFalse();
    }

    @Test
    void aplicaJustoBajoElMinimoDeLuz() {
        Medicion medicion = medicion(20.0, 60.0, 999);
        assertThat(estado.evaluarEstado(especie, medicion)).isTrue();
    }

    @Test
    void noAplicaExactamenteEnElMaximoDeLuz() {
        Medicion medicion = medicion(20.0, 60.0, 2500);
        assertThat(estado.evaluarEstado(especie, medicion)).isFalse();
    }

    @Test
    void obtenerEstadoDevuelveElNombreConstante() {
        assertThat(estado.obtenerEstado()).isEqualTo("NECESITA_LUZ");
    }

    @Test
    void describirIncluyeElValorLeidoYElMinimoDeLaEspecie() {
        Medicion medicion = medicion(20.0, 60.0, 850);
        assertThat(estado.describir(especie, medicion))
                .contains("850").contains("1000");
    }
}
