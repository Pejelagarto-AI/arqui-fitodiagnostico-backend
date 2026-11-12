package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dobles.EspeciesEnMemoria;
import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dobles.NotificadorGrabador;
import com.vivero.fitodiagnostico.dobles.RelojManual;
import com.vivero.fitodiagnostico.dobles.SolicitudesEnMemoria;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieYaExisteException;
import com.vivero.fitodiagnostico.dominio.excepcion.SolicitudNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.SolicitudYaResueltaException;
import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoSolicitud;
import com.vivero.fitodiagnostico.dominio.modelo.SolicitudEspecie;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@link ServicioSolicitudes}: pedir, ver las propias, resolver y avisar (RF-H1 a RF-H5). */
class ServicioSolicitudesTest {

    private SolicitudesEnMemoria solicitudes;
    private EspeciesEnMemoria especies;
    private NotificadorGrabador notificador;
    private RelojManual reloj;
    private ServicioSolicitudes servicio;
    private final UUID ana = UUID.randomUUID();
    private final UUID beto = UUID.randomUUID();

    @BeforeEach
    void preparar() {
        solicitudes = new SolicitudesEnMemoria();
        especies = new EspeciesEnMemoria(Muestras.especie("potos"), Muestras.especieInactiva("helecho"));
        notificador = new NotificadorGrabador();
        reloj = RelojManual.enUnDiaCualquiera();
        servicio = new ServicioSolicitudes(solicitudes, especies, notificador, reloj);
    }

    @Test
    void enviarGuardaUnaSolicitudPendienteDelSolicitante() {
        SolicitudEspecie creada = servicio.enviar(ana, "Monstera", "no está en la lista");

        assertThat(creada.estado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        assertThat(creada.solicitanteId()).isEqualTo(ana);
        assertThat(creada.creadaEn()).isEqualTo(reloj.instant());
        assertThat(servicio.deSolicitante(ana)).containsExactly(creada);
        assertThat(notificador.avisos()).as("pedir no avisa a nadie").isEmpty();
    }

    @Test
    void pedirUnaEspecieQueYaEstaEnElCatalogoActivaOInactivaSeRechaza() {
        assertThatThrownBy(() -> servicio.enviar(ana, "POTOS", null)).isInstanceOf(EspecieYaExisteException.class);
        assertThatThrownBy(() -> servicio.enviar(ana, "helecho", null)).isInstanceOf(EspecieYaExisteException.class);
        assertThat(solicitudes.cuantas()).isZero();
    }

    @Test
    void pedirSinNombreFallaPorValidacionDeDominio() {
        assertThatThrownBy(() -> servicio.enviar(ana, "  ", "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cadaClienteVeSoloLasSuyasLaMasRecientePrimero() {
        SolicitudEspecie primera = servicio.enviar(ana, "Monstera", null);
        reloj.avanzar(Duration.ofMinutes(5));
        SolicitudEspecie segunda = servicio.enviar(ana, "Calathea", null);
        servicio.enviar(beto, "Ficus", null);

        assertThat(servicio.deSolicitante(ana)).containsExactly(segunda, primera);
        assertThat(servicio.deSolicitante(UUID.randomUUID())).isEmpty();
    }

    @Test
    void lasPendientesSalenEnOrdenDeLlegadaYLasResueltasDejanDeEstar() {
        SolicitudEspecie primera = servicio.enviar(ana, "Monstera", null);
        reloj.avanzar(Duration.ofMinutes(5));
        SolicitudEspecie segunda = servicio.enviar(beto, "Calathea", null);
        assertThat(servicio.pendientes()).extracting(SolicitudEspecie::id).containsExactly(primera.id(), segunda.id());

        servicio.aprobar(primera.id(), null);

        assertThat(servicio.pendientes()).extracting(SolicitudEspecie::id).containsExactly(segunda.id());
    }

    @Test
    void aprobarNoCreaLaEspecieYNotificaAlSolicitanteConLaRespuesta() {
        SolicitudEspecie creada = servicio.enviar(ana, "Monstera", null);

        SolicitudEspecie aprobada = servicio.aprobar(creada.id(), "la damos de alta esta semana");

        assertThat(aprobada.estado()).isEqualTo(EstadoSolicitud.APROBADA);
        assertThat(aprobada.respuesta()).isEqualTo("la damos de alta esta semana");
        assertThat(aprobada.resueltaEn()).isEqualTo(reloj.instant());
        assertThat(especies.buscar("Monstera")).as("D55: aprobar no da de alta la especie").isEmpty();
        assertThat(notificador.avisosDeTipo(TipoDeAviso.SOLICITUD_RESUELTA)).singleElement().satisfies(aviso -> {
            assertThat(aviso.destinatarios()).containsExactly(ana);
            assertThat(aviso.mensaje()).contains("Monstera").contains("aprobada").contains("la damos de alta esta semana");
            assertThat(aviso.datos()).containsEntry(Aviso.SOLICITUD_ID, creada.id().toString())
                    .containsEntry(Aviso.RESOLUCION, "APROBADA");
        });
    }

    @Test
    void rechazarExigeMotivoYNotifica() {
        SolicitudEspecie creada = servicio.enviar(ana, "Monstera", null);

        assertThatThrownBy(() -> servicio.rechazar(creada.id(), " ")).isInstanceOf(IllegalArgumentException.class);
        assertThat(notificador.avisos()).isEmpty();
        assertThat(solicitudes.buscar(creada.id()).orElseThrow().estado()).isEqualTo(EstadoSolicitud.PENDIENTE);

        SolicitudEspecie rechazada = servicio.rechazar(creada.id(), "ya existe como Monstera deliciosa");

        assertThat(rechazada.estado()).isEqualTo(EstadoSolicitud.RECHAZADA);
        assertThat(notificador.avisosDeTipo(TipoDeAviso.SOLICITUD_RESUELTA)).singleElement().satisfies(aviso -> {
            assertThat(aviso.mensaje()).contains("rechazada").contains("ya existe como Monstera deliciosa");
            assertThat(aviso.datos()).containsEntry(Aviso.RESOLUCION, "RECHAZADA");
        });
    }

    @Test
    void unaSolicitudYaResueltaNoSeResuelveDeNuevoNiAvisaOtraVez() {
        SolicitudEspecie creada = servicio.enviar(ana, "Monstera", null);
        servicio.aprobar(creada.id(), null);

        assertThatThrownBy(() -> servicio.rechazar(creada.id(), "mejor no")).isInstanceOf(SolicitudYaResueltaException.class);
        assertThatThrownBy(() -> servicio.aprobar(creada.id(), null)).isInstanceOf(SolicitudYaResueltaException.class);
        assertThat(notificador.avisos()).hasSize(1);
    }

    @Test
    void resolverUnaQueNoExisteEsNoEncontrada() {
        assertThatThrownBy(() -> servicio.aprobar(UUID.randomUUID(), null)).isInstanceOf(SolicitudNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.rechazar(UUID.randomUUID(), "x")).isInstanceOf(SolicitudNoEncontradaException.class);
    }
}
