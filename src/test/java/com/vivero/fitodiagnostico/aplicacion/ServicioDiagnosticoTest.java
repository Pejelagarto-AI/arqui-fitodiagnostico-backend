package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.excepcion.EspecieNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.puerto.RangosPorEspecie;
import com.vivero.fitodiagnostico.dominio.servicio.ClasificadorDeParametros;
import com.vivero.fitodiagnostico.dominio.servicio.EvaluadorDeEstado;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaPorDesviacion;
import com.vivero.fitodiagnostico.dominio.servicio.RedactorDeRecomendaciones;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba de aplicación (sección 11, RA4/RA5): ejercita {@link ServicioDiagnostico}
 * de punta a punta —clasificación, agregación y recomendaciones incluidas—
 * con un doble en memoria de {@link RangosPorEspecie} (lambda, sin Mockito) y
 * los objetos de dominio reales que ensambla {@code ConfiguracionDominio}. Sin
 * Spring, sin levantar el servidor y sin leer ningún CSV o base de datos: si
 * se borrara toda la carpeta de infraestructura esta prueba seguiría
 * compilando y pasando (ver scripts/probar-dominio-sin-infraestructura.sh).
 */
class ServicioDiagnosticoTest {

    // Anexo B: sansevieria, humedad 20-45 %, luz 200-1500 lux, temperatura 15-29 °C.
    private static Especie sansevieria() {
        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        rangos.put(Magnitud.HUMEDAD, new Rango(20.0, 45.0));
        rangos.put(Magnitud.LUZ, new Rango(200, 1500));
        rangos.put(Magnitud.TEMPERATURA, new Rango(15.0, 29.0));
        return new Especie("sansevieria", rangos);
    }

    private static ServicioDiagnostico servicioCon(RangosPorEspecie rangosPorEspecie) {
        EvaluadorDeEstado evaluador = new EvaluadorDeEstado(
                new ClasificadorDeParametros(),
                new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO),
                new RedactorDeRecomendaciones());
        return new ServicioDiagnostico(rangosPorEspecie, evaluador);
    }

    @Test
    void losTresParametrosDentroDeRangoDanSaludable() {
        ServicioDiagnostico servicio = servicioCon(nombre -> Optional.of(sansevieria()));

        Medicion medicion = Medicion.de(
                new Lectura(Magnitud.HUMEDAD, 32.5),
                new Lectura(Magnitud.LUZ, 850),
                new Lectura(Magnitud.TEMPERATURA, 21.0));

        var diagnostico = servicio.diagnosticar("sansevieria", medicion);

        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.SALUDABLE);
        assertThat(diagnostico.recomendaciones()).isEmpty();
    }

    @Test
    void unaDesviacionLeveDaEnRiesgo() {
        ServicioDiagnostico servicio = servicioCon(nombre -> Optional.of(sansevieria()));

        // humedad 17 % (rango 20-45, ancho 25): desviación (20-17)/25 = 0.12 <= 0.25 -> EN_RIESGO.
        Medicion medicion = Medicion.de(
                new Lectura(Magnitud.HUMEDAD, 17.0),
                new Lectura(Magnitud.LUZ, 850),
                new Lectura(Magnitud.TEMPERATURA, 21.0));

        var diagnostico = servicio.diagnosticar("sansevieria", medicion);

        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.EN_RIESGO);
        assertThat(diagnostico.recomendaciones()).hasSize(1);
    }

    @Test
    void unaDesviacionSevereDaCritico() {
        ServicioDiagnostico servicio = servicioCon(nombre -> Optional.of(sansevieria()));

        // humedad 10 % (rango 20-45, ancho 25): desviación (20-10)/25 = 0.4 > 0.25 -> CRITICO.
        Medicion medicion = Medicion.de(
                new Lectura(Magnitud.HUMEDAD, 10.0),
                new Lectura(Magnitud.LUZ, 850),
                new Lectura(Magnitud.TEMPERATURA, 21.0));

        var diagnostico = servicio.diagnosticar("sansevieria", medicion);

        assertThat(diagnostico.estado()).isEqualTo(EstadoGlobal.CRITICO);
    }

    @Test
    void especieInexistenteLanzaLaExcepcionDeDominio() {
        ServicioDiagnostico servicio = servicioCon(nombre -> Optional.empty());

        Medicion medicion = Medicion.de(new Lectura(Magnitud.HUMEDAD, 30.0));

        assertThatThrownBy(() -> servicio.diagnosticar("planta-fantasma", medicion))
                .isInstanceOf(EspecieNoEncontradaException.class);
    }

    @Test
    void elServicioConsultaElPuertoExactamenteUnaVez() {
        AtomicInteger llamadas = new AtomicInteger();
        RangosPorEspecie doble = nombre -> {
            llamadas.incrementAndGet();
            return Optional.of(sansevieria());
        };
        ServicioDiagnostico servicio = servicioCon(doble);

        servicio.diagnosticar("sansevieria", Medicion.de(new Lectura(Magnitud.HUMEDAD, 30.0)));

        assertThat(llamadas.get()).isEqualTo(1);
    }
}
