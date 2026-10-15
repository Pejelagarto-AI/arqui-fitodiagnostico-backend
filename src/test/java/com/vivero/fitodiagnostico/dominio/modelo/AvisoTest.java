package com.vivero.fitodiagnostico.dominio.modelo;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvisoTest {

    @Test
    void copiaLosDestinatariosYNoCambiaSiSeModificaElOriginal() {
        Set<UUID> original = new HashSet<>(Set.of(UUID.randomUUID()));

        Aviso aviso = new Aviso(original, TipoDeAviso.CAMBIO_DE_ESTADO, "hola");
        original.add(UUID.randomUUID());

        assertThat(aviso.destinatarios()).hasSize(1);
    }

    @Test
    void exigeTipoYMensajeYAceptaNingunDestinatario() {
        assertThatThrownBy(() -> new Aviso(Set.of(), null, "hola")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Aviso(Set.of(), TipoDeAviso.INVITACION, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Aviso(null, TipoDeAviso.INVITACION, "hola").destinatarios()).isEmpty();
    }

    @Test
    void elCatalogoDeTiposEstaCompletoDesdeYa() {
        assertThat(TipoDeAviso.values()).extracting(Enum::name).containsExactlyInAnyOrder(
                "CAMBIO_DE_ESTADO", "DIAGNOSTICO_NUEVO", "ACCESO_PERDIDO", "INVITACION",
                "RESTABLECIMIENTO", "SOLICITUD_RESUELTA");
    }
}
