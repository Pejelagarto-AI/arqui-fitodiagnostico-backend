package com.vivero.fitodiagnostico;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La vista en vivo (RF-G2, D59) con un cliente STOMP de verdad contra el servidor
 * en un puerto real: se autentica con el MISMO JWT, se suscribe, recibe DIAGNOSTICO_NUEVO
 * y CAMBIO_DE_ESTADO, y se le rechaza todo lo que no le toca. Las peticiones REST van
 * por MockMvc contra el mismo contexto (y la misma base).
 *
 * <p>Incluye el recorrido de punta a punta del bloque: el dueño invita, el visitante se
 * registra y acepta, el dueño mide con cambio de estado, el invitado se entera por correo
 * y por WebSocket, el dueño revoca y el invitado recibe ACCESO_PERDIDO.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VistaEnVivoIntegracionTest extends BaseDeIntegracionDeCompartir {

    private static final Duration ESPERA = Duration.ofSeconds(5);
    private static final Duration SILENCIO = Duration.ofMillis(700);

    @LocalServerPort
    private int puerto;

    @Autowired
    private SimpMessageSendingOperations mensajeria;

    private final List<Cliente> abiertos = new ArrayList<>();

    @AfterEach
    void cerrarClientes() {
        abiertos.forEach(c -> {
            if (c.sesion.isConnected()) {
                c.sesion.disconnect();
            }
            c.cliente.stop();
        });
    }

    /** Un navegador simulado: una sesión STOMP con colas de lo que recibe y de los errores del servidor. */
    private final class Cliente {
        final String usuarioId;
        final WebSocketStompClient cliente;
        final StompSession sesion;
        final BlockingQueue<Map<String, Object>> mensajes = new LinkedBlockingQueue<>();
        final BlockingQueue<String> errores = new LinkedBlockingQueue<>();

        Cliente(String usuarioId, WebSocketStompClient cliente, StompSession sesion) {
            this.usuarioId = usuarioId;
            this.cliente = cliente;
            this.sesion = sesion;
        }

        /** Suscribe SIN esperar confirmación. */
        void suscribirSinEsperar(String destino) {
            sesion.subscribe(destino, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders cabeceras) {
                    return Map.class;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
                    mensajes.add((Map<String, Object>) cuerpo);
                }
            });
        }

        /**
         * Suscribe y espera a que el broker la haya registrado: el broker simple no manda RECEIPT, así
         * que se le envía una SONDA por el mismo destino hasta que llegue. Solo entonces es seguro
         * medir (si no, el mensaje saldría antes de que alguien escuchara). Las sondas se descartan.
         */
        void suscribir(String destino) throws InterruptedException {
            suscribirSinEsperar(destino);
            String cola = destino.substring("/user".length());
            long limite = System.nanoTime() + ESPERA.toNanos();
            while (System.nanoTime() < limite) {
                mensajeria.convertAndSendToUser(usuarioId, cola, Map.of("tipo", "SONDA"));
                Map<String, Object> recibido = mensajes.poll(150, TimeUnit.MILLISECONDS);
                if (recibido != null && "SONDA".equals(recibido.get("tipo"))) {
                    Thread.sleep(300);   // deja llegar las sondas que ya iban en camino
                    mensajes.removeIf(m -> "SONDA".equals(m.get("tipo")));
                    return;
                }
            }
            throw new IllegalStateException("la suscripción a " + destino + " no se confirmó; errores: " + errores);
        }

        /** Intenta suscribirse a algo que se espera rechazado: devuelve cuando llegó el ERROR del servidor. */
        void suscribirYEsperarRechazo(String destino) {
            suscribirSinEsperar(destino);
            await().atMost(ESPERA).until(() -> !errores.isEmpty());
        }

        Map<String, Object> siguiente() throws InterruptedException {
            Map<String, Object> mensaje = mensajes.poll(ESPERA.toSeconds(), TimeUnit.SECONDS);
            assertThat(mensaje).as("se esperaba un mensaje en vivo y no llegó").isNotNull();
            return mensaje;
        }

        void afirmarSilencio() throws InterruptedException {
            assertThat(mensajes.poll(SILENCIO.toMillis(), TimeUnit.MILLISECONDS)).as("no debía llegar nada").isNull();
        }
    }

    private Cliente conectar(String token) throws Exception {
        return conectar(token, "Bearer " + token, new WebSocketHttpHeaders());
    }

    private Cliente conectar(String token, String autorizacion, WebSocketHttpHeaders handshake) throws Exception {
        String usuario = usuarioDe(token);
        WebSocketStompClient cliente = new WebSocketStompClient(new StandardWebSocketClient());
        cliente.setTaskScheduler(new org.springframework.scheduling.concurrent.ConcurrentTaskScheduler());
        cliente.setMessageConverter(new CompositeMessageConverter(
                List.of(new MappingJackson2MessageConverter(), new StringMessageConverter())));
        StompHeaders conexion = new StompHeaders();
        if (autorizacion != null) {
            conexion.add("Authorization", autorizacion);
        }
        BlockingQueue<String> errores = new LinkedBlockingQueue<>();
        StompSession sesion = cliente.connectAsync("ws://localhost:" + puerto + "/ws", handshake, conexion,
                new StompSessionHandlerAdapter() {
                    @Override
                    public Type getPayloadType(StompHeaders cabeceras) {
                        return String.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders cabeceras, Object cuerpo) {
                        errores.add(cabeceras.getFirst("message") + " | " + cuerpo);
                    }
                }).get(ESPERA.toSeconds(), TimeUnit.SECONDS);
        Cliente nuevo = new Cliente(usuario, cliente, sesion);
        abiertos.add(nuevo);
        // Los errores de la sesión se reenvían a la cola del cliente.
        Thread.startVirtualThread(() -> {
            try {
                String error;
                while ((error = errores.poll(30, TimeUnit.SECONDS)) != null) {
                    nuevo.errores.add(error);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        return nuevo;
    }

    /** El id del usuario dueño del token (claim sub), para dirigir las sondas. */
    private static String usuarioDe(String token) {
        try {
            String carga = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]));
            return com.jayway.jsonpath.JsonPath.read(carga, "$.sub");
        } catch (RuntimeException e) {
            return "desconocido";   // tokens de prueba inválidos: no habrá conexión de todos modos
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> datos(Map<String, Object> mensaje) {
        return (Map<String, Object>) mensaje.get("datos");
    }

    // ---- el recorrido pedido, de punta a punta ----

    @Test
    void duenoInvitaVisitanteSeRegistraYAceptaDuenoMideConCambioDeEstadoInvitadoSeEnteraPorCorreoYWebSocketDuenoRevocaYInvitadoRecibeAccesoPerdido()
            throws Exception {
        Cuenta dueno = registrar("Dueña");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        String visitante = "compartir-visita-" + UUID.randomUUID().toString().substring(0, 6) + "@vivero.co";

        // 1. El dueño invita por correo.
        var invitacion = mockMvc.perform(json(con(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/plantas/" + planta + "/invitaciones"), dueno.token()),
                        "{\"correo\":\"" + visitante + "\"}")).andExpect(status().isCreated());
        String token = leer(invitacion, "$.token");
        assertThat(tokenDelEnlace(correosDe(visitante, "invitaron").get(0))).isEqualTo(token);

        // 2. El visitante sin cuenta se registra y acepta en el mismo flujo; después inicia sesión.
        mockMvc.perform(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/invitaciones/aceptar-con-registro"),
                        "{\"token\":\"" + token + "\",\"nombre\":\"Visita\",\"correo\":\"" + visitante
                                + "\",\"contrasena\":\"" + CLAVE + "\"}")).andExpect(status().isCreated());
        Cuenta invitado = iniciarSesion(visitante, CLAVE);

        // 3. Dueño e invitado abren la vista en vivo de la planta; el invitado también escucha sus avisos.
        Cliente delDueno = conectar(dueno.token());
        delDueno.suscribir("/user/queue/plantas/" + planta);
        Cliente delInvitado = conectar(invitado.token());
        delInvitado.suscribir("/user/queue/plantas/" + planta);
        Cliente avisosDelInvitado = conectar(invitado.token());
        avisosDelInvitado.suscribir("/user/queue/avisos");
        correo.limpiar();

        // 4. El dueño mide: la primera no es un cambio; la segunda, SALUDABLE -> CRITICO.
        medir(dueno, planta, SALUDABLE).andExpect(status().isCreated());
        medir(dueno, planta, CRITICA).andExpect(status().isCreated());

        for (Cliente cliente : List.of(delInvitado, delDueno)) {
            Map<String, Object> primero = cliente.siguiente();
            assertThat(primero).containsEntry("tipo", "DIAGNOSTICO_NUEVO");
            assertThat(datos(primero)).containsEntry("plantaId", planta).containsEntry("estado", "SALUDABLE")
                    .containsEntry("origen", "MANUAL");
            assertThat(cliente.siguiente()).containsEntry("tipo", "DIAGNOSTICO_NUEVO");   // la segunda medición
            Map<String, Object> cambio = cliente.siguiente();
            assertThat(cambio).containsEntry("tipo", "CAMBIO_DE_ESTADO");
            assertThat((String) cambio.get("mensaje")).contains("Pepa").contains("SALUDABLE").contains("CRITICO");
            cliente.afirmarSilencio();
        }
        // ... y por correo (un solo cambio de estado, a los dos, sin avisos de diagnóstico nuevo por correo).
        assertThat(correosDe(invitado.correo(), "Cambió el estado")).hasSize(1);
        assertThat(correosDe(dueno.correo(), "Cambió el estado")).hasSize(1);
        assertThat(correo.enviados()).hasSize(2);

        // 5. El dueño revoca: el invitado recibe ACCESO_PERDIDO por WebSocket y por correo.
        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/invitados/" + invitado.id()), dueno.token()))
                .andExpect(status().isNoContent());
        Map<String, Object> perdido = avisosDelInvitado.siguiente();
        assertThat(perdido).containsEntry("tipo", "ACCESO_PERDIDO");
        assertThat(datos(perdido)).containsEntry("plantaId", planta);
        assertThat(correosDe(invitado.correo(), "Perdiste el acceso")).hasSize(1);

        // 6. Ya no recibe más: la suscripción que abrió sigue ahí, pero la entrega es por destinatario.
        medir(dueno, planta, SALUDABLE).andExpect(status().isCreated());
        assertThat(delDueno.siguiente()).containsEntry("tipo", "DIAGNOSTICO_NUEVO");
        delInvitado.afirmarSilencio();
        mockMvc.perform(con(get("/api/v1/plantas/" + planta), invitado.token())).andExpect(status().isNotFound());
    }

    // ---- autenticación del WebSocket ----

    @Test
    void sinTokenOConTokenInvalidoLaConexionSeRechaza() {
        for (String autorizacion : new String[]{null, "Bearer basura", "Basic abc", "Bearer "}) {
            // Si por error conectara, no habría excepción y assertThatThrownBy fallaría: nadie sin identidad entra.
            assertThatThrownBy(() -> conectar("x", autorizacion, new WebSocketHttpHeaders()))
                    .as("Authorization = %s", autorizacion)
                    .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
        }
    }

    @Test
    void unTokenValidoDeUnaCuentaEliminadaSeRechaza() throws Exception {
        Cuenta ana = registrar("Ana");
        mockMvc.perform(con(delete("/api/v1/cuenta"), ana.token())).andExpect(status().isNoContent());

        assertThatThrownBy(() -> conectar(ana.token())).isInstanceOfAny(ExecutionException.class, TimeoutException.class);
    }

    @Test
    void unOrigenQueNoEstaEnLaListaDeCorsSeRechazaEnElHandshake() throws Exception {
        Cuenta ana = registrar("Ana");
        WebSocketHttpHeaders ajeno = new WebSocketHttpHeaders();
        ajeno.setOrigin("http://sitio-malicioso.example");

        assertThatThrownBy(() -> conectar(ana.token(), "Bearer " + ana.token(), ajeno))
                .isInstanceOfAny(ExecutionException.class, TimeoutException.class);

        WebSocketHttpHeaders permitido = new WebSocketHttpHeaders();
        permitido.setOrigin("http://localhost:5500");
        assertThat(conectar(ana.token(), "Bearer " + ana.token(), permitido).sesion.isConnected()).isTrue();
    }

    // ---- autorización de las suscripciones ----

    @Test
    void sePuedeSuscribirUnDuenoUnInvitadoPeroNoUnAjenoNiUnAdministradorYNadaDeLoQueNoEsPublico() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta invitado = registrar("Beto");
        Cuenta ajeno = registrar("Ajeno");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        compartir(dueno, planta, invitado);

        conectar(dueno.token()).suscribir("/user/queue/plantas/" + planta);
        conectar(invitado.token()).suscribir("/user/queue/plantas/" + planta);

        for (String destino : new String[]{
                "/user/queue/plantas/" + planta,                       // planta ajena
                "/user/queue/plantas/" + UUID.randomUUID(),            // planta inexistente: mismo rechazo
                "/queue/plantas/" + planta,                            // el destino interno, sin /user
                "/topic/plantas/" + planta, "/user/queue/otra-cosa"}) {
            Cliente intruso = conectar(ajeno.token());
            intruso.suscribirYEsperarRechazo(destino);   // llega el ERROR del servidor
            assertThat(intruso.errores.peek()).as(destino).isNotBlank();
        }
    }

    @Test
    void unAjenoRechazadoNoRecibeNadaAunqueElDuenoMida() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta ajeno = registrar("Ajeno");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        Cliente intruso = conectar(ajeno.token());
        intruso.suscribirYEsperarRechazo("/user/queue/plantas/" + planta);
        Cliente delDueno = conectar(dueno.token());
        delDueno.suscribir("/user/queue/plantas/" + planta);

        medir(dueno, planta, SALUDABLE).andExpect(status().isCreated());

        assertThat(delDueno.siguiente()).containsEntry("tipo", "DIAGNOSTICO_NUEVO");
        intruso.afirmarSilencio();
    }

    @Test
    void elAdministradorNoPuedeSuscribirseALaPlantaDeOtro() throws Exception {
        Cuenta dueno = registrar("Dueña");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        Cliente admin = conectar(tokenDelAdministrador());

        admin.suscribirYEsperarRechazo("/user/queue/plantas/" + planta);
        assertThat(admin.errores.peek()).contains("no tienes acceso");
    }

    @Test
    void elClienteNoPuedeEnviarMensajesPorElCanal() throws Exception {
        Cuenta dueno = registrar("Dueña");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        Cliente delDueno = conectar(dueno.token());
        delDueno.suscribir("/user/queue/plantas/" + planta);
        Cliente otro = conectar(dueno.token());
        otro.suscribir("/user/queue/avisos");

        // Intenta publicar directamente en el broker, haciéndose pasar por el servidor.
        Cliente tramposo = conectar(registrar("Tramposo").token());
        tramposo.sesion.send("/queue/plantas/" + planta, "{\"tipo\":\"FALSO\"}".getBytes());
        await().atMost(ESPERA).until(() -> !tramposo.errores.isEmpty());

        delDueno.afirmarSilencio();
        otro.afirmarSilencio();
    }

    // ---- otros avisos por la vista en vivo ----

    @Test
    void laSolicitudResueltaLlegaAlSolicitanteSuscritoASusAvisos() throws Exception {
        Cuenta ana = registrar("Ana");
        Cliente avisos = conectar(ana.token());
        avisos.suscribir("/user/queue/avisos");
        String id = leer(mockMvc.perform(json(con(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/solicitudes-especie"), ana.token()), "{\"nombre\":\"compartir-Calathea\"}"))
                .andExpect(status().isCreated()), "$.id");

        mockMvc.perform(json(con(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/admin/solicitudes-especie/" + id + "/rechazar"), tokenDelAdministrador()),
                        "{\"respuesta\":\"ya existe\"}")).andExpect(status().isOk());

        Map<String, Object> mensaje = avisos.siguiente();
        assertThat(mensaje).containsEntry("tipo", "SOLICITUD_RESUELTA");
        assertThat(datos(mensaje)).containsEntry("resolucion", "RECHAZADA").containsEntry("solicitudId", id);
    }

    @Test
    void ningunMensajeEnVivoLlevaUnToken() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta invitado = registrar("Beto");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        Cliente avisos = conectar(invitado.token());
        avisos.suscribir("/user/queue/avisos");
        compartir(dueno, planta, invitado);
        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/invitados/" + invitado.id()), dueno.token()))
                .andExpect(status().isNoContent());

        Map<String, Object> mensaje = avisos.siguiente();

        assertThat(mensaje.toString()).doesNotContain("token");
    }
}
