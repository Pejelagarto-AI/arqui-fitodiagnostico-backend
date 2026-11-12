package com.vivero.fitodiagnostico.adaptador.entrada.arranque;

import com.vivero.fitodiagnostico.aplicacion.ServicioCuentas;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class SembradorDeAdministradorTest {

    @Test
    void entregaLasCredencialesDelEntornoAlCasoDeUso() {
        ServicioCuentas cuentas = mock(ServicioCuentas.class);

        new SembradorDeAdministrador(cuentas, "admin@x.co", "contrasena-larga").run(null);

        verify(cuentas).asegurarAdministradorInicial("admin@x.co", "contrasena-larga");
    }

    @Test
    void unaContrasenaDelEntornoMasCortaQueElMinimoImpideArrancar() {
        ServicioCuentas cuentas = mock(ServicioCuentas.class);

        assertThatThrownBy(() -> new SembradorDeAdministrador(cuentas, "admin@x.co", "corta").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FITO_ADMIN_CONTRASENA");
        verifyNoInteractions(cuentas);
    }

    @Test
    void sinVariablesDelegaIgualAlCasoDeUsoQueDecideSiHaceFalta() {
        ServicioCuentas cuentas = mock(ServicioCuentas.class);

        new SembradorDeAdministrador(cuentas, "", "").run(null);

        verify(cuentas).asegurarAdministradorInicial("", "");
    }
}
