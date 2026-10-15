package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La política es una función pura (D19): dos objetos en memoria, ningún puerto ni doble. */
class PoliticaDeAccesoTest {

    private final PoliticaDeAcceso politica = new PoliticaDeAcceso();
    private final UUID dueno = UUID.randomUUID();
    private final UUID invitado = UUID.randomUUID();
    private final UUID ajeno = UUID.randomUUID();
    private final Planta planta = new Planta(UUID.randomUUID(), dueno, "Pepa", Muestras.especie("helecho"), Instant.now());

    @Test
    void elDuenoLeeYModificaAunqueNoTengaInvitados() {
        assertThat(politica.puedeLeer(dueno, planta, Set.of())).isTrue();
        assertThat(politica.puedeModificar(dueno, planta)).isTrue();
    }

    @Test
    void elInvitadoSoloLee() {
        // RN5.
        assertThat(politica.puedeLeer(invitado, planta, Set.of(invitado))).isTrue();
        assertThat(politica.puedeModificar(invitado, planta)).isFalse();
    }

    @Test
    void unAjenoNoHaceNada() {
        // RN3.
        assertThat(politica.puedeLeer(ajeno, planta, Set.of(invitado))).isFalse();
        assertThat(politica.puedeModificar(ajeno, planta)).isFalse();
    }

    @Test
    void elDuenoLeeAunqueEstePresenteEnLosInvitados() {
        assertThat(politica.puedeLeer(dueno, planta, Set.of(dueno))).isTrue();
    }

    @Test
    void unConjuntoDeInvitadosNuloEquivaleASinInvitados() {
        assertThat(politica.puedeLeer(ajeno, planta, null)).isFalse();
        assertThat(politica.puedeLeer(dueno, planta, null)).isTrue();
    }

    @Test
    void exigeUsuarioYPlanta() {
        assertThatThrownBy(() -> politica.puedeLeer(null, planta, Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> politica.puedeLeer(dueno, null, Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> politica.puedeModificar(null, planta)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> politica.puedeModificar(dueno, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
