package com.vivero.fitodiagnostico.adaptador.salida.notificacion;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.puerto.salida.Notificador;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificadorEnRegistroTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(NotificadorEnRegistro.class);
    private final ListAppender<ILoggingEvent> eventos = new ListAppender<>();

    @BeforeEach
    void escuchar() {
        eventos.start();
        logger.addAppender(eventos);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void dejarDeEscuchar() {
        logger.detachAppender(eventos);
    }

    @Test
    void cadaAvisoDejaUnaSolaLineaConTipoDestinatariosYTexto() {
        Notificador notificador = new NotificadorEnRegistro();
        UUID destinatario = UUID.randomUUID();

        notificador.avisar(new Aviso(Set.of(destinatario), TipoDeAviso.CAMBIO_DE_ESTADO,
                "La planta «Pepa» pasó de SALUDABLE a CRITICO."));

        assertThat(eventos.list).singleElement().satisfies(evento -> {
            assertThat(evento.getLevel()).isEqualTo(Level.INFO);
            assertThat(evento.getFormattedMessage())
                    .contains("CAMBIO_DE_ESTADO").contains(destinatario.toString()).contains("Pepa");
        });
    }

    @Test
    void unAvisoSinDestinatariosTambienSeRegistra() {
        new NotificadorEnRegistro().avisar(new Aviso(Set.of(), TipoDeAviso.ACCESO_PERDIDO, "x"));

        assertThat(eventos.list).hasSize(1);
        assertThat(eventos.list.get(0).getFormattedMessage()).contains("0 destinatario");
    }
}
