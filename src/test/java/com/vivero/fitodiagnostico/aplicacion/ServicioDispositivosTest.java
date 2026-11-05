package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dobles.AutenticadorDeDispositivosFalso;
import com.vivero.fitodiagnostico.dobles.EmisorTokenDispositivoFijo;
import com.vivero.fitodiagnostico.dobles.GeneradorDeTokensFijo;
import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dobles.MundoEnMemoria;
import com.vivero.fitodiagnostico.dobles.RelojManual;
import com.vivero.fitodiagnostico.dominio.excepcion.CredencialRechazadaException;
import com.vivero.fitodiagnostico.dominio.excepcion.DispositivoNoEncontradoException;
import com.vivero.fitodiagnostico.dominio.excepcion.DispositivoSinReclamarException;
import com.vivero.fitodiagnostico.dominio.excepcion.DispositivoYaExisteException;
import com.vivero.fitodiagnostico.dominio.excepcion.DispositivoYaReclamadoException;
import com.vivero.fitodiagnostico.dominio.excepcion.FirmaDeFlotaInvalidaException;
import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeRegistro;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.Reto;
import com.vivero.fitodiagnostico.dominio.modelo.TokenDeDispositivo;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.RegistrarDispositivoDeFabricaPort.DispositivoDeFabrica;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los tres casos de uso de los dispositivos (fábrica, reclamo, autenticación) con dobles en memoria, sin JWT,
 * sin HMAC y sin Spring: lo que se prueba son las reglas, no la criptografía.
 */
class ServicioDispositivosTest {

    private final UUID admin = UUID.randomUUID();
    private final UUID dueno = UUID.randomUUID();
    private final UUID invitado = UUID.randomUUID();
    private final UUID ajeno = UUID.randomUUID();

    private MundoEnMemoria mundo;
    private EmisorTokenDispositivoFijo emisor;
    private RelojManual reloj;
    private ServicioDispositivos servicio;
    private Planta planta;

    @BeforeEach
    void preparar() {
        mundo = new MundoEnMemoria();
        emisor = new EmisorTokenDispositivoFijo();
        reloj = RelojManual.enUnDiaCualquiera();
        servicio = new ServicioDispositivos(mundo.plantas, mundo.accesos, new PoliticaDeAcceso(), mundo.dispositivos,
                emisor, new AutenticadorDeDispositivosFalso(), new GeneradorDeTokensFijo(), reloj);
        planta = Planta.nueva(dueno, "Pepa", Muestras.especie("helecho"));
        mundo.plantas.guardar(planta);
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), invitado));
    }

    private DispositivoDeFabrica deFabrica() {
        return servicio.registrar(admin, Optional.of("fito-AAAAAA"));
    }

    private String responder(DispositivoDeFabrica fabrica, Reto reto) {
        return AutenticadorDeDispositivosFalso.respuestaCorrecta(fabrica.secreto(), reto.valor());
    }

    // ---- fábrica ----

    @Test
    void registrarSinSerialGeneraUnoConFormatoSinCaracteresAmbiguosYUnSecreto() {
        DispositivoDeFabrica f = servicio.registrar(admin, Optional.empty());

        assertThat(f.serial()).matches(Pattern.compile("fito-[A-HJ-NP-Z2-9]{6}"));
        assertThat(f.secreto()).isNotBlank();
        assertThat(f.creadoEn()).isEqualTo(reloj.instant());
        assertThat(mundo.dispositivos.porSerial(f.serial())).isPresent();
    }

    @Test
    void registrarConSerialLoRespetaYUnoVacioSeTratacomoAusente() {
        assertThat(servicio.registrar(admin, Optional.of("esp32-01")).serial()).isEqualTo("esp32-01");
        assertThat(servicio.registrar(admin, Optional.of("  ")).serial()).startsWith("fito-");
    }

    @Test
    void unSerialTomadoEs409YNoSePisaElSecretoDelOriginal() {
        DispositivoDeFabrica original = deFabrica();

        assertThatThrownBy(() -> servicio.registrar(admin, Optional.of("fito-AAAAAA")))
                .isInstanceOf(DispositivoYaExisteException.class);
        assertThat(mundo.dispositivos.porSerial("fito-AAAAAA").orElseThrow().secreto()).isEqualTo(original.secreto());
    }

    @Test
    void unSerialMalFormadoSeRechazaSinGuardar() {
        assertThatThrownBy(() -> servicio.registrar(admin, Optional.of("con espacios/raros")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(mundo.dispositivos.cuantos()).isZero();
    }

    @Test
    void elAdministradorListaTodosReclamadosOSin() {
        deFabrica();
        servicio.registrar(admin, Optional.of("fito-BBBBBB"));
        servicio.reclamar(dueno, planta.id(), "fito-BBBBBB");

        assertThat(servicio.listar(admin)).extracting(Dispositivo::serial)
                .containsExactlyInAnyOrder("fito-AAAAAA", "fito-BBBBBB");
    }

    // ---- reclamo: solo el dueño ----

    @Test
    void elDuenoReclamaYElDispositivoQuedaEnSuPlanta() {
        deFabrica();

        Dispositivo d = servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");

        assertThat(d.plantaId()).contains(planta.id());
        assertThat(d.reclamadoEn()).contains(reloj.instant());
        assertThat(servicio.listar(dueno, planta.id())).extracting(Dispositivo::serial).containsExactly("fito-AAAAAA");
    }

    @Test
    void reclamarDeNuevoParaLaMismaPlantaEsIdempotente() {
        deFabrica();
        servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");
        reloj.avanzar(Duration.ofMinutes(5));

        Dispositivo d = servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");

        assertThat(d.reclamadoEn()).contains(reloj.instant().minus(Duration.ofMinutes(5)));
        assertThat(servicio.listar(dueno, planta.id())).hasSize(1);
    }

    @Test
    void unSerialQueNoExisteEs404() {
        assertThatThrownBy(() -> servicio.reclamar(dueno, planta.id(), "fito-NOEXISTE"))
                .isInstanceOf(DispositivoNoEncontradoException.class);
    }

    @Test
    void unDispositivoDeOtraPlantaEs409YNoSeRoba() {
        deFabrica();
        Planta otra = Planta.nueva(dueno, "Otra", Muestras.especie("helecho"));
        mundo.plantas.guardar(otra);
        servicio.reclamar(dueno, otra.id(), "fito-AAAAAA");

        assertThatThrownBy(() -> servicio.reclamar(dueno, planta.id(), "fito-AAAAAA"))
                .isInstanceOf(DispositivoYaReclamadoException.class);
        assertThat(mundo.dispositivos.porSerial("fito-AAAAAA").orElseThrow().plantaId()).contains(otra.id());
    }

    @Test
    void elInvitadoNoPuedeReclamarListarNiDesvincularYUnAjenoRecibeLoMismoQueSiNoExistiera() {
        deFabrica();
        servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");

        assertThatThrownBy(() -> servicio.reclamar(invitado, planta.id(), "fito-AAAAAA"))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.listar(invitado, planta.id())).isInstanceOf(OperacionNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.desvincular(invitado, planta.id(), "fito-AAAAAA"))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.reclamar(ajeno, planta.id(), "fito-AAAAAA"))
                .isInstanceOf(PlantaNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.listar(ajeno, planta.id())).isInstanceOf(PlantaNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.desvincular(ajeno, planta.id(), "fito-AAAAAA"))
                .isInstanceOf(PlantaNoEncontradaException.class);
    }

    @Test
    void desvincularLoDejaSinReclamarYUnaPlantaAjenaNoLoDesvincula() {
        deFabrica();
        servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");
        Planta otra = Planta.nueva(dueno, "Otra", Muestras.especie("helecho"));
        mundo.plantas.guardar(otra);

        assertThatThrownBy(() -> servicio.desvincular(dueno, otra.id(), "fito-AAAAAA"))
                .isInstanceOf(DispositivoNoEncontradoException.class);
        servicio.desvincular(dueno, planta.id(), "fito-AAAAAA");

        assertThat(mundo.dispositivos.porSerial("fito-AAAAAA").orElseThrow().estaReclamado()).isFalse();
        assertThat(servicio.listar(dueno, planta.id())).isEmpty();
    }

    @Test
    void eliminarLaPlantaDesvinculaSusDispositivosYNoLosBorra() {
        deFabrica();
        servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");

        mundo.cascada.eliminarPlanta(planta);

        assertThat(mundo.dispositivos.porSerial("fito-AAAAAA").orElseThrow().estaReclamado()).isFalse();
    }

    // ---- reto y credencial ----

    @Test
    void elRetoSeTraduceABase64EstandarConRellenoYVale60Segundos() {
        deFabrica();

        Reto reto = servicio.reto("fito-AAAAAA");

        // el generador fijo entrega "token-N": el guion de base64url pasa a "+" y se agrega el relleno
        assertThat(reto.valor()).doesNotContain("-", "_").endsWith("=");
        assertThat(reto.expira()).isEqualTo(reloj.instant().plusSeconds(60));
    }

    @Test
    void retoDeUnSerialQueNoExisteEs404() {
        assertThatThrownBy(() -> servicio.reto("fito-NOEXISTE")).isInstanceOf(DispositivoNoEncontradoException.class);
    }

    @Test
    void conRetoYRespuestaCorrectaUnDispositivoReclamadoRecibeSuCredencialYQuedaRegistrado() {
        DispositivoDeFabrica f = deFabrica();
        servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");
        Reto reto = servicio.reto("fito-AAAAAA");
        reloj.avanzar(Duration.ofSeconds(2));

        TokenDeDispositivo token = servicio.credencial("fito-AAAAAA", responder(f, reto));

        assertThat(token.serial()).isEqualTo("fito-AAAAAA");
        assertThat(token.plantaId()).isEqualTo(planta.id());
        assertThat(emisor.emitidos()).containsExactly(token);
        assertThat(mundo.dispositivos.porSerial("fito-AAAAAA").orElseThrow().ultimaCredencialEn())
                .contains(reloj.instant());
    }

    @Test
    void sinReclamarEsUn409YElRetoSeConsumeIgual() {
        DispositivoDeFabrica f = deFabrica();
        Reto reto = servicio.reto("fito-AAAAAA");

        assertThatThrownBy(() -> servicio.credencial("fito-AAAAAA", responder(f, reto)))
                .isInstanceOf(DispositivoSinReclamarException.class);

        assertThat(mundo.dispositivos.porSerial("fito-AAAAAA").orElseThrow().reto()).isEmpty();
        assertThat(emisor.emitidos()).isEmpty();
    }

    @Test
    void unaRespuestaMalaEs401NoEmiteNadaYConsumeElReto() {
        DispositivoDeFabrica f = deFabrica();
        servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");
        Reto reto = servicio.reto("fito-AAAAAA");

        assertThatThrownBy(() -> servicio.credencial("fito-AAAAAA", "respuesta-mala"))
                .isInstanceOf(CredencialRechazadaException.class);

        // el mismo reto ya no sirve, ni siquiera con la respuesta buena
        assertThatThrownBy(() -> servicio.credencial("fito-AAAAAA", responder(f, reto)))
                .isInstanceOf(CredencialRechazadaException.class);
        assertThat(emisor.emitidos()).isEmpty();
        assertThat(mundo.dispositivos.porSerial("fito-AAAAAA").orElseThrow().ultimaCredencialEn()).isEmpty();
    }

    @Test
    void sinHaberPedidoRetoEs401() {
        DispositivoDeFabrica f = deFabrica();
        servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");

        assertThatThrownBy(() -> servicio.credencial("fito-AAAAAA", responder(f, new Reto("inventado", reloj.instant()))))
                .isInstanceOf(CredencialRechazadaException.class);
    }

    @Test
    void unRetoVencidoEs401AunqueLaRespuestaSeaBuena() {
        DispositivoDeFabrica f = deFabrica();
        servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");
        Reto reto = servicio.reto("fito-AAAAAA");
        reloj.avanzar(Duration.ofSeconds(61));

        assertThatThrownBy(() -> servicio.credencial("fito-AAAAAA", responder(f, reto)))
                .isInstanceOf(CredencialRechazadaException.class);
    }

    @Test
    void unRetoNoSePuedeReusar() {
        DispositivoDeFabrica f = deFabrica();
        servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");
        Reto reto = servicio.reto("fito-AAAAAA");
        servicio.credencial("fito-AAAAAA", responder(f, reto));

        assertThatThrownBy(() -> servicio.credencial("fito-AAAAAA", responder(f, reto)))
                .isInstanceOf(CredencialRechazadaException.class);
        assertThat(emisor.emitidos()).hasSize(1);
    }

    @Test
    void pedirOtroRetoInvalidaElAnterior() {
        DispositivoDeFabrica f = deFabrica();
        servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");
        Reto primero = servicio.reto("fito-AAAAAA");
        Reto segundo = servicio.reto("fito-AAAAAA");

        assertThatThrownBy(() -> servicio.credencial("fito-AAAAAA", responder(f, primero)))
                .isInstanceOf(CredencialRechazadaException.class);
        // ese intento gasto el reto vigente (el segundo): hay que pedir otro
        assertThatThrownBy(() -> servicio.credencial("fito-AAAAAA", responder(f, segundo)))
                .isInstanceOf(CredencialRechazadaException.class);
    }

    @Test
    void despuesDeDesvincularNoHayCredencialNuevaYLaVueltaAReclamarLaRestituye() {
        DispositivoDeFabrica f = deFabrica();
        servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");
        servicio.credencial("fito-AAAAAA", responder(f, servicio.reto("fito-AAAAAA")));
        servicio.desvincular(dueno, planta.id(), "fito-AAAAAA");

        Reto reto = servicio.reto("fito-AAAAAA");
        assertThatThrownBy(() -> servicio.credencial("fito-AAAAAA", responder(f, reto)))
                .isInstanceOf(DispositivoSinReclamarException.class);

        servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");
        assertThat(servicio.credencial("fito-AAAAAA", responder(f, servicio.reto("fito-AAAAAA"))).plantaId())
                .isEqualTo(planta.id());
    }

    @Test
    void credencialDeUnSerialQueNoExisteEs404() {
        assertThatThrownBy(() -> servicio.credencial("fito-NOEXISTE", "x"))
                .isInstanceOf(DispositivoNoEncontradoException.class);
    }

    // ---- auto-registro con clave de flota (fase 3b) ----

    private static final String SECRETO_DEL_CHIP = "c".repeat(43);

    private static String firma(String serial, String secreto) {
        return AutenticadorDeDispositivosFalso.firmaDeFlota(serial, secreto);
    }

    @Test
    void unRegistroConFirmaValidaCreaUnDispositivoDeLaFlotaSinReclamar() {
        var r = servicio.registrar("fito-CHIP01", SECRETO_DEL_CHIP, firma("fito-CHIP01", SECRETO_DEL_CHIP));

        assertThat(r.creado()).isTrue();
        assertThat(r.dispositivo().registradoPor()).isEqualTo(OrigenDeRegistro.FLOTA);
        assertThat(r.dispositivo().estaReclamado()).isFalse();
        assertThat(mundo.dispositivos.porSerial("fito-CHIP01")).isPresent();
    }

    @Test
    void registrarDeNuevoConElMismoSecretoEsIdempotenteYNoToca() {
        servicio.registrar("fito-CHIP01", SECRETO_DEL_CHIP, firma("fito-CHIP01", SECRETO_DEL_CHIP));
        servicio.reclamar(dueno, planta.id(), "fito-CHIP01");

        var otra = servicio.registrar("fito-CHIP01", SECRETO_DEL_CHIP, firma("fito-CHIP01", SECRETO_DEL_CHIP));

        assertThat(otra.creado()).isFalse();
        assertThat(otra.dispositivo().plantaId()).contains(planta.id());   // el reclamo sigue intacto
    }

    @Test
    void elMismoSerialConOtroSecretoEs409AunqueLaFirmaSeaValida() {
        servicio.registrar("fito-CHIP01", SECRETO_DEL_CHIP, firma("fito-CHIP01", SECRETO_DEL_CHIP));
        String otro = "d".repeat(43);

        assertThatThrownBy(() -> servicio.registrar("fito-CHIP01", otro, firma("fito-CHIP01", otro)))
                .isInstanceOf(DispositivoYaExisteException.class);
        assertThat(mundo.dispositivos.porSerial("fito-CHIP01").orElseThrow().tieneElSecreto(SECRETO_DEL_CHIP)).isTrue();
    }

    @Test
    void unaFirmaMalaEs401SinMirarSiElSerialExiste() {
        assertThatThrownBy(() -> servicio.registrar("fito-CHIP01", SECRETO_DEL_CHIP, "firma-falsa"))
                .isInstanceOf(FirmaDeFlotaInvalidaException.class);
        deFabrica();   // fito-AAAAAA existe
        assertThatThrownBy(() -> servicio.registrar("fito-AAAAAA", SECRETO_DEL_CHIP, "firma-falsa"))
                .isInstanceOf(FirmaDeFlotaInvalidaException.class);
        assertThat(mundo.dispositivos.porSerial("fito-CHIP01")).isEmpty();
    }

    @Test
    void unSecretoCortoOUnSerialMalformadoSonArgumentoInvalidoPeroSoloConFirmaValida() {
        assertThatThrownBy(() -> servicio.registrar("fito-CHIP01", "corto", firma("fito-CHIP01", "corto")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.registrar("con espacios", SECRETO_DEL_CHIP, firma("con espacios", SECRETO_DEL_CHIP)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void despuesDelRegistroElChipPideCredencialYEsperaElReclamo() {
        servicio.registrar("fito-CHIP01", SECRETO_DEL_CHIP, firma("fito-CHIP01", SECRETO_DEL_CHIP));

        Reto reto = servicio.reto("fito-CHIP01");
        assertThatThrownBy(() -> servicio.credencial("fito-CHIP01",
                AutenticadorDeDispositivosFalso.respuestaCorrecta(SECRETO_DEL_CHIP, reto.valor())))
                .isInstanceOf(DispositivoSinReclamarException.class);

        servicio.reclamar(dueno, planta.id(), "fito-CHIP01");
        Reto otro = servicio.reto("fito-CHIP01");
        assertThat(servicio.credencial("fito-CHIP01",
                AutenticadorDeDispositivosFalso.respuestaCorrecta(SECRETO_DEL_CHIP, otro.valor())).plantaId())
                .isEqualTo(planta.id());
    }

    @Test
    void reclamarUnSerialSinRegistroDiceQueLaMateraNoHaEncendido() {
        assertThatThrownBy(() -> servicio.reclamar(dueno, planta.id(), "fito-NOHAY1"))
                .isInstanceOf(DispositivoNoEncontradoException.class)
                .hasMessageContaining("la matera no ha encendido todavía");
    }

    // ---- vínculo vigente en cada lectura (D96) ----

    @Test
    void exigirVinculoPasaSoloSiElDispositivoSigueReclamadoPorEsaPlanta() {
        deFabrica();
        servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");

        servicio.exigirVinculo("fito-AAAAAA", planta.id());   // no lanza
    }

    @Test
    void exigirVinculoRechazaUnDispositivoQueNoExisteOSinReclamarOReclamadoPorOtraPlanta() {
        assertThatThrownBy(() -> servicio.exigirVinculo("fito-NOEXIS", planta.id()))
                .isInstanceOf(CredencialRechazadaException.class);

        deFabrica();
        assertThatThrownBy(() -> servicio.exigirVinculo("fito-AAAAAA", planta.id()))
                .isInstanceOf(CredencialRechazadaException.class);

        Planta otra = Planta.nueva(ajeno, "Otra", Muestras.especie("helecho"));
        mundo.plantas.guardar(otra);
        servicio.reclamar(ajeno, otra.id(), "fito-AAAAAA");
        assertThatThrownBy(() -> servicio.exigirVinculo("fito-AAAAAA", planta.id()))
                .isInstanceOf(CredencialRechazadaException.class);
        servicio.exigirVinculo("fito-AAAAAA", otra.id());
    }

    @Test
    void desvincularBorraLaUltimaCredencialYElRetoPendiente() {
        DispositivoDeFabrica fabrica = deFabrica();
        servicio.reclamar(dueno, planta.id(), "fito-AAAAAA");
        Reto reto = servicio.reto("fito-AAAAAA");
        servicio.credencial("fito-AAAAAA", responder(fabrica, reto));
        servicio.reto("fito-AAAAAA");

        servicio.desvincular(dueno, planta.id(), "fito-AAAAAA");

        Dispositivo d = mundo.dispositivos.porSerial("fito-AAAAAA").orElseThrow();
        assertThat(d.ultimaCredencialEn()).isEmpty();
        assertThat(d.reto()).isEmpty();
        assertThatThrownBy(() -> servicio.exigirVinculo("fito-AAAAAA", planta.id()))
                .isInstanceOf(CredencialRechazadaException.class);
    }
}
