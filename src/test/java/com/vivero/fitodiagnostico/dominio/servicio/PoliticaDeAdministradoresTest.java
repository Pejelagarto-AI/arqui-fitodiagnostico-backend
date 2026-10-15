package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Credencial;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RN4 / D18, con JUnit puro y dos objetos en memoria: la política no consulta
 * ningún puerto, así que no hace falta ningún doble.
 */
class PoliticaDeAdministradoresTest {

    private final PoliticaDeAdministradores politica = new PoliticaDeAdministradores();

    private static Usuario cliente() {
        return Usuario.nuevo("Cliente", "cliente@vivero.co", new Credencial("h"));
    }

    private static Usuario administrador() {
        return Usuario.nuevo("Admin", "admin@vivero.co", new Credencial("h")).conRolAdministrador();
    }

    @Test
    void elUnicoAdministradorNoPuedeDejarDeSerlo() {
        assertThat(politica.puedeDejarDeSerAdministrador(administrador(), 1)).isFalse();
    }

    @Test
    void conOtroAdministradorSiPuedeDejarDeSerlo() {
        assertThat(politica.puedeDejarDeSerAdministrador(administrador(), 2)).isTrue();
    }

    @Test
    void unClienteSiemprePuedeDejarDeSerloPorqueNoPierdeNada() {
        // Eliminar a un cliente nunca amenaza la RN4, aunque no quedara ningún administrador
        // (estado imposible en producción, pero la política no debe depender de ello).
        assertThat(politica.puedeDejarDeSerAdministrador(cliente(), 1)).isTrue();
        assertThat(politica.puedeDejarDeSerAdministrador(cliente(), 0)).isTrue();
    }

    @Test
    void conCeroAdministradoresElQueSeDiceAdministradorNoPuedeIrse() {
        // Estado inconsistente: el conteo dice 0 pero el usuario es admin. Ante la duda, no se permite.
        assertThat(politica.puedeDejarDeSerAdministrador(administrador(), 0)).isFalse();
    }
}
