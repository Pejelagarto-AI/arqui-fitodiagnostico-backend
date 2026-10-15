package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DetectorDeCambioDeEstadoTest {

    private final DetectorDeCambioDeEstado detector = new DetectorDeCambioDeEstado();

    @Test
    void cadaParDeEstadosDistintosEsUnCambio() {
        for (EstadoGlobal antes : EstadoGlobal.values()) {
            for (EstadoGlobal despues : EstadoGlobal.values()) {
                assertThat(detector.huboCambio(Muestras.diagnosticoCon(antes), Muestras.diagnosticoCon(despues)))
                        .as(antes + " -> " + despues)
                        .isEqualTo(antes != despues);
            }
        }
    }

    @Test
    void elPrimerDiagnosticoNoCuentaComoCambioNiSiNaceCritico() {
        // Decisión documentada (D40): sin anterior no hay contra qué cambiar.
        for (EstadoGlobal estado : EstadoGlobal.values()) {
            assertThat(detector.huboCambio(null, Muestras.diagnosticoCon(estado))).as(estado.name()).isFalse();
        }
    }

    @Test
    void soloMiraElEstadoGlobalNoLosValores() {
        // Mismo estado con otra medición: no es un cambio de estado.
        var anterior = Muestras.diagnosticoCon(EstadoGlobal.SALUDABLE);
        var nuevo = new com.vivero.fitodiagnostico.dominio.modelo.Diagnostico(anterior.especie(),
                Muestras.medicion(31, 510, 19), EstadoGlobal.SALUDABLE, java.util.List.of(), java.util.List.of(),
                java.time.Instant.now());

        assertThat(detector.huboCambio(anterior, nuevo)).isFalse();
    }

    @Test
    void exigeElDiagnosticoNuevo() {
        assertThatThrownBy(() -> detector.huboCambio(Muestras.diagnosticoCon(EstadoGlobal.SALUDABLE), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
