package com.vivero.fitodiagnostico.dominio.modelo;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EspecieTest {

    private static Map<Magnitud, Rango> rangosMonstera() {
        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        rangos.put(Magnitud.TEMPERATURA, new Rango(18.0, 29.0));
        rangos.put(Magnitud.HUMEDAD, new Rango(55.0, 80.0));
        rangos.put(Magnitud.LUZ, new Rango(1000, 2500));
        return rangos;
    }

    @Test
    void nombreCientificoEnBlancoLanza() {
        assertThatThrownBy(() -> new Especie(" ", "comun", rangosMonstera()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rangosVaciosLanza() {
        assertThatThrownBy(() -> new Especie("Monstera deliciosa", "comun", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rangoDeUnaMagnitudSinDefinicionLanza() {
        Map<Magnitud, Rango> soloTemperatura = new EnumMap<>(Magnitud.class);
        soloTemperatura.put(Magnitud.TEMPERATURA, new Rango(18.0, 29.0));
        Especie especie = new Especie("Monstera deliciosa", "costilla de Adán", soloTemperatura);

        assertThatThrownBy(() -> especie.rango(Magnitud.HUMEDAD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rangoDevuelveElRangoDeLaMagnitudPedida() {
        Especie especie = new Especie("Monstera deliciosa", "costilla de Adán", rangosMonstera());
        assertThat(especie.rango(Magnitud.HUMEDAD)).isEqualTo(new Rango(55.0, 80.0));
    }

    @Test
    void elMapaDeRangosEsInmutableDesdeFuera() {
        Especie especie = new Especie("Monstera deliciosa", "costilla de Adán", rangosMonstera());
        assertThatThrownBy(() -> especie.rangos().put(Magnitud.LUZ, new Rango(0.0, 1.0)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mutarElMapaOriginalDespuesDeConstruirNoAfectaLaEspecie() {
        Map<Magnitud, Rango> rangosMutables = rangosMonstera();
        Especie especie = new Especie("Monstera deliciosa", "costilla de Adán", rangosMutables);

        rangosMutables.put(Magnitud.LUZ, new Rango(0.0, 1.0));

        assertThat(especie.rango(Magnitud.LUZ)).isEqualTo(new Rango(1000, 2500));
    }
}
