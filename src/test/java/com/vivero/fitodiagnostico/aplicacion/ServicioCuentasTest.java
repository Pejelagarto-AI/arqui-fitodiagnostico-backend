package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.IniciarSesionPort;
import com.vivero.fitodiagnostico.dobles.CifradorFalso;
import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dobles.MundoEnMemoria;
import com.vivero.fitodiagnostico.dobles.UsuariosEnMemoria;
import com.vivero.fitodiagnostico.dominio.excepcion.ContrasenaActualIncorrectaException;
import com.vivero.fitodiagnostico.dominio.excepcion.CorreoYaRegistradoException;
import com.vivero.fitodiagnostico.dominio.excepcion.CredencialesInvalidasException;
import com.vivero.fitodiagnostico.dominio.excepcion.UltimoAdministradorException;
import com.vivero.fitodiagnostico.dominio.excepcion.UsuarioNoEncontradoException;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.modelo.TokenDeSesion;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAdministradores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prueba de aplicación (RA4/RA5): {@link ServicioCuentas} con dobles en
 * memoria de todos sus puertos y la política de dominio real. Sin Spring, sin
 * BCrypt, sin JWT y sin base.
 */
class ServicioCuentasTest {

    private UsuariosEnMemoria usuarios;
    private MundoEnMemoria mundo;
    private ServicioCuentas servicio;

    @BeforeEach
    void preparar() {
        usuarios = new UsuariosEnMemoria();
        mundo = new MundoEnMemoria();
        servicio = new ServicioCuentas(usuarios, new CifradorFalso(), new PoliticaDeAdministradores(),
                usuario -> new TokenDeSesion("token-de-" + usuario.correo(), Instant.parse("2030-01-01T00:00:00Z")),
                mundo.cascada);
    }

    @Test
    void registrarGuardaUnClienteConLaContrasenaCifrada() {
        Usuario ana = servicio.registrar("Ana", "Ana@Vivero.co", "clave-larga-1");

        Usuario guardado = usuarios.buscarPorId(ana.id()).orElseThrow();
        assertThat(guardado.correo()).isEqualTo("ana@vivero.co");
        assertThat(guardado.esAdministrador()).isFalse();
        assertThat(guardado.credencial().hash()).isEqualTo("hash:clave-larga-1").isNotEqualTo("clave-larga-1");
    }

    @Test
    void registrarConUnCorreoRepetidoLanzaSinDistinguirMayusculas() {
        servicio.registrar("Ana", "ana@vivero.co", "clave-larga-1");

        assertThatThrownBy(() -> servicio.registrar("Otra Ana", "ANA@vivero.co", "clave-larga-2"))
                .isInstanceOf(CorreoYaRegistradoException.class);
    }

    @Test
    void iniciarSesionConCredencialesCorrectasDevuelveUsuarioYToken() {
        servicio.registrar("Ana", "ana@vivero.co", "clave-larga-1");

        IniciarSesionPort.Sesion sesion = servicio.iniciarSesion("ANA@vivero.co", "clave-larga-1");

        assertThat(sesion.usuario().correo()).isEqualTo("ana@vivero.co");
        assertThat(sesion.token().valor()).isEqualTo("token-de-ana@vivero.co");
    }

    @Test
    void contrasenaIncorrectaCorreoInexistenteYCorreoMalFormadoDanElMismoError() {
        servicio.registrar("Ana", "ana@vivero.co", "clave-larga-1");

        assertThatThrownBy(() -> servicio.iniciarSesion("ana@vivero.co", "otra"))
                .isInstanceOf(CredencialesInvalidasException.class);
        assertThatThrownBy(() -> servicio.iniciarSesion("nadie@vivero.co", "clave-larga-1"))
                .isInstanceOf(CredencialesInvalidasException.class);
        assertThatThrownBy(() -> servicio.iniciarSesion("no-es-correo", "clave-larga-1"))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void actualizarDatosCambiaNombreYCorreo() {
        Usuario ana = servicio.registrar("Ana", "ana@vivero.co", "clave-larga-1");

        Usuario editada = servicio.actualizarDatos(ana.id(), "Ana M.", "ana.m@vivero.co");

        assertThat(servicio.consultar(ana.id()).nombre()).isEqualTo("Ana M.");
        assertThat(editada.correo()).isEqualTo("ana.m@vivero.co");
    }

    @Test
    void actualizarDatosConElCorreoDeOtroUsuarioLanza() {
        Usuario ana = servicio.registrar("Ana", "ana@vivero.co", "clave-larga-1");
        servicio.registrar("Beto", "beto@vivero.co", "clave-larga-2");

        assertThatThrownBy(() -> servicio.actualizarDatos(ana.id(), "Ana", "beto@vivero.co"))
                .isInstanceOf(CorreoYaRegistradoException.class);
    }

    @Test
    void actualizarDatosConMiPropioCorreoNoEsConflicto() {
        Usuario ana = servicio.registrar("Ana", "ana@vivero.co", "clave-larga-1");

        assertThat(servicio.actualizarDatos(ana.id(), "Ana Nueva", "ANA@vivero.co").nombre())
                .isEqualTo("Ana Nueva");
    }

    @Test
    void consultarUnUsuarioInexistenteLanza() {
        assertThatThrownBy(() -> servicio.consultar(UUID.randomUUID()))
                .isInstanceOf(UsuarioNoEncontradoException.class);
    }

    @Test
    void cambiarContrasenaExigeLaActualYGuardaLaNuevaCifrada() {
        Usuario ana = servicio.registrar("Ana", "ana@vivero.co", "clave-larga-1");

        servicio.cambiarContrasena(ana.id(), "clave-larga-1", "clave-nueva-2");

        assertThat(servicio.iniciarSesion("ana@vivero.co", "clave-nueva-2").usuario().id()).isEqualTo(ana.id());
        assertThatThrownBy(() -> servicio.iniciarSesion("ana@vivero.co", "clave-larga-1"))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void cambiarContrasenaConLaActualIncorrectaLanzaYNoCambiaNada() {
        Usuario ana = servicio.registrar("Ana", "ana@vivero.co", "clave-larga-1");

        assertThatThrownBy(() -> servicio.cambiarContrasena(ana.id(), "equivocada", "clave-nueva-2"))
                .isInstanceOf(ContrasenaActualIncorrectaException.class);
        assertThat(servicio.iniciarSesion("ana@vivero.co", "clave-larga-1")).isNotNull();
    }

    @Test
    void eliminarLaPropiaCuentaDeUnClienteLaBorra() {
        Usuario ana = servicio.registrar("Ana", "ana@vivero.co", "clave-larga-1");

        servicio.eliminar(ana.id());

        assertThat(usuarios.buscarPorId(ana.id())).isEmpty();
    }

    @Test
    void elUnicoAdministradorNoPuedeEliminarSuPropiaCuenta() {
        servicio.asegurarAdministradorInicial("admin@vivero.co", "clave-admin-1");
        Usuario admin = usuarios.buscarPorCorreo("admin@vivero.co").orElseThrow();

        assertThatThrownBy(() -> servicio.eliminar(admin.id())).isInstanceOf(UltimoAdministradorException.class);
        assertThat(usuarios.buscarPorId(admin.id())).isPresent();
    }

    @Test
    void unAdministradorConOtroAdministradorSiPuedeEliminarSuCuenta() {
        servicio.asegurarAdministradorInicial("admin@vivero.co", "clave-admin-1");
        Usuario admin = usuarios.buscarPorCorreo("admin@vivero.co").orElseThrow();
        Usuario ana = servicio.registrar("Ana", "ana@vivero.co", "clave-larga-1");
        usuarios.guardar(ana.conRolAdministrador());

        servicio.eliminar(admin.id());

        assertThat(usuarios.contarAdministradores()).isEqualTo(1);
    }

    @Test
    void elPrimerAdministradorSeCreaConRolesClienteYAdministrador() {
        boolean creado = servicio.asegurarAdministradorInicial("Admin@Vivero.co", "clave-admin-1");

        Usuario admin = usuarios.buscarPorCorreo("admin@vivero.co").orElseThrow();
        assertThat(creado).isTrue();
        assertThat(admin.esAdministrador()).isTrue();
        assertThat(admin.credencial().hash()).isEqualTo("hash:clave-admin-1");
    }

    @Test
    void sembrarElPrimerAdministradorEsIdempotente() {
        servicio.asegurarAdministradorInicial("admin@vivero.co", "clave-admin-1");

        boolean otraVez = servicio.asegurarAdministradorInicial("otro@vivero.co", "clave-admin-2");

        assertThat(otraVez).isFalse();
        assertThat(usuarios.listar()).hasSize(1);
    }

    @Test
    void sembrarAscendeAUnClienteExistenteConEseCorreoSinCambiarSuContrasena() {
        Usuario ana = servicio.registrar("Ana", "ana@vivero.co", "clave-larga-1");

        servicio.asegurarAdministradorInicial("ana@vivero.co", "otra-clave-9");

        Usuario ascendida = usuarios.buscarPorId(ana.id()).orElseThrow();
        assertThat(ascendida.esAdministrador()).isTrue();
        assertThat(ascendida.credencial().hash()).isEqualTo("hash:clave-larga-1");
    }

    @Test
    void sinAdministradorNiCredencialesDeArranqueSePrefiereNoArrancar() {
        assertThatThrownBy(() -> servicio.asegurarAdministradorInicial("", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FITO_ADMIN_CORREO");
        assertThatThrownBy(() -> servicio.asegurarAdministradorInicial(null, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void conUnAdministradorExistenteLasCredencialesDeArranqueNoHacenFalta() {
        servicio.asegurarAdministradorInicial("admin@vivero.co", "clave-admin-1");

        assertThat(servicio.asegurarAdministradorInicial("", "")).isFalse();
    }

    // ---- cascada de plantas (RN12, D11) ----

    @Test
    void eliminarLaCuentaEliminaSusPlantasConHistorialYAccesosYAvisaALosInvitados() {
        Usuario ana = servicio.registrar("Ana", "ana@vivero.co", "clave-larga-1");
        Usuario beto = servicio.registrar("Beto", "beto@vivero.co", "clave-larga-1");
        Planta propia = Planta.nueva(ana.id(), "Pepa", Muestras.especie("helecho"));
        Planta ajena = Planta.nueva(beto.id(), "Tuya", Muestras.especie("helecho"));
        mundo.plantas.guardar(propia);
        mundo.plantas.guardar(ajena);
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(propia.id(), beto.id()));   // Beto es invitado de Ana
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(ajena.id(), ana.id()));     // Ana es invitada de Beto

        servicio.eliminar(ana.id());

        assertThat(mundo.plantas.buscar(propia.id())).isEmpty();
        assertThat(mundo.plantas.buscar(ajena.id())).isPresent();   // la planta de Beto sigue
        assertThat(mundo.accesos.invitadosDe(propia.id())).isEmpty();
        assertThat(mundo.accesos.invitadosDe(ajena.id())).isEmpty();   // Ana ya no es invitada en ninguna parte
        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.ACCESO_PERDIDO)).singleElement()
                .satisfies(aviso -> assertThat(aviso.destinatarios()).containsExactly(beto.id()));
        assertThat(usuarios.buscarPorId(ana.id())).isEmpty();
    }

    @Test
    void siLaRN4ImpideEliminarNoSeToqueNiUnaPlanta() {
        servicio.asegurarAdministradorInicial("admin@vivero.co", "clave-larga-1");
        Usuario admin = usuarios.buscarPorCorreo("admin@vivero.co").orElseThrow();
        Planta planta = Planta.nueva(admin.id(), "Pepa", Muestras.especie("helecho"));
        mundo.plantas.guardar(planta);

        assertThatThrownBy(() -> servicio.eliminar(admin.id())).isInstanceOf(UltimoAdministradorException.class);

        assertThat(mundo.plantas.buscar(planta.id())).isPresent();
    }
}
