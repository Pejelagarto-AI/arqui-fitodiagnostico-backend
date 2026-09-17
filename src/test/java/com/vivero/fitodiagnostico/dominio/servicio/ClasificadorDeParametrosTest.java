package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClasificadorDeParametrosTest {

    private final ClasificadorDeParametros clasificador = new ClasificadorDeParametros();

    // especie de prueba: temp 18-29 °C, humedad 55-80 % HR, luz 1000-2500 lux
    private static Map<Magnitud, Rango> rangosMonstera() {
        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        rangos.put(Magnitud.TEMPERATURA, new Rango(18.0, 29.0));
        rangos.put(Magnitud.HUMEDAD, new Rango(55.0, 80.0));
        rangos.put(Magnitud.LUZ, new Rango(1000, 2500));
        return rangos;
    }

    private final Especie especie = new Especie("especie-de-prueba", rangosMonstera());

    @Test
    void clasificaLasTresLecturasEnElOrdenDeLaMedicion() {
        Medicion medicion = Medicion.de(
                new Lectura(Magnitud.TEMPERATURA, 20.0),
                new Lectura(Magnitud.HUMEDAD, 40.0),
                new Lectura(Magnitud.LUZ, 3000));

        List<ResultadoParametro> resultados = clasificador.clasificar(especie, medicion);

        assertThat(resultados).hasSize(3);
        // Orden del enum Magnitud: HUMEDAD, LUZ, TEMPERATURA.
        assertThat(resultados).extracting(ResultadoParametro::magnitud)
                .containsExactly(Magnitud.HUMEDAD, Magnitud.LUZ, Magnitud.TEMPERATURA);
        assertThat(resultados.get(0).clasificacion()).isEqualTo(Clasificacion.BAJO);
        assertThat(resultados.get(1).clasificacion()).isEqualTo(Clasificacion.ALTO);
        assertThat(resultados.get(2).clasificacion()).isEqualTo(Clasificacion.OPTIMO);
    }

    @Test
    void unaLecturaDeMagnitudSinRangoEnLaEspecieLanza() {
        Map<Magnitud, Rango> soloTemperatura = new EnumMap<>(Magnitud.class);
        soloTemperatura.put(Magnitud.TEMPERATURA, new Rango(18.0, 29.0));
        Especie especieIncompleta = new Especie("especie-de-prueba", soloTemperatura);
        Medicion medicion = Medicion.de(new Lectura(Magnitud.HUMEDAD, 40.0));

        assertThatThrownBy(() -> clasificador.clasificar(especieIncompleta, medicion))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HUMEDAD")
                .hasMessageContaining("especie-de-prueba");
    }

    @Test
    void clasificaUnaMedicionConUnaSolaMagnitudSinExigirLasDemas() {
        Map<Magnitud, Rango> soloLuz = new EnumMap<>(Magnitud.class);
        soloLuz.put(Magnitud.LUZ, new Rango(1000, 2500));
        Especie especieSoloLuz = new Especie("especie-de-prueba", soloLuz);
        Medicion medicion = Medicion.de(new Lectura(Magnitud.LUZ, 500));

        List<ResultadoParametro> resultados = clasificador.clasificar(especieSoloLuz, medicion);

        assertThat(resultados).hasSize(1);
        assertThat(resultados.get(0).magnitud()).isEqualTo(Magnitud.LUZ);
        assertThat(resultados.get(0).clasificacion()).isEqualTo(Clasificacion.BAJO);
        assertThat(resultados.get(0).fueraDeRango()).isTrue();
    }

    @Test
    void unResultadoOptimoNoEstaFueraDeRango() {
        Medicion medicion = Medicion.de(new Lectura(Magnitud.TEMPERATURA, 20.0));
        Especie especieSoloTemp = new Especie("especie-de-prueba",
                Map.of(Magnitud.TEMPERATURA, new Rango(18.0, 29.0)));

        List<ResultadoParametro> resultados = clasificador.clasificar(especieSoloTemp, medicion);

        assertThat(resultados.get(0).fueraDeRango()).isFalse();
    }
}
