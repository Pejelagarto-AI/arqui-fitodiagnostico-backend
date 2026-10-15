package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dobles.CifradorFalso;
import com.vivero.fitodiagnostico.dobles.GeneradorDeTokensFijo;
import com.vivero.fitodiagnostico.dobles.MundoEnMemoria;
import com.vivero.fitodiagnostico.dobles.RelojManual;
import com.vivero.fitodiagnostico.dobles.UsuariosEnMemoria;
import com.vivero.fitodiagnostico.dominio.excepcion.RestablecimientoNoVigenteException;
import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.SolicitudRestablecimiento;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.modelo.TokenDeSesion;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAdministradores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link ServicioRecuperacion}: no enumerar, un solo uso, caducar e invalidarse si la contraseña cambia (RN14). */
class ServicioRecuperacionTest {

    private MundoEnMemoria mundo;
    private UsuariosEnMemoria usuarios;
    private GeneradorDeTokensFijo tokens;
    private RelojManual reloj;
    private ServicioCuentas cuentas;
    private ServicioRecuperacion servicio;
    private Usuario ana;

    @BeforeEach
    void preparar() {
        mundo = new MundoEnMemoria();
        usuarios = new UsuariosEnMemoria();
        tokens = new GeneradorDeTokensFijo();
        reloj = RelojManual.enUnDiaCualquiera();
        cuentas = new ServicioCuentas(usuarios, new CifradorFalso(), new PoliticaDeAdministradores(),
                usuario -> new TokenDeSesion("t", Instant.parse("2030-01-01T00:00:00Z")), mundo.cascada);
        servicio = new ServicioRecuperacion(usuarios, mundo.restablecimientos, new CifradorFalso(), tokens,
                mundo.notificador, reloj);
        ana = cuentas.registrar("Ana", "ana@vivero.co", "clave-vieja-1");
    }

    // ---- RF-A7 ----

    @Test
    void solicitarParaUnCorreoExistenteGuardaLaSolicitudYAvisaConElTokenEnLosDatos() {
        servicio.solicitar("  ANA@vivero.co ");

        assertThat(mundo.restablecimientos.todas()).singleElement().satisfies((SolicitudRestablecimiento s) -> {
            assertThat(s.usuarioId()).isEqualTo(ana.id());
            assertThat(s.huellaDelToken()).isEqualTo("huella:token-1");
            assertThat(s.expiraEn()).isEqualTo(reloj.instant().plus(SolicitudRestablecimiento.VIGENCIA));
            assertThat(s.usado()).isFalse();
        });
        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.RESTABLECIMIENTO)).singleElement().satisfies(aviso -> {
            assertThat(aviso.destinatarios()).containsExactly(ana.id());
            assertThat(aviso.datos()).containsEntry(Aviso.TOKEN, "token-1");
            assertThat(aviso.mensaje()).doesNotContain("token-1");
        });
    }

    @Test
    void solicitarParaUnCorreoInexistenteONoValidoNoHaceNadaVisibleNiLanza() {
        servicio.solicitar("nadie@vivero.co");
        servicio.solicitar("esto-no-es-un-correo");
        servicio.solicitar(null);

        assertThat(mundo.restablecimientos.cuantas()).isZero();
        assertThat(mundo.notificador.avisos()).isEmpty();
    }

    // ---- RF-A8 ----

    @Test
    void restablecerCambiaLaContrasenaYConsumeElEnlace() {
        servicio.solicitar("ana@vivero.co");

        servicio.restablecer("token-1", "clave-nueva-1");

        assertThat(cuentas.iniciarSesion("ana@vivero.co", "clave-nueva-1").usuario().id()).isEqualTo(ana.id());
        assertThatThrownBy(() -> cuentas.iniciarSesion("ana@vivero.co", "clave-vieja-1"))
                .isInstanceOf(com.vivero.fitodiagnostico.dominio.excepcion.CredencialesInvalidasException.class);
        assertThat(mundo.restablecimientos.todas().get(0).usado()).isTrue();
    }

    @Test
    void elEnlaceEsDeUnSoloUso() {
        servicio.solicitar("ana@vivero.co");
        servicio.restablecer("token-1", "clave-nueva-1");

        assertThatThrownBy(() -> servicio.restablecer("token-1", "otra-clave-nueva-2"))
                .isInstanceOf(RestablecimientoNoVigenteException.class);
        assertThat(cuentas.iniciarSesion("ana@vivero.co", "clave-nueva-1")).isNotNull();
    }

    @Test
    void elEnlaceCaducaALaHora() {
        servicio.solicitar("ana@vivero.co");
        reloj.avanzar(Duration.ofMinutes(59));
        // todavía sirve a los 59 minutos: se comprueba con una solicitud aparte para no consumirla
        servicio.solicitar("ana@vivero.co");
        reloj.avanzar(Duration.ofMinutes(2));

        assertThatThrownBy(() -> servicio.restablecer("token-1", "clave-nueva-1"))
                .isInstanceOf(RestablecimientoNoVigenteException.class);
        servicio.restablecer("token-2", "clave-nueva-1");   // la segunda, pedida a los 59, aún está vigente
    }

    @Test
    void siLaContrasenaCambioPorOtraViaElEnlaceSeInvalida() {
        servicio.solicitar("ana@vivero.co");
        cuentas.cambiarContrasena(ana.id(), "clave-vieja-1", "clave-por-otra-via-1");   // RF-A5

        assertThatThrownBy(() -> servicio.restablecer("token-1", "clave-del-atacante-1"))
                .isInstanceOf(RestablecimientoNoVigenteException.class);
        assertThat(cuentas.iniciarSesion("ana@vivero.co", "clave-por-otra-via-1")).isNotNull();
    }

    @Test
    void unTokenQueNoExisteONuloOEnBlancoDaElMismoError() {
        assertThatThrownBy(() -> servicio.restablecer("inventado", "clave-nueva-1"))
                .isInstanceOf(RestablecimientoNoVigenteException.class);
        assertThatThrownBy(() -> servicio.restablecer(null, "clave-nueva-1"))
                .isInstanceOf(RestablecimientoNoVigenteException.class);
        assertThatThrownBy(() -> servicio.restablecer("  ", "clave-nueva-1"))
                .isInstanceOf(RestablecimientoNoVigenteException.class);
    }

    @Test
    void siLaCuentaSeEliminoElEnlaceNoSirve() {
        servicio.solicitar("ana@vivero.co");
        cuentas.eliminar(ana.id());

        assertThatThrownBy(() -> servicio.restablecer("token-1", "clave-nueva-1"))
                .isInstanceOf(RestablecimientoNoVigenteException.class);
        assertThat(mundo.restablecimientos.cuantas()).as("la cascada borra las solicitudes de la cuenta").isZero();
    }
}
