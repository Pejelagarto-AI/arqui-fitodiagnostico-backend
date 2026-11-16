package com.vivero.fitodiagnostico;

import com.vivero.fitodiagnostico.adaptador.entrada.programado.RetencionDeLecturas;
import com.vivero.fitodiagnostico.historial.PodarHistorialPort;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** La tarea diaria de retención (D176): existe por defecto y el perfil de pruebas la apaga; el caso de uso siempre está. */
class RetencionDelHistorialConfiguracionTest {

    @SpringBootTest
    @ActiveProfiles("test")
    @Nested
    class ApagadaEnPruebas {
        @Autowired private ApplicationContext contexto;

        @Test
        void sinTareaPeroConElCasoDeUso() {
            assertThat(contexto.getBeanNamesForType(RetencionDeLecturas.class)).isEmpty();
            assertThat(contexto.getBeanNamesForType(PodarHistorialPort.class)).hasSize(1);
        }
    }

    @SpringBootTest(properties = "fitodiagnostico.historial.retencion.habilitada=true")
    @ActiveProfiles("test")
    @Nested
    class EncendidaPorConfiguracion {
        @Autowired private ApplicationContext contexto;

        @Test
        void conLaPropiedadEncendidaLaTareaExiste() {
            assertThat(contexto.getBeanNamesForType(RetencionDeLecturas.class)).hasSize(1);
        }
    }
}
