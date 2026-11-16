package com.vivero.fitodiagnostico.mascota.aplicacion;

import com.vivero.fitodiagnostico.aplicacion.ServicioMediciones;
import com.vivero.fitodiagnostico.dobles.MascotaEnMemoria;
import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dobles.MundoEnMemoria;
import com.vivero.fitodiagnostico.dobles.RelojManual;
import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.servicio.ClasificadorDeParametros;
import com.vivero.fitodiagnostico.dominio.servicio.DetectorDeCambioDeEstado;
import com.vivero.fitodiagnostico.dominio.servicio.EvaluadorDeEstado;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import com.vivero.fitodiagnostico.dominio.servicio.RedactorDeRecomendaciones;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaPorDesviacion;
import com.vivero.fitodiagnostico.mascota.modelo.Animo;
import com.vivero.fitodiagnostico.mascota.modelo.AvisoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.EstadoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.ParametrosDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.ResultadoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeEvento;
import com.vivero.fitodiagnostico.mascota.modelo.VistaDeMascota;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La mascota con dobles en memoria y el caso de uso de medir REAL, conectado por {@code ObservadorDeMediciones}
 * como lo conecta la composición (sin transacción, así que el puente actúa en el acto). Especie de prueba: humedad
 * 30-60, luz 500-1000, temperatura 18-26.
 */
class ServicioMascotaTest {

    private final UUID dueno = UUID.randomUUID();
    private final UUID invitado = UUID.randomUUID();
    private final UUID ajeno = UUID.randomUUID();
    private final RelojManual reloj = RelojManual.enUnDiaCualquiera();

    private MundoEnMemoria mundo;
    private MascotaEnMemoria mascota;
    private ServicioMascota servicio;
    private ServicioConsultaDeMascota consulta;
    private ServicioMediciones mediciones;
    private Planta planta;

    @BeforeEach
    void preparar() {
        mundo = new MundoEnMemoria();
        mascota = new MascotaEnMemoria();
        PoliticaDeAcceso politica = new PoliticaDeAcceso();
        servicio = new ServicioMascota(mundo.plantas, mundo.accesos, mundo.diagnosticos, mundo.dispositivos, politica,
                mascota, mascota, mascota, mascota, ParametrosDeMascota.POR_DEFECTO, reloj);
        consulta = new ServicioConsultaDeMascota(mundo.plantas, mundo.accesos, mundo.diagnosticos, politica,
                mascota, mascota, mascota, ParametrosDeMascota.POR_DEFECTO);
        mediciones = new ServicioMediciones(mundo.plantas, mundo.accesos, mundo.diagnosticos,
                new EvaluadorDeEstado(new ClasificadorDeParametros(),
                        new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO), new RedactorDeRecomendaciones()),
                new DetectorDeCambioDeEstado(), mundo.notificador, politica, reloj,
                List.of(servicio::alRegistrarLectura));
        planta = Planta.nueva(dueno, "Pepa", Muestras.especie("helecho"));
        mundo.plantas.guardar(planta);
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), invitado));
        Dispositivo matera = Dispositivo.deFabrica("fito-MASCOTA", "secreto-de-prueba", reloj.instant());
        matera.reclamar(planta.id(), reloj.instant());
        mundo.dispositivos.guardar(matera);
    }

    /** Una lectura del dispositivo, tomada "ahora", y un minuto después la siguiente. */
    private void lectura(double humedad, double luz, double temperatura) {
        mediciones.registrar(planta.id(), Muestras.medicion(humedad, luz, temperatura), OrigenDeMedicion.DISPOSITIVO,
                reloj.instant());
        reloj.avanzar(Duration.ofMinutes(1));
    }

    @Test
    void laPrimeraLecturaCreaLaMascotaSinEventos() {
        lectura(45, 700, 22);

        assertThat(mascota.de(planta.id())).hasValueSatisfying(e -> {
            assertThat(e.vitalidad()).isEqualTo(ParametrosDeMascota.VITALIDAD_INICIAL);
            assertThat(e.rachaDesde()).isNotNull();
        });
        assertThat(mascota.publicados).isEmpty();
    }

    @Test
    void elRiegoSeDetectaConLaSegundaLecturaYSeAvisaAPersonasYMatera() {
        for (int i = 0; i < 4; i++) {
            lectura(32, 700, 22);
        }
        lectura(50, 700, 22);
        assertThat(mascota.tiposDe(planta.id())).isEmpty();   // una sola lectura no dispara

        lectura(51, 700, 22);

        assertThat(mascota.tiposDe(planta.id())).containsExactly(TipoDeEvento.REGADA);
        AvisoDeMascota aviso = mascota.publicados.get(0);
        assertThat(aviso.destinatarios()).containsExactlyInAnyOrder(dueno, invitado);
        assertThat(aviso.seriales()).containsExactly("fito-MASCOTA");
        assertThat(aviso.animo()).isEqualTo(Animo.FELIZ);
        assertThat(aviso.mensaje()).isEqualTo("«Pepa» recibió agua.");
        assertThat(mascota.de(planta.id()).orElseThrow().ultimaRegadaEn()).isNotNull();
    }

    @Test
    void elTicHaceQueSeDesmayeAunqueNoLleguenLecturas() {
        lectura(5, 700, 22);   // CRITICO
        reloj.avanzar(Duration.ofHours(2));
        servicio.avanzar(planta.id());
        // 2 h en CRITICO desde la lectura (la ventana sin lecturas corta ahí): 80 - 40.
        assertThat(mascota.de(planta.id()).orElseThrow().vitalidad()).isCloseTo(40, org.assertj.core.api.Assertions.within(0.01));

        reloj.avanzar(Duration.ofHours(3));
        servicio.avanzar(planta.id());   // la ventana sin lecturas son 2 h: sigue en 40 y no se desmaya
        assertThat(mascota.de(planta.id()).orElseThrow().desmayada()).isFalse();

        lectura(5, 700, 22);
        reloj.avanzar(Duration.ofHours(2));
        servicio.avanzar(planta.id());
        assertThat(mascota.tiposDe(planta.id())).containsExactly(TipoDeEvento.DESMAYO);
        assertThat(mascota.publicados).extracting(AvisoDeMascota::animo).containsExactly(Animo.DESMAYADA);
        assertThat(servicio.mascotasActivas()).containsExactly(planta.id());
    }

    @Test
    void acariciarLoHaceSoloElDuenoYNoCambiaLaVitalidad() {
        lectura(45, 700, 22);

        ResultadoDeAccion r = servicio.actuar(dueno, planta.id(), TipoDeAccion.ACARICIAR);

        assertThat(r.estado()).isEqualTo(EstadoDeAccion.HECHA);
        assertThat(mascota.tiposDe(planta.id())).containsExactly(TipoDeEvento.ACARICIADA);
        assertThat(mascota.de(planta.id()).orElseThrow().vitalidad()).isEqualTo(ParametrosDeMascota.VITALIDAD_INICIAL);
        assertThatThrownBy(() -> servicio.actuar(invitado, planta.id(), TipoDeAccion.ACARICIAR))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.actuar(ajeno, planta.id(), TipoDeAccion.ACARICIAR))
                .isInstanceOf(PlantaNoEncontradaException.class);
    }

    @Test
    void lasCariciasDeLaMismaPlantaSeEnfriandiezSegundosYLasQueSobranDevuelvenLaUltima() {
        lectura(45, 700, 22);
        int avisosAntes = mascota.publicados.size();

        ResultadoDeAccion primera = servicio.actuar(dueno, planta.id(), TipoDeAccion.ACARICIAR);
        reloj.avanzar(Duration.ofSeconds(9));
        ResultadoDeAccion sobrante = servicio.actuar(dueno, planta.id(), TipoDeAccion.ACARICIAR);
        reloj.avanzar(Duration.ofSeconds(1));   // 10 s justos desde la primera: ya cuenta
        ResultadoDeAccion segunda = servicio.actuar(dueno, planta.id(), TipoDeAccion.ACARICIAR);

        assertThat(primera.repetida()).isFalse();
        assertThat(sobrante.repetida()).isTrue();
        assertThat(sobrante.id()).isEqualTo(primera.id());                // la última, no un evento nuevo
        assertThat(sobrante.creadaEn()).isEqualTo(primera.creadaEn());
        assertThat(sobrante.estado()).isEqualTo(EstadoDeAccion.HECHA);
        assertThat(segunda.repetida()).isFalse();
        assertThat(segunda.id()).isNotEqualTo(primera.id());
        assertThat(mascota.tiposDe(planta.id())).containsExactly(TipoDeEvento.ACARICIADA, TipoDeEvento.ACARICIADA);
        assertThat(mascota.publicados).hasSize(avisosAntes + 2);          // la sobrante no avisa a nadie ni a la matera
    }

    @Test
    void elEnfriamientoDeLaCariciaSeConfiguraYNoAplicaAOtraPlanta() {
        ParametrosDeMascota sinEspera = new ParametrosDeMascota(80, 20, 20, 5, 10, Duration.ofMinutes(10),
                Duration.ofHours(2), Duration.ofHours(24), 15, 2.0, 2000, Duration.ofHours(1), 3, 60,
                Duration.ofMinutes(30), Duration.ofMinutes(15), 30, Duration.ZERO);
        ServicioMascota libre = new ServicioMascota(mundo.plantas, mundo.accesos, mundo.diagnosticos, mundo.dispositivos,
                new PoliticaDeAcceso(), mascota, mascota, mascota, mascota, sinEspera, reloj);
        lectura(45, 700, 22);

        libre.actuar(dueno, planta.id(), TipoDeAccion.ACARICIAR);
        ResultadoDeAccion otra = libre.actuar(dueno, planta.id(), TipoDeAccion.ACARICIAR);

        assertThat(otra.repetida()).isFalse();
        assertThat(mascota.tiposDe(planta.id())).containsExactly(TipoDeEvento.ACARICIADA, TipoDeEvento.ACARICIADA);
    }

    @Test
    void laRegueQuedaPendienteYElSensorLaConfirma() {
        for (int i = 0; i < 4; i++) {
            lectura(32, 700, 22);
        }
        ResultadoDeAccion r = servicio.actuar(dueno, planta.id(), TipoDeAccion.LA_REGUE);
        assertThat(r.estado()).isEqualTo(EstadoDeAccion.PENDIENTE);
        assertThat(r.venceEn()).isEqualTo(r.creadaEn().plus(Duration.ofMinutes(15)));
        assertThat(servicio.actuar(dueno, planta.id(), TipoDeAccion.LA_REGUE).id()).isEqualTo(r.id());   // idempotente

        reloj.avanzar(Duration.ofSeconds(5));
        lectura(50, 700, 22);
        lectura(51, 700, 22);

        assertThat(mascota.accion(r.id()).orElseThrow().estado()).isEqualTo(EstadoDeAccion.CONFIRMADA);
        assertThat(mascota.tiposDe(planta.id())).containsExactly(TipoDeEvento.RIEGO_DECLARADO, TipoDeEvento.REGADA,
                TipoDeEvento.RIEGO_CONFIRMADO);
    }

    @Test
    void laRegueSinQueSubaLaHumedadSeDesmienteAlVencer() {
        lectura(32, 700, 22);
        ResultadoDeAccion r = servicio.actuar(dueno, planta.id(), TipoDeAccion.LA_REGUE);
        lectura(33, 700, 22);

        reloj.avanzar(Duration.ofMinutes(15));
        servicio.avanzar(planta.id());

        assertThat(mascota.accion(r.id()).orElseThrow().estado()).isEqualTo(EstadoDeAccion.DESMENTIDA);
        assertThat(mascota.tiposDe(planta.id())).endsWith(TipoDeEvento.RIEGO_DESMENTIDO);
    }

    @Test
    void laConsultaLaVenDuenoEInvitadoYUnAjenoRecibeNoEncontrada() {
        VistaDeMascota sinLecturas = consulta.consultar(dueno, planta.id());
        assertThat(sinLecturas.hayLecturas()).isFalse();
        assertThat(sinLecturas.animo()).isEqualTo(Animo.SIN_DATOS);

        lectura(10, 700, 22);   // humedad 20 bajo el mínimo: SEDIENTA
        servicio.actuar(dueno, planta.id(), TipoDeAccion.ACARICIAR);
        VistaDeMascota vista = consulta.consultar(invitado, planta.id());

        assertThat(vista.hayLecturas()).isTrue();
        assertThat(vista.animo()).isEqualTo(Animo.SEDIENTA);
        assertThat(vista.ultimosEventos()).extracting(e -> e.tipo()).containsExactly(TipoDeEvento.ACARICIADA);
        assertThat(consulta.eventos(dueno, planta.id(), new Paginacion(0, 10)).total()).isEqualTo(1);
        assertThatThrownBy(() -> consulta.consultar(ajeno, planta.id())).isInstanceOf(PlantaNoEncontradaException.class);
        assertThatThrownBy(() -> consulta.eventos(ajeno, planta.id(), Paginacion.primera()))
                .isInstanceOf(PlantaNoEncontradaException.class);
    }

    @Test
    void unaLecturaDeUnaPlantaQueYaNoExisteSeIgnora() {
        lectura(45, 700, 22);
        mundo.cascada.eliminarPlanta(planta);
        mascota.publicados.clear();

        servicio.avanzar(planta.id());

        assertThat(mascota.publicados).isEmpty();
        assertThat(Set.copyOf(mascota.tiposDe(planta.id()))).isEmpty();
    }
}
