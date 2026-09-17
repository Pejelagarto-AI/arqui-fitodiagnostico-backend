package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReglaPorDesviacionTest {

    // Rango de ancho 10 en todos los casos, para que la desviación relativa
    // sea fácil de razonar: (10 - valor) / 10 para BAJO.
    private static final Rango RANGO = new Rango(10.0, 20.0);

    private final ReglaPorDesviacion regla = new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO);

    private static ResultadoParametro optimo(Magnitud magnitud) {
        return new ResultadoParametro(magnitud, 15.0, RANGO, Clasificacion.OPTIMO);
    }

    private static ResultadoParametro bajoConDesviacion(Magnitud magnitud, double desviacion) {
        double valor = RANGO.minimo() - desviacion * (RANGO.maximo() - RANGO.minimo());
        return new ResultadoParametro(magnitud, valor, RANGO, Clasificacion.BAJO);
    }

    @Test
    void todosOptimoDaSaludable() {
        EstadoGlobal estado = regla.agregar(List.of(
                optimo(Magnitud.TEMPERATURA), optimo(Magnitud.HUMEDAD), optimo(Magnitud.LUZ)));

        assertThat(estado).isEqualTo(EstadoGlobal.SALUDABLE);
    }

    @Test
    void unParametroFueraPorDebajoDelUmbralDaEnRiesgo() {
        EstadoGlobal estado = regla.agregar(List.of(
                optimo(Magnitud.TEMPERATURA),
                bajoConDesviacion(Magnitud.HUMEDAD, 0.10),
                optimo(Magnitud.LUZ)));

        assertThat(estado).isEqualTo(EstadoGlobal.EN_RIESGO);
    }

    @Test
    void unParametroExactamenteEnElUmbralDaEnRiesgoNoCritico() {
        EstadoGlobal estado = regla.agregar(List.of(
                bajoConDesviacion(Magnitud.HUMEDAD, 0.25)));

        assertThat(estado).isEqualTo(EstadoGlobal.EN_RIESGO);
    }

    @Test
    void unParametroPorEncimaDelUmbralDaCritico() {
        EstadoGlobal estado = regla.agregar(List.of(
                optimo(Magnitud.TEMPERATURA),
                bajoConDesviacion(Magnitud.HUMEDAD, 0.2501),
                optimo(Magnitud.LUZ)));

        assertThat(estado).isEqualTo(EstadoGlobal.CRITICO);
    }

    @Test
    void variosFueraDeRangoPeroTodosBajoElUmbralDaEnRiesgo() {
        EstadoGlobal estado = regla.agregar(List.of(
                bajoConDesviacion(Magnitud.TEMPERATURA, 0.10),
                bajoConDesviacion(Magnitud.HUMEDAD, 0.15),
                bajoConDesviacion(Magnitud.LUZ, 0.05)));

        assertThat(estado).isEqualTo(EstadoGlobal.EN_RIESGO);
    }

    @Test
    void unUmbralCeroLanza() {
        assertThatThrownBy(() -> new ReglaPorDesviacion(0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unUmbralNegativoLanza() {
        assertThatThrownBy(() -> new ReglaPorDesviacion(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaListaVaciaLanza() {
        assertThatThrownBy(() -> regla.agregar(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
