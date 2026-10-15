package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dobles.Muestras;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiagnosticoDePlantaTest {

    private final UUID planta = UUID.randomUUID();

    @Test
    void envuelveAlDiagnosticoSinModificarloYGuardaOrigenYFecha() {
        Diagnostico diagnostico = Muestras.diagnosticoCon(EstadoGlobal.EN_RIESGO);

        DiagnosticoDePlanta registro = DiagnosticoDePlanta.deUnaEvaluacion(planta, OrigenDeMedicion.MANUAL, diagnostico);

        assertThat(registro.diagnostico()).isSameAs(diagnostico);   // D5: no se copia ni se le agrega nada
        assertThat(registro.plantaId()).isEqualTo(planta);
        assertThat(registro.origen()).isEqualTo(OrigenDeMedicion.MANUAL);
        assertThat(registro.registradoEn()).isEqualTo(diagnostico.evaluadoEn());
        assertThat(registro.estado()).isEqualTo(EstadoGlobal.EN_RIESGO);
        assertThat(registro.id()).isNotNull();
    }

    @Test
    void cadaRegistroTieneSuPropioId() {
        Diagnostico diagnostico = Muestras.diagnosticoCon(EstadoGlobal.SALUDABLE);

        assertThat(DiagnosticoDePlanta.deUnaEvaluacion(planta, OrigenDeMedicion.MANUAL, diagnostico).id())
                .isNotEqualTo(DiagnosticoDePlanta.deUnaEvaluacion(planta, OrigenDeMedicion.MANUAL, diagnostico).id());
    }

    @Test
    void elOrigenNoVivePorFueraDelDiagnosticoSueltoCuyoModeloQuedaIntacto() {
        // D17: Diagnostico y Medicion no tienen campo de origen; solo el envoltorio lo tiene.
        assertThat(Diagnostico.class.getRecordComponents())
                .extracting(c -> c.getName()).doesNotContain("origen", "plantaId");
        assertThat(java.util.Arrays.stream(Medicion.class.getDeclaredFields()).map(f -> f.getName()))
                .doesNotContain("origen");
    }

    @Test
    void exigeTodosSusDatos() {
        Diagnostico diagnostico = Muestras.diagnosticoCon(EstadoGlobal.SALUDABLE);
        Instant ahora = Instant.now();

        assertThatThrownBy(() -> new DiagnosticoDePlanta(null, planta, OrigenDeMedicion.MANUAL, ahora, diagnostico))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiagnosticoDePlanta(UUID.randomUUID(), null, OrigenDeMedicion.MANUAL, ahora, diagnostico))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiagnosticoDePlanta(UUID.randomUUID(), planta, null, ahora, diagnostico))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiagnosticoDePlanta(UUID.randomUUID(), planta, OrigenDeMedicion.MANUAL, null, diagnostico))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DiagnosticoDePlanta(UUID.randomUUID(), planta, OrigenDeMedicion.MANUAL, ahora, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
