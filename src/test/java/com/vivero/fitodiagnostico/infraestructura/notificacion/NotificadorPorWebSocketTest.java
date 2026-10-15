package com.vivero.fitodiagnostico.infraestructura.notificacion;

import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.infraestructura.notificacion.NotificadorPorWebSocket.MensajeEnVivo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** El adaptador de la vista en vivo (RF-G2, D59): a quién y a qué destino empuja, y qué nunca empuja. */
class NotificadorPorWebSocketTest {

    private final SimpMessageSendingOperations mensajes = mock(SimpMessageSendingOperations.class);
    private final NotificadorPorWebSocket notificador = new NotificadorPorWebSocket(mensajes);
    private final UUID dueno = UUID.randomUUID();
    private final UUID invitado = UUID.randomUUID();
    private final UUID planta = UUID.randomUUID();

    @Test
    void elDiagnosticoNuevoVaAlDestinoDeLaPlantaDeCadaDestinatario() {
        notificador.avisar(new Aviso(Set.of(dueno, invitado), TipoDeAviso.DIAGNOSTICO_NUEVO, "nuevo",
                Map.of(Aviso.PLANTA_ID, planta.toString(), Aviso.ESTADO, "SALUDABLE")));

        ArgumentCaptor<MensajeEnVivo> captor = ArgumentCaptor.forClass(MensajeEnVivo.class);
        verify(mensajes).convertAndSendToUser(eq(dueno.toString()), eq("/queue/plantas/" + planta), captor.capture());
        verify(mensajes).convertAndSendToUser(eq(invitado.toString()), eq("/queue/plantas/" + planta), any(MensajeEnVivo.class));
        assertThat(captor.getValue().tipo()).isEqualTo(TipoDeAviso.DIAGNOSTICO_NUEVO);
        assertThat(captor.getValue().datos()).containsEntry(Aviso.ESTADO, "SALUDABLE");
    }

    @Test
    void elCambioDeEstadoTambienVaAlDestinoDeLaPlanta() {
        assertThat(NotificadorPorWebSocket.destinoDe(new Aviso(Set.of(), TipoDeAviso.CAMBIO_DE_ESTADO, "x",
                Map.of(Aviso.PLANTA_ID, planta.toString())))).isEqualTo("/queue/plantas/" + planta);
    }

    @Test
    void elAccesoPerdidoVaAlCanalGeneralDeAvisosAunqueTraigaLaPlanta() {
        // Quien perdió el acceso ya no puede suscribirse al destino de esa planta.
        assertThat(NotificadorPorWebSocket.destinoDe(new Aviso(Set.of(), TipoDeAviso.ACCESO_PERDIDO, "x",
                Map.of(Aviso.PLANTA_ID, planta.toString())))).isEqualTo("/queue/avisos");
        assertThat(NotificadorPorWebSocket.destinoDe(new Aviso(Set.of(), TipoDeAviso.SOLICITUD_RESUELTA, "x")))
                .isEqualTo("/queue/avisos");
    }

    @Test
    void sinPlantaEnLosDatosUnAvisoDePlantaCaeEnElCanalGeneral() {
        assertThat(NotificadorPorWebSocket.destinoDe(new Aviso(Set.of(), TipoDeAviso.CAMBIO_DE_ESTADO, "x")))
                .isEqualTo("/queue/avisos");
    }

    @Test
    void nuncaEmpujaElTokenAunqueElAvisoLoTraiga() {
        notificador.avisar(new Aviso(Set.of(dueno), TipoDeAviso.ACCESO_PERDIDO, "perdiste",
                Map.of(Aviso.TOKEN, "SECRETO", Aviso.PLANTA_ID, planta.toString())));

        ArgumentCaptor<MensajeEnVivo> captor = ArgumentCaptor.forClass(MensajeEnVivo.class);
        verify(mensajes).convertAndSendToUser(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue().datos()).doesNotContainKey(Aviso.TOKEN).containsKey(Aviso.PLANTA_ID);
    }

    @Test
    void sinDestinatariosNoEmpujaNada() {
        notificador.avisar(Aviso.aCorreos(Set.of("a@b.co"), TipoDeAviso.INVITACION, "x", Map.of()));

        verify(mensajes, never()).convertAndSendToUser(anyString(), anyString(), any(Object.class));
    }

    @Test
    void siUnoFallaLosDemasSiguenRecibiendo() {
        doThrow(new IllegalStateException("sesión cerrada")).when(mensajes)
                .convertAndSendToUser(eq(dueno.toString()), anyString(), any(Object.class));

        notificador.avisar(new Aviso(Set.of(dueno, invitado), TipoDeAviso.DIAGNOSTICO_NUEVO, "nuevo",
                Map.of(Aviso.PLANTA_ID, planta.toString())));

        verify(mensajes).convertAndSendToUser(eq(invitado.toString()), anyString(), any(Object.class));
    }
}
