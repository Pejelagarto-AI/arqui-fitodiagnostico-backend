package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.AceptarInvitacionPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.CompartirPlantaPort;
import com.vivero.fitodiagnostico.dobles.CifradorFalso;
import com.vivero.fitodiagnostico.dobles.GeneradorDeTokensFijo;
import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dobles.MundoEnMemoria;
import com.vivero.fitodiagnostico.dobles.RelojManual;
import com.vivero.fitodiagnostico.dobles.UsuariosEnMemoria;
import com.vivero.fitodiagnostico.dominio.excepcion.CorreoYaRegistradoException;
import com.vivero.fitodiagnostico.dominio.excepcion.InvitacionNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.InvitacionNoVigenteException;
import com.vivero.fitodiagnostico.dominio.excepcion.InvitadoNoEncontradoException;
import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoInvitacion;
import com.vivero.fitodiagnostico.dominio.modelo.Invitacion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.modelo.TokenDeSesion;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAdministradores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ServicioInvitaciones} con dobles en memoria, tokens deterministas y un
 * reloj manual: quién puede compartir, un solo uso, caducidad, registrarse y
 * aceptar en una transacción, y que perder el acceso avise.
 */
class ServicioInvitacionesTest {

    private MundoEnMemoria mundo;
    private UsuariosEnMemoria usuarios;
    private GeneradorDeTokensFijo tokens;
    private RelojManual reloj;
    private ServicioCuentas cuentas;
    private ServicioInvitaciones servicio;

    private Usuario dueno;
    private Usuario beto;
    private Usuario ajeno;
    private Planta planta;

    @BeforeEach
    void preparar() {
        mundo = new MundoEnMemoria();
        usuarios = new UsuariosEnMemoria();
        tokens = new GeneradorDeTokensFijo();
        reloj = RelojManual.enUnDiaCualquiera();
        cuentas = new ServicioCuentas(usuarios, new CifradorFalso(), new PoliticaDeAdministradores(),
                usuario -> new TokenDeSesion("t", Instant.parse("2030-01-01T00:00:00Z")), mundo.cascada);
        servicio = new ServicioInvitaciones(mundo.plantas, mundo.accesos, mundo.invitaciones, usuarios,
                new PoliticaDeAcceso(), tokens, mundo.notificador, cuentas, reloj);

        dueno = cuentas.registrar("Dueña", "duena@vivero.co", "clave-larga-1");
        beto = cuentas.registrar("Beto", "beto@vivero.co", "clave-larga-1");
        ajeno = cuentas.registrar("Ajeno", "ajeno@vivero.co", "clave-larga-1");
        planta = Planta.nueva(dueno.id(), "Pepa", Muestras.especie("helecho"));
        mundo.plantas.guardar(planta);
    }

    // ---- generar (RF-E1) ----

    @Test
    void elDuenoGeneraUnaInvitacionPendienteYSoloSeGuardaLaHuellaDelToken() {
        CompartirPlantaPort.InvitacionGenerada generada = servicio.generar(dueno.id(), planta.id(), null);

        assertThat(generada.token()).isEqualTo("token-1");
        Invitacion guardada = mundo.invitaciones.todas().get(0);
        assertThat(guardada.huellaDelToken()).isEqualTo("huella:token-1").isNotEqualTo(generada.token());
        assertThat(guardada.estado()).isEqualTo(EstadoInvitacion.PENDIENTE);
        assertThat(guardada.plantaId()).isEqualTo(planta.id());
        assertThat(guardada.expiraEn()).isEqualTo(reloj.instant().plus(Invitacion.VIGENCIA));
        assertThat(mundo.notificador.avisos()).as("sin correo, el dueño reparte el enlace").isEmpty();
    }

    @Test
    void conCorreoSeAvisaUnaInvitacionALaDireccionConElTokenEnLosDatosYNoEnElTexto() {
        servicio.generar(dueno.id(), planta.id(), "  Amiga@Vivero.CO ");

        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.INVITACION)).singleElement().satisfies(aviso -> {
            assertThat(aviso.correos()).containsExactly("amiga@vivero.co");
            assertThat(aviso.destinatarios()).isEmpty();
            assertThat(aviso.datos()).containsEntry(Aviso.TOKEN, "token-1");
            assertThat(aviso.mensaje()).contains("Dueña").contains("Pepa").doesNotContain("token-1");
        });
    }

    @Test
    void elDuenoNoPuedeInvitarseASiMismoNiConSuPropioCorreo() {
        assertThatThrownBy(() -> servicio.generar(dueno.id(), planta.id(), "DUENA@vivero.co"))
                .isInstanceOf(OperacionNoPermitidaException.class).hasMessageContaining("ti mismo");

        assertThat(mundo.invitaciones.cuantas()).isZero();
        assertThat(mundo.notificador.avisos()).isEmpty();
    }

    @Test
    void unCorreoMalFormadoSeRechazaSinGuardarNada() {
        assertThatThrownBy(() -> servicio.generar(dueno.id(), planta.id(), "no-es-correo"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(mundo.invitaciones.cuantas()).isZero();
    }

    @Test
    void soloElDuenoGeneraUnInvitadoRecibe403YUnAjeno404() {
        mundo.accesos.otorgar(com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido.otorgadoAhora(planta.id(), beto.id()));

        assertThatThrownBy(() -> servicio.generar(beto.id(), planta.id(), null))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.generar(ajeno.id(), planta.id(), null))
                .isInstanceOf(PlantaNoEncontradaException.class);
        assertThat(mundo.invitaciones.cuantas()).isZero();
    }

    // ---- aceptar (RF-E2, RN13) ----

    @Test
    void aceptarOtorgaAccesoDeLecturaYConsumeLaInvitacion() {
        String token = servicio.generar(dueno.id(), planta.id(), null).token();

        Planta aceptada = servicio.aceptar(beto.id(), token);

        assertThat(aceptada.id()).isEqualTo(planta.id());
        assertThat(mundo.accesos.invitadosDe(planta.id())).containsExactly(beto.id());
        assertThat(mundo.invitaciones.todas().get(0).estado()).isEqualTo(EstadoInvitacion.ACEPTADA);
        assertThat(servicio.compartidasConmigo(beto.id())).extracting(Planta::id).containsExactly(planta.id());
    }

    @Test
    void aceptarDosVecesElMismoTokenFallaClaroYNoDuplicaElAcceso() {
        String token = servicio.generar(dueno.id(), planta.id(), null).token();
        servicio.aceptar(beto.id(), token);

        assertThatThrownBy(() -> servicio.aceptar(ajeno.id(), token))
                .isInstanceOf(InvitacionNoVigenteException.class).hasMessageContaining("ya fue aceptada");
        assertThat(mundo.accesos.invitadosDe(planta.id())).containsExactly(beto.id());
    }

    @Test
    void unTokenCaducadoFallaClaroYNoOtorgaNada() {
        String token = servicio.generar(dueno.id(), planta.id(), null).token();
        reloj.avanzar(Invitacion.VIGENCIA.plusSeconds(1));

        assertThatThrownBy(() -> servicio.aceptar(beto.id(), token))
                .isInstanceOf(InvitacionNoVigenteException.class).hasMessageContaining("caducó");
        assertThat(mundo.accesos.invitadosDe(planta.id())).isEmpty();
    }

    @Test
    void unTokenQueNoExisteEsNoEncontradoAunqueEsteEnBlancoONulo() {
        assertThatThrownBy(() -> servicio.aceptar(beto.id(), "token-inventado"))
                .isInstanceOf(InvitacionNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.aceptar(beto.id(), " ")).isInstanceOf(InvitacionNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.aceptar(beto.id(), null)).isInstanceOf(InvitacionNoEncontradaException.class);
    }

    @Test
    void elDuenoNoPuedeAceptarSuPropiaInvitacionYNoLaConsume() {
        String token = servicio.generar(dueno.id(), planta.id(), null).token();

        assertThatThrownBy(() -> servicio.aceptar(dueno.id(), token))
                .isInstanceOf(OperacionNoPermitidaException.class).hasMessageContaining("propia planta");

        assertThat(mundo.accesos.invitadosDe(planta.id())).isEmpty();
        assertThat(mundo.invitaciones.todas().get(0).estado()).as("sigue pendiente para quien sí debe usarla")
                .isEqualTo(EstadoInvitacion.PENDIENTE);
        servicio.aceptar(beto.id(), token);   // y alguien más sí puede
    }

    @Test
    void unaInvitacionRevocadaNoSePuedeAceptar() {
        CompartirPlantaPort.InvitacionGenerada generada = servicio.generar(dueno.id(), planta.id(), null);
        servicio.revocar(dueno.id(), planta.id(), generada.invitacion().id());

        assertThatThrownBy(() -> servicio.aceptar(beto.id(), generada.token()))
                .isInstanceOf(InvitacionNoVigenteException.class).hasMessageContaining("revocada");
    }

    // ---- registrarse y aceptar en el mismo flujo (RF-E2) ----

    @Test
    void elVisitanteSeRegistraYAceptaEnElMismoFlujo() {
        String token = servicio.generar(dueno.id(), planta.id(), null).token();

        AceptarInvitacionPort.AceptacionConRegistro resultado =
                servicio.registrarYAceptar(token, "Carla", "Carla@Vivero.co", "clave-larga-1");

        assertThat(resultado.usuario().correo()).isEqualTo("carla@vivero.co");
        assertThat(resultado.planta().id()).isEqualTo(planta.id());
        assertThat(usuarios.buscarPorCorreo("carla@vivero.co")).isPresent();
        assertThat(mundo.accesos.invitadosDe(planta.id())).containsExactly(resultado.usuario().id());
        assertThat(mundo.invitaciones.todas().get(0).estado()).isEqualTo(EstadoInvitacion.ACEPTADA);
    }

    @Test
    void conUnTokenInvalidoNoSeCreaLaCuenta() {
        assertThatThrownBy(() -> servicio.registrarYAceptar("token-inventado", "Carla", "carla@vivero.co", "clave-larga-1"))
                .isInstanceOf(InvitacionNoEncontradaException.class);

        assertThat(usuarios.buscarPorCorreo("carla@vivero.co")).isEmpty();
    }

    @Test
    void conUnTokenCaducadoNoSeCreaLaCuenta() {
        String token = servicio.generar(dueno.id(), planta.id(), null).token();
        reloj.avanzar(Duration.ofDays(8));

        assertThatThrownBy(() -> servicio.registrarYAceptar(token, "Carla", "carla@vivero.co", "clave-larga-1"))
                .isInstanceOf(InvitacionNoVigenteException.class);

        assertThat(usuarios.buscarPorCorreo("carla@vivero.co")).isEmpty();
    }

    @Test
    void siElCorreoYaTieneCuentaNoSeConsumeLaInvitacion() {
        String token = servicio.generar(dueno.id(), planta.id(), null).token();

        assertThatThrownBy(() -> servicio.registrarYAceptar(token, "Otro Beto", "beto@vivero.co", "clave-larga-1"))
                .isInstanceOf(CorreoYaRegistradoException.class);

        assertThat(mundo.invitaciones.todas().get(0).estado()).isEqualTo(EstadoInvitacion.PENDIENTE);
        assertThat(mundo.accesos.invitadosDe(planta.id())).isEmpty();
    }

    // ---- listar y revocar invitaciones (RF-E3) ----

    @Test
    void lasPendientesSoloListanLasVigentes() {
        servicio.generar(dueno.id(), planta.id(), null);                       // vigente
        CompartirPlantaPort.InvitacionGenerada revocada = servicio.generar(dueno.id(), planta.id(), null);
        servicio.revocar(dueno.id(), planta.id(), revocada.invitacion().id());
        String aceptada = servicio.generar(dueno.id(), planta.id(), null).token();
        servicio.aceptar(beto.id(), aceptada);
        reloj.avanzar(Duration.ofDays(6));
        servicio.generar(dueno.id(), planta.id(), null);                       // la última, recién creada
        reloj.avanzar(Duration.ofDays(2));                                     // la primera ya caducó

        List<Invitacion> pendientes = servicio.pendientes(dueno.id(), planta.id());

        assertThat(pendientes).hasSize(1);
        assertThat(pendientes.get(0).huellaDelToken()).isEqualTo("huella:token-4");
    }

    @Test
    void soloElDuenoListaYRevocaInvitaciones() {
        CompartirPlantaPort.InvitacionGenerada generada = servicio.generar(dueno.id(), planta.id(), null);

        assertThatThrownBy(() -> servicio.pendientes(ajeno.id(), planta.id())).isInstanceOf(PlantaNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.revocar(ajeno.id(), planta.id(), generada.invitacion().id()))
                .isInstanceOf(PlantaNoEncontradaException.class);
    }

    @Test
    void revocarUnaInvitacionDeOtraPlantaEsNoEncontrada() {
        Planta otra = Planta.nueva(dueno.id(), "Otra", Muestras.especie("helecho"));
        mundo.plantas.guardar(otra);
        CompartirPlantaPort.InvitacionGenerada deOtra = servicio.generar(dueno.id(), otra.id(), null);

        assertThatThrownBy(() -> servicio.revocar(dueno.id(), planta.id(), deOtra.invitacion().id()))
                .isInstanceOf(InvitacionNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.revocar(dueno.id(), planta.id(), UUID.randomUUID()))
                .isInstanceOf(InvitacionNoEncontradaException.class);
    }

    @Test
    void revocarUnaYaAceptadaFallaPorqueLoQueCorrespondeEsQuitarElAcceso() {
        CompartirPlantaPort.InvitacionGenerada generada = servicio.generar(dueno.id(), planta.id(), null);
        servicio.aceptar(beto.id(), generada.token());

        assertThatThrownBy(() -> servicio.revocar(dueno.id(), planta.id(), generada.invitacion().id()))
                .isInstanceOf(InvitacionNoVigenteException.class);
    }

    // ---- invitados (RF-E4, RF-E5, RF-E6, RF-G3) ----

    @Test
    void elDuenoVeQuienesTienenAccesoConSusDatos() {
        servicio.aceptar(beto.id(), servicio.generar(dueno.id(), planta.id(), null).token());

        assertThat(servicio.invitados(dueno.id(), planta.id())).singleElement().satisfies(invitado -> {
            assertThat(invitado.usuario().id()).isEqualTo(beto.id());
            assertThat(invitado.otorgadoEn()).isEqualTo(reloj.instant());
        });
        assertThatThrownBy(() -> servicio.invitados(beto.id(), planta.id())).isInstanceOf(OperacionNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.invitados(ajeno.id(), planta.id())).isInstanceOf(PlantaNoEncontradaException.class);
    }

    @Test
    void revocarElAccesoDeUnInvitadoLoQuitaYLeAvisaAccesoPerdido() {
        servicio.aceptar(beto.id(), servicio.generar(dueno.id(), planta.id(), null).token());

        servicio.revocarAcceso(dueno.id(), planta.id(), beto.id());

        assertThat(mundo.accesos.invitadosDe(planta.id())).isEmpty();
        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.ACCESO_PERDIDO)).singleElement().satisfies(aviso -> {
            assertThat(aviso.destinatarios()).containsExactly(beto.id());
            assertThat(aviso.mensaje()).contains("Pepa");
            assertThat(aviso.datos()).containsEntry(Aviso.PLANTA_ID, planta.id().toString());
        });
    }

    @Test
    void revocarAUnoQueNoEsInvitadoFallaYNoAvisa() {
        assertThatThrownBy(() -> servicio.revocarAcceso(dueno.id(), planta.id(), ajeno.id()))
                .isInstanceOf(InvitadoNoEncontradoException.class);
        assertThat(mundo.notificador.avisos()).isEmpty();
    }

    @Test
    void soloElDuenoRevocaAccesos() {
        servicio.aceptar(beto.id(), servicio.generar(dueno.id(), planta.id(), null).token());

        assertThatThrownBy(() -> servicio.revocarAcceso(beto.id(), planta.id(), beto.id()))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.revocarAcceso(ajeno.id(), planta.id(), beto.id()))
                .isInstanceOf(PlantaNoEncontradaException.class);
        assertThat(mundo.accesos.invitadosDe(planta.id())).containsExactly(beto.id());
    }

    @Test
    void renunciarQuitaElAccesoPeroNoAvisaPorqueFueDecisionDelInvitado() {
        servicio.aceptar(beto.id(), servicio.generar(dueno.id(), planta.id(), null).token());

        servicio.renunciar(beto.id(), planta.id());

        assertThat(mundo.accesos.invitadosDe(planta.id())).isEmpty();
        assertThat(servicio.compartidasConmigo(beto.id())).isEmpty();
        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.ACCESO_PERDIDO)).isEmpty();
    }

    @Test
    void renunciarAUnaPlantaQueNoMeCompartieronEsNoEncontrada() {
        assertThatThrownBy(() -> servicio.renunciar(ajeno.id(), planta.id())).isInstanceOf(PlantaNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.renunciar(dueno.id(), planta.id())).isInstanceOf(PlantaNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.renunciar(beto.id(), UUID.randomUUID())).isInstanceOf(PlantaNoEncontradaException.class);
    }

    @Test
    void despuesDeRevocarElInvitadoYaNoPuedeLeerYUnTokenNuevoLoVuelveAInvitar() {
        servicio.aceptar(beto.id(), servicio.generar(dueno.id(), planta.id(), null).token());
        servicio.revocarAcceso(dueno.id(), planta.id(), beto.id());
        assertThat(servicio.compartidasConmigo(beto.id())).isEmpty();

        servicio.aceptar(beto.id(), servicio.generar(dueno.id(), planta.id(), null).token());

        assertThat(servicio.compartidasConmigo(beto.id())).hasSize(1);
    }
}
