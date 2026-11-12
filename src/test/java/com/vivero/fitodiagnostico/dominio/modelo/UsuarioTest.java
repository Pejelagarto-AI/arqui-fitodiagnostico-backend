package com.vivero.fitodiagnostico.dominio.modelo;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JUnit puro: el modelo de usuario, sin Spring, sin cifrado y sin base. */
class UsuarioTest {

    private static final Credencial HASH = new Credencial("hash-de-prueba");

    private static Usuario ana() {
        return Usuario.nuevo("Ana", "ana@vivero.co", HASH);
    }

    @Test
    void unUsuarioNuevoEsSoloCliente() {
        Usuario ana = ana();

        assertThat(ana.roles()).containsExactly(Rol.CLIENTE);
        assertThat(ana.esAdministrador()).isFalse();
    }

    @Test
    void clienteEstaSiempreAunqueSePidaOtroConjunto() {
        // RN2: ADMINISTRADOR suma, no sustituye.
        Usuario soloAdmin = new Usuario(UUID.randomUUID(), "Ana", "ana@vivero.co", HASH,
                EnumSet.of(Rol.ADMINISTRADOR));

        assertThat(soloAdmin.roles()).containsExactlyInAnyOrder(Rol.CLIENTE, Rol.ADMINISTRADOR);
    }

    @Test
    void clienteEstaAunqueLosRolesVengaNulos() {
        Usuario sinRoles = new Usuario(UUID.randomUUID(), "Ana", "ana@vivero.co", HASH, null);

        assertThat(sinRoles.roles()).containsExactly(Rol.CLIENTE);
    }

    @Test
    void otorgarElRolConservaClienteYEsIdempotente() {
        Usuario admin = ana().conRolAdministrador().conRolAdministrador();

        assertThat(admin.esAdministrador()).isTrue();
        assertThat(admin.roles()).containsExactlyInAnyOrder(Rol.CLIENTE, Rol.ADMINISTRADOR);
    }

    @Test
    void revocarElRolDejaSoloClienteYNuncaQuitaCliente() {
        Usuario revocado = ana().conRolAdministrador().sinRolAdministrador();

        assertThat(revocado.roles()).containsExactly(Rol.CLIENTE);
        assertThat(revocado.sinRolAdministrador().roles()).containsExactly(Rol.CLIENTE);
    }

    @Test
    void lasOperacionesDevuelvenCopiasYNoMutanLaOriginal() {
        Usuario original = ana();

        original.conRolAdministrador();
        original.conDatos("Otra", "otra@vivero.co");

        assertThat(original.esAdministrador()).isFalse();
        assertThat(original.nombre()).isEqualTo("Ana");
    }

    @Test
    void conDatosCambiaNombreYCorreoPeroConservaIdentidadRolesYCredencial() {
        Usuario admin = ana().conRolAdministrador();

        Usuario editado = admin.conDatos("Ana María", "ana.maria@vivero.co");

        assertThat(editado.id()).isEqualTo(admin.id());
        assertThat(editado.nombre()).isEqualTo("Ana María");
        assertThat(editado.correo()).isEqualTo("ana.maria@vivero.co");
        assertThat(editado.roles()).isEqualTo(admin.roles());
        assertThat(editado.credencial()).isEqualTo(admin.credencial());
    }

    @Test
    void conCredencialReemplazaSoloLaCredencial() {
        Credencial nueva = new Credencial("otro-hash");

        Usuario cambiado = ana().conCredencial(nueva);

        assertThat(cambiado.credencial()).isEqualTo(nueva);
        assertThat(cambiado.correo()).isEqualTo("ana@vivero.co");
    }

    @Test
    void elCorreoSeNormalizaParaQueRN1ComparePorLaMismaForma() {
        // RN1: "Ana@Vivero.CO " y "ana@vivero.co" son el mismo correo.
        assertThat(Usuario.nuevo("Ana", "  Ana@Vivero.CO ", HASH).correo()).isEqualTo("ana@vivero.co");
        assertThat(Usuario.normalizarCorreo("ANA@VIVERO.CO")).isEqualTo("ana@vivero.co");
    }

    @Test
    void elNombreSeRecortaYNoPuedeEstarEnBlanco() {
        assertThat(Usuario.nuevo("  Ana  ", "ana@vivero.co", HASH).nombre()).isEqualTo("Ana");
        assertThatThrownBy(() -> Usuario.nuevo("  ", "ana@vivero.co", HASH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Usuario.nuevo(null, "ana@vivero.co", HASH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void correosSinFormaDeDireccionSeRechazan() {
        for (String malo : new String[] {null, "", "  ", "sin-arroba", "@vivero.co", "ana@", "a@@b.co", "a b@vivero.co"}) {
            assertThatThrownBy(() -> Usuario.nuevo("Ana", malo, HASH))
                    .as("correo %s", malo)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void sinCredencialOSinIdNoHayUsuario() {
        assertThatThrownBy(() -> new Usuario(UUID.randomUUID(), "Ana", "ana@vivero.co", null, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Usuario(null, "Ana", "ana@vivero.co", HASH, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void elConjuntoDeRolesEsInmutableDesdeFuera() {
        assertThatThrownBy(() -> ana().roles().add(Rol.ADMINISTRADOR))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void elHashNuncaAparecePorToString() {
        // RN10: un usuario que termine en un log no debe filtrar su hash.
        assertThat(ana().toString()).doesNotContain("hash-de-prueba");
    }
}
