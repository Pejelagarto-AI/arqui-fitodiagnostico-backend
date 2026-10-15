package com.vivero.fitodiagnostico.infraestructura.notificacion;

import com.vivero.fitodiagnostico.dobles.UsuariosEnMemoria;
import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.Credencial;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.infraestructura.configuracion.EnlacesDelFront;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** El adaptador de correo (D13, D54) con un "SMTP" que solo recuerda y un repositorio en memoria. */
class NotificadorPorCorreoTest {

    private final List<SimpleMailMessage> enviados = new ArrayList<>();
    private final UsuariosEnMemoria usuarios = new UsuariosEnMemoria();
    private final EnlacesDelFront enlaces = new EnlacesDelFront("http://front.local/");
    private Usuario ana;
    private Usuario beto;
    private NotificadorPorCorreo notificador;

    @BeforeEach
    void preparar() {
        MailSender capturador = new MailSender() {
            @Override
            public void send(SimpleMailMessage mensaje) {
                enviados.add(mensaje);
            }

            @Override
            public void send(SimpleMailMessage... mensajes) {
                enviados.addAll(List.of(mensajes));
            }
        };
        notificador = new NotificadorPorCorreo(capturador, usuarios, enlaces, "no-responder@vivero.co", false);
        ana = Usuario.nuevo("Ana", "ana@vivero.co", new Credencial("h"));
        beto = Usuario.nuevo("Beto", "beto@vivero.co", new Credencial("h"));
        usuarios.guardar(ana);
        usuarios.guardar(beto);
    }

    @Test
    void resuelveCadaIdAUnaDireccionYMandaUnCorreoPorDestinatarioSinCopiasAOtros() {
        notificador.avisar(new Aviso(Set.of(ana.id(), beto.id()), TipoDeAviso.CAMBIO_DE_ESTADO,
                "La planta «Pepa» pasó de SALUDABLE a CRITICO."));

        assertThat(enviados).hasSize(2);
        assertThat(enviados).extracting(m -> List.of(m.getTo())).containsExactlyInAnyOrder(
                List.of("ana@vivero.co"), List.of("beto@vivero.co"));
        assertThat(enviados).allSatisfy(m -> {
            assertThat(m.getFrom()).isEqualTo("no-responder@vivero.co");
            assertThat(m.getSubject()).isEqualTo("Cambió el estado de una de tus plantas");
            assertThat(m.getText()).isEqualTo("La planta «Pepa» pasó de SALUDABLE a CRITICO.");
            assertThat(m.getCc()).isNull();
            assertThat(m.getBcc()).isNull();
        });
    }

    @Test
    void unaCuentaQueYaNoExisteSeOmiteSinFallar() {
        notificador.avisar(new Aviso(Set.of(ana.id(), UUID.randomUUID()), TipoDeAviso.ACCESO_PERDIDO, "perdiste"));

        assertThat(enviados).singleElement().satisfies(m -> assertThat(m.getTo()).containsExactly("ana@vivero.co"));
    }

    @Test
    void laInvitacionAUnaDireccionSinCuentaLlevaElEnlaceDelFrontYLaCaducidad() {
        notificador.avisar(Aviso.aCorreos(Set.of("nueva@vivero.co"), TipoDeAviso.INVITACION,
                "Ana te invitó a ver su planta «Pepa».", Map.of(Aviso.TOKEN, "tok_en-123")));

        assertThat(enviados).singleElement().satisfies(m -> {
            assertThat(m.getTo()).containsExactly("nueva@vivero.co");
            assertThat(m.getSubject()).contains("invitaron");
            assertThat(m.getText()).contains("Ana te invitó").contains("http://front.local/invitacion?token=tok_en-123")
                    .contains("una sola vez").contains("7 días");
        });
    }

    @Test
    void elRestablecimientoLlevaElEnlaceConElTokenYSuCaducidadEnMinutos() {
        notificador.avisar(new Aviso(Set.of(ana.id()), TipoDeAviso.RESTABLECIMIENTO, "Pediste restablecer.",
                Map.of(Aviso.TOKEN, "abc")));

        assertThat(enviados).singleElement().satisfies(m -> {
            assertThat(m.getTo()).containsExactly("ana@vivero.co");
            assertThat(m.getText()).contains("http://front.local/restablecer?token=abc").contains("60 minutos");
        });
    }

    @Test
    void elTokenSeCodificaParaQueNoRompaLaUrl() {
        assertThat(enlaces.invitacion("a b&c")).isEqualTo("http://front.local/invitacion?token=a+b%26c");
    }

    @Test
    void otrosTiposNoLlevanEnlaceAunqueTraigaDatos() {
        notificador.avisar(new Aviso(Set.of(ana.id()), TipoDeAviso.SOLICITUD_RESUELTA, "Tu solicitud fue aprobada.",
                Map.of(Aviso.SOLICITUD_ID, "x")));

        assertThat(enviados).singleElement().satisfies(m -> assertThat(m.getText()).doesNotContain("http"));
    }

    @Test
    void siElEnvioFallaNoLanzaYSigueConElSiguiente() {
        List<String> intentos = new ArrayList<>();
        MailSender roto = new MailSender() {
            @Override
            public void send(SimpleMailMessage mensaje) {
                intentos.add(mensaje.getTo()[0]);
                if (mensaje.getTo()[0].startsWith("ana")) {
                    throw new MailSendException("SMTP caído");
                }
                enviados.add(mensaje);
            }

            @Override
            public void send(SimpleMailMessage... mensajes) {
                for (SimpleMailMessage m : mensajes) {
                    send(m);
                }
            }
        };
        NotificadorPorCorreo conFallo = new NotificadorPorCorreo(roto, usuarios, enlaces, "x@vivero.co", false);

        conFallo.avisar(new Aviso(Set.of(ana.id(), beto.id()), TipoDeAviso.CAMBIO_DE_ESTADO, "cambió"));

        assertThat(intentos).containsExactlyInAnyOrder("ana@vivero.co", "beto@vivero.co");
        assertThat(enviados).singleElement().satisfies(m -> assertThat(m.getTo()).containsExactly("beto@vivero.co"));
    }

    @Test
    void enModoAsincronoElEnvioSaleEnOtroHiloYElLlamadorNoEspera() {
        List<String> hilos = new ArrayList<>();
        MailSender lento = new MailSender() {
            @Override
            public void send(SimpleMailMessage mensaje) {
                hilos.add(Thread.currentThread().getName());
                enviados.add(mensaje);
            }

            @Override
            public void send(SimpleMailMessage... mensajes) {
                for (SimpleMailMessage m : mensajes) {
                    send(m);
                }
            }
        };
        NotificadorPorCorreo asincrono = new NotificadorPorCorreo(lento, usuarios, enlaces, "x@vivero.co", true);

        asincrono.avisar(new Aviso(Set.of(ana.id()), TipoDeAviso.CAMBIO_DE_ESTADO, "cambió"));

        await().untilAsserted(() -> assertThat(enviados).hasSize(1));
        assertThat(hilos).singleElement().isNotEqualTo(Thread.currentThread().getName()).isEqualTo("correo-de-avisos");
        asincrono.cerrar();
    }

    @Test
    void cadaTipoTieneAsunto() {
        for (TipoDeAviso tipo : TipoDeAviso.values()) {
            assertThat(NotificadorPorCorreo.asuntoDe(tipo)).isNotBlank();
        }
    }
}
