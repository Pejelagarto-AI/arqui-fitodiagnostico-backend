package com.vivero.fitodiagnostico.adaptador.salida.notificacion;

import com.vivero.fitodiagnostico.adaptador.salida.notificacion.PublicadorDeMascotaPorWebSocket.MensajeDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.Animo;
import com.vivero.fitodiagnostico.mascota.modelo.AvisoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.EventoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeEvento;
import com.vivero.fitodiagnostico.mascota.puerto.salida.PublicadorDeMascota;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** El canal de la vista en vivo de la mascota y el reparto tras el commit (D142). */
class PublicadorDeMascotaPorWebSocketTest {

    private final SimpMessageSendingOperations mensajes = mock(SimpMessageSendingOperations.class);
    private final UUID dueno = UUID.randomUUID();
    private final UUID invitado = UUID.randomUUID();
    private final UUID planta = UUID.randomUUID();

    private AvisoDeMascota aviso() {
        EventoDeMascota evento = new EventoDeMascota(UUID.randomUUID(), planta, TipoDeEvento.REGADA,
                Instant.parse("2026-11-16T08:05:00Z"), Map.of("referencia", "30.0"));
        return new AvisoDeMascota(evento, "Pepa", 64, Animo.FELIZ, Set.of(dueno, invitado), Set.of("fito-1"));
    }

    @Test
    void vaAlDestinoDeLaPlantaDeCadaPersonaConTipoDatosYDetalle() {
        new PublicadorDeMascotaPorWebSocket(mensajes).publicar(aviso());

        ArgumentCaptor<MensajeDeMascota> captor = ArgumentCaptor.forClass(MensajeDeMascota.class);
        verify(mensajes).convertAndSendToUser(eq(dueno.toString()), eq("/queue/plantas/" + planta), captor.capture());
        verify(mensajes).convertAndSendToUser(eq(invitado.toString()), eq("/queue/plantas/" + planta), any(MensajeDeMascota.class));
        MensajeDeMascota m = captor.getValue();
        assertThat(m.tipo()).isEqualTo("EVENTO_DE_MASCOTA");
        assertThat(m.mensaje()).isEqualTo("«Pepa» recibió agua.");
        assertThat(m.datos()).containsEntry("plantaId", planta.toString()).containsEntry("evento", "REGADA")
                .containsEntry("vitalidad", "64").containsEntry("animo", "FELIZ")
                .containsEntry("ocurridoEn", "2026-11-16T08:05:00Z").containsKey("eventoId");
        assertThat(m.detalle()).containsEntry("referencia", "30.0");
    }

    @Test
    void sinTransaccionSaleEnElActoYUnCanalQueFallaNoFrenaALosDemas() {
        List<AvisoDeMascota> recibidos = new ArrayList<>();
        PublicadorDeMascota roto = a -> { throw new IllegalStateException("broker caído"); };
        PublicadorDeMascota sano = recibidos::add;

        new PublicadorDeMascotaTrasCommit(List.of(roto, sano)).publicar(aviso());

        assertThat(recibidos).hasSize(1);
    }
}
