package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dobles.MundoEnMemoria;
import com.vivero.fitodiagnostico.dobles.RelojManual;
import com.vivero.fitodiagnostico.dobles.SaludEnMemoria;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.salud.EstadoDeSalud;
import com.vivero.fitodiagnostico.salud.EstadoDeSensor;
import com.vivero.fitodiagnostico.salud.SaludDelDispositivo;
import com.vivero.fitodiagnostico.salud.SensorDeMatera;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** La salud de la matera con dobles en memoria: cambios, avisos, recuperación y silencio. */
class ServicioSaludTest {

    private static final String SERIAL = "fito-AAAAAA";
    private static final Map<SensorDeMatera, EstadoDeSensor> TODO_OK = SaludDelDispositivo.todosOk();
    private static final Map<SensorDeMatera, EstadoDeSensor> SIN_TEMPERATURA = Map.of(
            SensorDeMatera.HUMEDAD, EstadoDeSensor.OK, SensorDeMatera.LUZ, EstadoDeSensor.OK,
            SensorDeMatera.TEMPERATURA, EstadoDeSensor.FALLA);

    private final UUID dueno = UUID.randomUUID();
    private final UUID invitado = UUID.randomUUID();

    private MundoEnMemoria mundo;
    private SaludEnMemoria salud;
    private RelojManual reloj;
    private ServicioSalud servicio;
    private Planta planta;

    @BeforeEach
    void preparar() {
        mundo = new MundoEnMemoria();
        salud = new SaludEnMemoria();
        reloj = RelojManual.enUnDiaCualquiera();
        servicio = new ServicioSalud(salud, mundo.dispositivos, mundo.plantas, mundo.accesos, salud, reloj,
                Duration.ofSeconds(5), Duration.ofMinutes(2), Duration.ofMinutes(5));
        planta = Planta.nueva(dueno, "Pepa", Muestras.especie("helecho"));
        mundo.plantas.guardar(planta);
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), invitado));
        Dispositivo d = Dispositivo.deFabrica(SERIAL, "secreto", reloj.instant());
        d.reclamar(planta.id(), reloj.instant());
        mundo.dispositivos.guardar(d);
    }

    @Test
    void unaFallaSeGuardaYAvisaAlDuenoYAlInvitadoConLaFrase() {
        var r = servicio.reportar(SERIAL, planta.id(), SIN_TEMPERATURA, "dht22 sin respuesta");

        assertThat(r.estado()).isEqualTo(EstadoDeSalud.FALLA);
        assertThat(r.cambio()).isTrue();
        assertThat(salud.de(SERIAL)).get().satisfies(s -> {
            assertThat(s.sensoresConFalla()).containsExactly(SensorDeMatera.TEMPERATURA);
            assertThat(s.detalle()).isEqualTo("dht22 sin respuesta");
        });
        assertThat(salud.avisos).hasSize(1);
        assertThat(salud.avisos.get(0).mensaje()).isEqualTo("no te siento la temperatura");
        assertThat(salud.avisos.get(0).destinatarios()).containsExactlyInAnyOrder(dueno, invitado);
        assertThat(salud.avisos.get(0).plantaId()).isEqualTo(planta.id());
    }

    @Test
    void repetirElMismoReporteActualizaLaHoraPeroNoVuelveAAvisar() {
        servicio.reportar(SERIAL, planta.id(), SIN_TEMPERATURA, "dht22 sin respuesta");
        var cambioEn = salud.de(SERIAL).orElseThrow().cambioEn();
        reloj.avanzar(Duration.ofMinutes(5));

        var r = servicio.reportar(SERIAL, planta.id(), SIN_TEMPERATURA, "dht22 sin respuesta");

        assertThat(r.cambio()).isFalse();
        assertThat(salud.avisos).hasSize(1);
        assertThat(salud.de(SERIAL).orElseThrow().cambioEn()).isEqualTo(cambioEn);
        assertThat(salud.de(SERIAL).orElseThrow().reportadoEn()).isEqualTo(reloj.instant());
    }

    @Test
    void siSeCaenDosOTresSensoresLaFraseEsGeneral() {
        var dos = Map.of(SensorDeMatera.HUMEDAD, EstadoDeSensor.FALLA, SensorDeMatera.LUZ, EstadoDeSensor.OK,
                SensorDeMatera.TEMPERATURA, EstadoDeSensor.FALLA);
        servicio.reportar(SERIAL, planta.id(), dos, null);
        assertThat(salud.avisos.get(0).mensaje()).isEqualTo("no siento mis sensores");
    }

    @Test
    void recuperarseEnviaOkYAvisaYBorraElDetalle() {
        servicio.reportar(SERIAL, planta.id(), SIN_TEMPERATURA, "dht22 sin respuesta");
        reloj.avanzar(Duration.ofMinutes(1));

        var r = servicio.reportar(SERIAL, planta.id(), TODO_OK, "ya responde");

        assertThat(r.estado()).isEqualTo(EstadoDeSalud.OK);
        assertThat(r.cambio()).isTrue();
        assertThat(salud.de(SERIAL).orElseThrow().detalle()).isNull();
        assertThat(salud.avisos).hasSize(2);
        assertThat(salud.avisos.get(1).salud().estado()).isEqualTo(EstadoDeSalud.OK);
    }

    @Test
    void elPrimerReporteSanoNoAvisaPorqueNoCambioNada() {
        var r = servicio.reportar(SERIAL, planta.id(), TODO_OK, null);
        assertThat(r.cambio()).isFalse();
        assertThat(salud.avisos).isEmpty();
        assertThat(salud.de(SERIAL)).isPresent();
    }

    @Test
    void unaLecturaGuardaLaHoraYNoAvisaSiTodoEstabaBien() {
        servicio.registrarLectura(SERIAL, planta.id());
        reloj.avanzar(Duration.ofSeconds(5));
        servicio.registrarLectura(SERIAL, planta.id());

        assertThat(salud.de(SERIAL).orElseThrow().ultimaLecturaEn()).isEqualTo(reloj.instant());
        assertThat(salud.de(SERIAL).orElseThrow().estado()).isEqualTo(EstadoDeSalud.OK);
        assertThat(salud.avisos).isEmpty();
    }

    @Test
    void unaLecturaCuandoHabiaFallaRecuperaYAvisa() {
        servicio.reportar(SERIAL, planta.id(), SIN_TEMPERATURA, "dht22 sin respuesta");
        reloj.avanzar(Duration.ofSeconds(30));

        servicio.registrarLectura(SERIAL, planta.id());

        assertThat(salud.de(SERIAL).orElseThrow().estado()).isEqualTo(EstadoDeSalud.OK);
        assertThat(salud.de(SERIAL).orElseThrow().sensoresConFalla()).isEmpty();
        assertThat(salud.avisos).hasSize(2);
    }

    // ---- silencio ----

    @Test
    void sinNoticiasPasaSoloDespuesDelMinimoDeDosMinutosAunqueTresIntervalosSeanQuinceSegundos() {
        servicio.registrarLectura(SERIAL, planta.id());

        reloj.avanzar(Duration.ofSeconds(119));
        assertThat(servicio.revisar()).isZero();

        reloj.avanzar(Duration.ofSeconds(2));
        assertThat(servicio.revisar()).isEqualTo(1);
        assertThat(salud.de(SERIAL).orElseThrow().estado()).isEqualTo(EstadoDeSalud.SIN_NOTICIAS);
        assertThat(salud.avisos).hasSize(1);
        assertThat(salud.avisos.get(0).mensaje()).isEqualTo("no sé de la matera desde hace 2 min");
    }

    @Test
    void conUnIntervaloLargoElTopeSonTresIntervalos() {
        servicio = new ServicioSalud(salud, mundo.dispositivos, mundo.plantas, mundo.accesos, salud, reloj,
                Duration.ofMinutes(1), Duration.ofMinutes(2), Duration.ofMinutes(5));
        servicio.registrarLectura(SERIAL, planta.id());

        reloj.avanzar(Duration.ofMinutes(3));
        assertThat(servicio.revisar()).isZero();
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(servicio.revisar()).isEqualTo(1);
    }

    @Test
    void unaMateraQueSigueReportandoSuFallaNoSeVaASinNoticias() {
        servicio.reportar(SERIAL, planta.id(), SIN_TEMPERATURA, "dht22 sin respuesta");
        for (int i = 0; i < 4; i++) {
            reloj.avanzar(Duration.ofMinutes(5));          // su latido de falla
            assertThat(servicio.revisar()).isZero();
            servicio.reportar(SERIAL, planta.id(), SIN_TEMPERATURA, "dht22 sin respuesta");
        }
        assertThat(salud.de(SERIAL).orElseThrow().estado()).isEqualTo(EstadoDeSalud.FALLA);
    }

    @Test
    void unaMateraConFallaQueCallaPasaASinNoticiasTrasTresLatidos() {
        servicio.reportar(SERIAL, planta.id(), SIN_TEMPERATURA, null);
        reloj.avanzar(Duration.ofMinutes(15));
        assertThat(servicio.revisar()).isZero();
        reloj.avanzar(Duration.ofMinutes(1));
        assertThat(servicio.revisar()).isEqualTo(1);
    }

    @Test
    void seAvisaUnaSolaVezAunqueLaRevisionSigaPasando() {
        servicio.registrarLectura(SERIAL, planta.id());
        reloj.avanzar(Duration.ofMinutes(10));
        servicio.revisar();
        servicio.revisar();
        reloj.avanzar(Duration.ofMinutes(10));
        servicio.revisar();
        assertThat(salud.avisos).hasSize(1);
    }

    @Test
    void cuandoVuelveUnaLecturaLaMateraSeRecuperaDelSilencio() {
        servicio.registrarLectura(SERIAL, planta.id());
        reloj.avanzar(Duration.ofMinutes(10));
        servicio.revisar();

        servicio.registrarLectura(SERIAL, planta.id());

        assertThat(salud.de(SERIAL).orElseThrow().estado()).isEqualTo(EstadoDeSalud.OK);
        assertThat(salud.avisos).hasSize(2);
    }

    @Test
    void unaMateraSinReclamarOQueNuncaDejoNoticiaNoSeVigila() {
        Dispositivo libre = Dispositivo.deFabrica("fito-BBBBBB", "s", reloj.instant());
        mundo.dispositivos.guardar(libre);
        salud.guardar(new SaludDelDispositivo("fito-BBBBBB", EstadoDeSalud.OK, TODO_OK, null, null,
                reloj.instant(), reloj.instant()));
        reloj.avanzar(Duration.ofHours(1));

        assertThat(servicio.revisar()).isZero();          // SERIAL sin fila, BBBBBB sin dueño
        assertThat(salud.avisos).isEmpty();
    }

    @Test
    void laConsultaTraeLaFraseConElTiempoQueLlevaCallada() {
        servicio.registrarLectura(SERIAL, planta.id());
        reloj.avanzar(Duration.ofMinutes(3));
        servicio.revisar();
        reloj.avanzar(Duration.ofMinutes(9));

        var vistas = servicio.deLosDispositivos(java.util.List.of(SERIAL, "fito-NADA"));

        assertThat(vistas).containsOnlyKeys(SERIAL);
        assertThat(vistas.get(SERIAL).mensaje()).isEqualTo("no sé de la matera desde hace 12 min");
    }

    // ---- fase 13 (D185): la peor salud por planta, para el tablero ----

    private void segundaMatera(String serial, UUID plantaId) {
        Dispositivo d = Dispositivo.deFabrica(serial, "secreto", reloj.instant());
        d.reclamar(plantaId, reloj.instant());
        mundo.dispositivos.guardar(d);
    }

    @Test
    void laPeorSaludDeLaPlantaGanaFallaSobreSinNoticiasSobreOkYSinMaterasNoAparece() {
        salud.vincular(SERIAL, planta.id());
        salud.vincular("fito-BBBBBB", planta.id());
        salud.vincular("fito-CCCCCC", planta.id());
        segundaMatera("fito-BBBBBB", planta.id());
        segundaMatera("fito-CCCCCC", planta.id());
        servicio.registrarLectura(SERIAL, planta.id());                                   // OK
        servicio.registrarLectura("fito-BBBBBB", planta.id());                            // OK, luego calla
        servicio.reportar("fito-CCCCCC", planta.id(), SIN_TEMPERATURA, "dht22 sin respuesta");   // FALLA
        Planta sola = Planta.nueva(dueno, "Sola", Muestras.especie("helecho"));
        mundo.plantas.guardar(sola);

        var vistas = servicio.laPeorDeLasPlantas(java.util.List.of(planta.id(), sola.id()));

        assertThat(vistas).containsOnlyKeys(planta.id());
        assertThat(vistas.get(planta.id()).salud().estado()).isEqualTo(EstadoDeSalud.FALLA);
        assertThat(vistas.get(planta.id()).mensaje()).isEqualTo("no te siento la temperatura");
    }

    @Test
    void sinFallasLaQueCallaGanaALaQueResponde() {
        salud.vincular(SERIAL, planta.id());
        salud.vincular("fito-BBBBBB", planta.id());
        segundaMatera("fito-BBBBBB", planta.id());
        servicio.registrarLectura(SERIAL, planta.id());
        reloj.avanzar(Duration.ofMinutes(10));
        servicio.registrarLectura("fito-BBBBBB", planta.id());
        servicio.revisar();   // SERIAL lleva 10 min callada: SIN_NOTICIAS; BBBBBB sigue OK

        var vistas = servicio.laPeorDeLasPlantas(java.util.List.of(planta.id()));

        assertThat(vistas.get(planta.id()).salud().serial()).isEqualTo(SERIAL);
        assertThat(vistas.get(planta.id()).salud().estado()).isEqualTo(EstadoDeSalud.SIN_NOTICIAS);
        assertThat(servicio.laPeorDeLasPlantas(java.util.List.of())).isEmpty();
    }
}
