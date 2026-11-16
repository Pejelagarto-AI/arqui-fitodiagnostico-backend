package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.ExposicionLuminica;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Periodo;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Cuándo un cambio de estado se debe solo a la noche (D171). */
class CausaDelCambioDeEstadoTest {

    private final Rango rango = new Rango(30, 60);

    private ResultadoParametro p(Magnitud m, Clasificacion c) {
        return new ResultadoParametro(m, 45, rango, c);
    }

    private Diagnostico d(Periodo periodo, EstadoGlobal estado, Clasificacion humedad, Clasificacion luz) {
        ExposicionLuminica exposicion = periodo == Periodo.NOCHE ? new ExposicionLuminica(24, 9, 800, 700, 3) : null;
        return new Diagnostico(null, null, estado,
                List.of(p(Magnitud.HUMEDAD, humedad), p(Magnitud.LUZ, luz), p(Magnitud.TEMPERATURA, Clasificacion.OPTIMO)),
                List.of(), Instant.now(), periodo, exposicion);
    }

    @Test
    void entrarEnLaNocheConLaLuzComoUnicaFallaEsSoloElPeriodo() {
        Diagnostico dia = d(Periodo.DIA, EstadoGlobal.CRITICO, Clasificacion.OPTIMO, Clasificacion.BAJO);
        Diagnostico noche = d(Periodo.NOCHE, EstadoGlobal.SALUDABLE, Clasificacion.OPTIMO, Clasificacion.BAJO);

        assertThat(CausaDelCambioDeEstado.soloPorElPeriodo(dia, noche)).isTrue();
        assertThat(CausaDelCambioDeEstado.soloPorElPeriodo(noche, dia)).isTrue();   // y al amanecer
    }

    @Test
    void siCambioTambienLaHumedadYaNoEsSoloElPeriodo() {
        Diagnostico dia = d(Periodo.DIA, EstadoGlobal.CRITICO, Clasificacion.OPTIMO, Clasificacion.BAJO);
        Diagnostico noche = d(Periodo.NOCHE, EstadoGlobal.EN_RIESGO, Clasificacion.BAJO, Clasificacion.BAJO);

        assertThat(CausaDelCambioDeEstado.soloPorElPeriodo(dia, noche)).isFalse();
    }

    @Test
    void sinCambioDePeriodoNuncaEsElPeriodo() {
        Diagnostico antes = d(Periodo.DIA, EstadoGlobal.SALUDABLE, Clasificacion.OPTIMO, Clasificacion.OPTIMO);
        Diagnostico despues = d(Periodo.DIA, EstadoGlobal.CRITICO, Clasificacion.OPTIMO, Clasificacion.BAJO);

        assertThat(CausaDelCambioDeEstado.soloPorElPeriodo(antes, despues)).isFalse();
    }
}
