package com.vivero.fitodiagnostico.dominio.modelo;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    @Test
    void llevaDatosYCorreosInmutablesYElConstructorCortoLosDejaVacios() {
        Map<String, String> datos = new HashMap<>(Map.of(Aviso.TOKEN, "t"));
        Aviso aviso = Aviso.aCorreos(Set.of("ana@vivero.co"), TipoDeAviso.INVITACION, "te invitaron", datos);
        datos.put("otro", "x");

        assertThat(aviso.datos()).containsOnlyKeys(Aviso.TOKEN);
        assertThat(aviso.correos()).containsExactly("ana@vivero.co");
        assertThat(aviso.destinatarios()).isEmpty();
        Aviso corto = new Aviso(Set.of(UUID.randomUUID()), TipoDeAviso.CAMBIO_DE_ESTADO, "hola");
        assertThat(corto.datos()).isEmpty();
        assertThat(corto.correos()).isEmpty();
    }

    @Test
    void elToStringNuncaImprimeElTokenNiLosCorreos() {
        Aviso aviso = Aviso.aCorreos(Set.of("secreto@vivero.co"), TipoDeAviso.INVITACION, "te invitaron",
                Map.of(Aviso.TOKEN, "TOKEN-SECRETO-123"));

        assertThat(aviso.toString()).contains("INVITACION").contains("te invitaron")
                .doesNotContain("TOKEN-SECRETO-123").doesNotContain("secreto@vivero.co");
    }
}
