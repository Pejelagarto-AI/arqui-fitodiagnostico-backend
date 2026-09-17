package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RedactorDeRecomendacionesTest {

    private final RedactorDeRecomendaciones redactor = new RedactorDeRecomendaciones();

    @Test
    void unParametroBajoRecomiendaLaAccionDeBajo() {
        ResultadoParametro humedadBaja = new ResultadoParametro(
                Magnitud.HUMEDAD, 32.5, new Rango(40.0, 70.0), Clasificacion.BAJO);

        List<String> recomendaciones = redactor.redactar(List.of(humedadBaja));

        assertThat(recomendaciones).containsExactly(
                "Humedad del sustrato 32.5 % por debajo del rango óptimo 40–70 %: regar moderadamente.");
    }

    @Test
    void unParametroAltoRecomiendaLaAccionDeAlto() {
        ResultadoParametro luzAlta = new ResultadoParametro(
                Magnitud.LUZ, 3000, new Rango(1000.0, 2500.0), Clasificacion.ALTO);

        List<String> recomendaciones = redactor.redactar(List.of(luzAlta));

        assertThat(recomendaciones).containsExactly(
                "Luz 3000 lux por encima del rango óptimo 1000–2500 lux: protegerla de la luz directa.");
    }

    @Test
    void unParametroOptimoNoGeneraRecomendacion() {
        ResultadoParametro temperaturaOptima = new ResultadoParametro(
                Magnitud.TEMPERATURA, 20.0, new Rango(15.0, 29.0), Clasificacion.OPTIMO);

        List<String> recomendaciones = redactor.redactar(List.of(temperaturaOptima));

        assertThat(recomendaciones).isEmpty();
    }

    @Test
    void losNumerosEnterosNoMuestranDecimales() {
        ResultadoParametro humedadBaja = new ResultadoParametro(
                Magnitud.HUMEDAD, 40.0, new Rango(55.0, 80.0), Clasificacion.BAJO);

        List<String> recomendaciones = redactor.redactar(List.of(humedadBaja));

        assertThat(recomendaciones).containsExactly(
                "Humedad del sustrato 40 % por debajo del rango óptimo 55–80 %: regar moderadamente.");
    }

    @Test
    void respetaElOrdenDeLosParametrosYOmiteLosOptimos() {
        ResultadoParametro humedadBaja = new ResultadoParametro(
                Magnitud.HUMEDAD, 40.0, new Rango(55.0, 80.0), Clasificacion.BAJO);
        ResultadoParametro luzOptima = new ResultadoParametro(
                Magnitud.LUZ, 1500, new Rango(1000.0, 2500.0), Clasificacion.OPTIMO);
        ResultadoParametro temperaturaAlta = new ResultadoParametro(
                Magnitud.TEMPERATURA, 35.0, new Rango(18.0, 29.0), Clasificacion.ALTO);

        List<String> recomendaciones = redactor.redactar(
                List.of(humedadBaja, luzOptima, temperaturaAlta));

        assertThat(recomendaciones).containsExactly(
                "Humedad del sustrato 40 % por debajo del rango óptimo 55–80 %: regar moderadamente.",
                "Temperatura 35 °C por encima del rango óptimo 18–29 °C: llevarla a un lugar más fresco y ventilado.");
    }

    @Test
    void unaListaSinParametrosFueraDeRangoDevuelveListaVacia() {
        ResultadoParametro optimo = new ResultadoParametro(
                Magnitud.LUZ, 1500, new Rango(1000.0, 2500.0), Clasificacion.OPTIMO);

        assertThat(redactor.redactar(List.of(optimo))).isEmpty();
    }
}
