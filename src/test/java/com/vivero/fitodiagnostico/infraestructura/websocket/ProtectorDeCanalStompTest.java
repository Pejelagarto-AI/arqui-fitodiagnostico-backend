package com.vivero.fitodiagnostico.infraestructura.websocket;

import com.vivero.fitodiagnostico.aplicacion.ServicioCuentas;
import com.vivero.fitodiagnostico.aplicacion.ServicioPlantas;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.UsuarioNoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** La puerta del WebSocket (D59): autenticar en el CONNECT y autorizar cada SUBSCRIBE, frame por frame, sin red. */
class ProtectorDeCanalStompTest {

    private final UUID yo = UUID.randomUUID();
    private final UUID planta = UUID.randomUUID();
    private ServicioCuentas cuentas;
    private ServicioPlantas plantas;
    private ProtectorDeCanalStomp protector;

    @BeforeEach
    void preparar() {
        cuentas = mock(ServicioCuentas.class);
        plantas = mock(ServicioPlantas.class);
        JwtDecoder decodificador = token -> {
            if (!token.startsWith("valido:")) {
                throw new BadJwtException("firma inválida");
            }
            return Jwt.withTokenValue(token).header("alg", "none").subject(token.substring("valido:".length())).build();
        };
        DefaultListableBeanFactory fabrica = new DefaultListableBeanFactory();
        fabrica.registerSingleton("cuentas", cuentas);
        fabrica.registerSingleton("plantas", plantas);
        protector = new ProtectorDeCanalStomp(decodificador, fabrica.getBeanProvider(ServicioCuentas.class),
                fabrica.getBeanProvider(ServicioPlantas.class));
    }

    private static StompHeaderAccessor frame(StompCommand comando) {
        StompHeaderAccessor accesor = StompHeaderAccessor.create(comando);
        accesor.setLeaveMutable(true);
        return accesor;
    }

    private Message<?> enviar(StompHeaderAccessor accesor) {
        return protector.preSend(MessageBuilder.createMessage(new byte[0], accesor.getMessageHeaders()), null);
    }

    private StompHeaderAccessor conectado() {
        StompHeaderAccessor conexion = frame(StompCommand.CONNECT);
        conexion.addNativeHeader("Authorization", "Bearer valido:" + yo);
        return conexion;
    }

    private StompHeaderAccessor suscripcion(String destino, boolean autenticado) {
        StompHeaderAccessor suscribir = frame(StompCommand.SUBSCRIBE);
        suscribir.setDestination(destino);
        suscribir.setSubscriptionId("sub-0");
        if (autenticado) {
            suscribir.setUser(new ProtectorDeCanalStomp.Sesion(yo.toString()));
        }
        return suscribir;
    }

    // ---- CONNECT: autenticación con el mismo JWT ----

    @Test
    void unTokenValidoDeUnaCuentaViveFijaLaIdentidadDeLaSesionConElIdDelUsuario() {
        Message<?> mensaje = enviar(conectado());

        assertThat(StompHeaderAccessor.wrap(mensaje).getUser()).isNotNull();
        assertThat(StompHeaderAccessor.wrap(mensaje).getUser().getName()).isEqualTo(yo.toString());
        verify(cuentas).consultar(yo);
    }

    @Test
    void sinCabeceraAuthorizationElConnectSeRechaza() {
        assertThatThrownBy(() -> enviar(frame(StompCommand.CONNECT))).isInstanceOf(MessagingException.class);
    }

    @Test
    void conUnEsquemaQueNoEsBearerSeRechaza() {
        StompHeaderAccessor conexion = frame(StompCommand.CONNECT);
        conexion.addNativeHeader("Authorization", "Basic abc");

        assertThatThrownBy(() -> enviar(conexion)).isInstanceOf(MessagingException.class);
    }

    @Test
    void unTokenInvalidoManipuladoOCaducadoSeRechazaSinConsultarCuentas() {
        StompHeaderAccessor conexion = frame(StompCommand.CONNECT);
        conexion.addNativeHeader("Authorization", "Bearer basura");

        assertThatThrownBy(() -> enviar(conexion)).isInstanceOf(MessagingException.class)
                .hasMessageContaining("no es válido");
        verifyNoInteractions(cuentas);
    }

    @Test
    void unTokenValidoDeUnaCuentaEliminadaSeRechaza() {
        doThrow(new UsuarioNoEncontradoException(yo)).when(cuentas).consultar(yo);

        assertThatThrownBy(() -> enviar(conectado())).isInstanceOf(MessagingException.class);
    }

    @Test
    void unSubjectQueNoEsUnUuidSeRechaza() {
        StompHeaderAccessor conexion = frame(StompCommand.CONNECT);
        conexion.addNativeHeader("Authorization", "Bearer valido:no-es-uuid");

        assertThatThrownBy(() -> enviar(conexion)).isInstanceOf(MessagingException.class);
    }

    // ---- SUBSCRIBE: autorización por planta ----

    @Test
    void sePuedeSuscribirAlCanalGeneralDeAvisos() {
        enviar(suscripcion("/user/queue/avisos", true));

        verifyNoInteractions(plantas);
    }

    @Test
    void sePuedeSuscribirAUnaPlantaQueSePuedeLeer() {
        enviar(suscripcion("/user/queue/plantas/" + planta, true));

        verify(plantas).detalle(yo, planta);
    }

    @Test
    void unaPlantaAjenaOInexistenteSeRechazaIgual() {
        doThrow(new PlantaNoEncontradaException(planta)).when(plantas).detalle(any(), any());

        assertThatThrownBy(() -> enviar(suscripcion("/user/queue/plantas/" + planta, true)))
                .isInstanceOf(MessagingException.class).hasMessageContaining("no tienes acceso");
    }

    @Test
    void sinHaberseAutenticadoNoSePuedeSuscribirANada() {
        assertThatThrownBy(() -> enviar(suscripcion("/user/queue/avisos", false))).isInstanceOf(MessagingException.class);
        verifyNoInteractions(plantas);
    }

    @Test
    void cualquierOtroDestinoSeRechazaAunqueSeaAutenticado() {
        for (String destino : new String[]{"/queue/avisos", "/topic/plantas/" + planta, "/user/queue/otra",
                "/user/queue/plantas/no-es-un-uuid", "/user/queue/plantas/" + planta + "/x",
                "/user/queue/plantas/" + "z".repeat(36)}) {
            assertThatThrownBy(() -> enviar(suscripcion(destino, true))).as(destino).isInstanceOf(MessagingException.class);
        }
    }

    // ---- SEND: sentido único ----

    @Test
    void elClienteNuncaPuedeEnviar() {
        StompHeaderAccessor enviarFrame = frame(StompCommand.SEND);
        enviarFrame.setDestination("/queue/plantas/" + planta);
        enviarFrame.setUser(new ProtectorDeCanalStomp.Sesion(yo.toString()));

        assertThatThrownBy(() -> enviar(enviarFrame)).isInstanceOf(MessagingException.class)
                .hasMessageContaining("solo lectura");
    }

    @Test
    void losDemasFramesPasanSinTocarNada() {
        Message<?> mensaje = enviar(frame(StompCommand.DISCONNECT));

        assertThat(mensaje).isNotNull();
        verifyNoInteractions(cuentas, plantas);
    }
}
