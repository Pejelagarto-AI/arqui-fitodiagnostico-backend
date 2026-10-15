package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieInactivaException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlantaTest {

    private final UUID dueno = UUID.randomUUID();

    @Test
    void unaPlantaNuevaGuardaDuenoApodoEspecieYFecha() {
        Instant antes = Instant.now();

        Planta planta = Planta.nueva(dueno, "  Pepa  ", Muestras.especie("helecho"));

        assertThat(planta.id()).isNotNull();
        assertThat(planta.duenoId()).isEqualTo(dueno);
        assertThat(planta.apodo()).isEqualTo("Pepa");   // RF-D1: sin espacios en los extremos
        assertThat(planta.especie().nombre()).isEqualTo("helecho");
        assertThat(planta.registradaEn()).isBetween(antes, Instant.now());
        assertThat(planta.esDe(dueno)).isTrue();
        assertThat(planta.esDe(UUID.randomUUID())).isFalse();
    }

    @Test
    void noSeRegistraConUnaEspecieDescontinuada() {
        assertThatThrownBy(() -> Planta.nueva(dueno, "Pepa", Muestras.especieInactiva("potos")))
                .isInstanceOf(EspecieInactivaException.class)
                .hasMessageContaining("potos");
    }

    @Test
    void elApodoEsObligatorioYTieneLargoMaximo() {
        assertThatThrownBy(() -> Planta.nueva(dueno, null, Muestras.especie("helecho")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Planta.nueva(dueno, "   ", Muestras.especie("helecho")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Planta.nueva(dueno, "x".repeat(Planta.LARGO_MAXIMO_DEL_APODO + 1),
                Muestras.especie("helecho"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(Planta.nueva(dueno, "x".repeat(Planta.LARGO_MAXIMO_DEL_APODO), Muestras.especie("helecho")))
                .isNotNull();
    }

    @Test
    void sinDuenoNiEspecieNoExiste() {
        assertThatThrownBy(() -> Planta.nueva(null, "Pepa", Muestras.especie("helecho")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Planta.nueva(dueno, "Pepa", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cambiarElApodoConservaTodoLoDemasIncluidaUnaEspecieDescontinuada() {
        // RN11: una planta cuya especie se desactivó después sigue pudiendo editarse.
        Planta existente = new Planta(UUID.randomUUID(), dueno, "Pepa", Muestras.especieInactiva("potos"), Instant.now());

        Planta editada = existente.conApodo("Pepita");

        assertThat(editada.apodo()).isEqualTo("Pepita");
        assertThat(editada.id()).isEqualTo(existente.id());
        assertThat(editada.especie()).isEqualTo(existente.especie());
        assertThat(editada.registradaEn()).isEqualTo(existente.registradaEn());
    }

    @Test
    void cambiarAOtraEspecieExigeQueEsteActiva() {
        Planta planta = Planta.nueva(dueno, "Pepa", Muestras.especie("helecho"));

        assertThat(planta.conEspecie(Muestras.especie("potos")).especie().nombre()).isEqualTo("potos");
        assertThatThrownBy(() -> planta.conEspecie(Muestras.especieInactiva("lavanda")))
                .isInstanceOf(EspecieInactivaException.class);
    }

    @Test
    void ponerLaMismaEspecieDescontinuadaNoEsUnCambioYNoFalla() {
        Planta planta = new Planta(UUID.randomUUID(), dueno, "Pepa", Muestras.especieInactiva("potos"), Instant.now());

        assertThat(planta.conEspecie(Muestras.especieInactiva("POTOS")).especie().nombre()).isEqualTo("POTOS");
    }

    @Test
    void reconstruirUnaPlantaConEspecieDescontinuadaEsValido() {
        // El constructor no aplica RN11: lo usa el adaptador para leer de la base.
        Planta planta = new Planta(UUID.randomUUID(), dueno, "Pepa", Muestras.especieInactiva("potos"), Instant.now());

        assertThat(planta.especie().activa()).isFalse();
    }
}
