package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.AceptarInvitacionPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.CompartirPlantaPort;
import com.vivero.fitodiagnostico.aplicacion.ServicioInvitaciones;
import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dominio.excepcion.CorreoYaRegistradoException;
import com.vivero.fitodiagnostico.dominio.excepcion.InvitacionNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.InvitacionNoVigenteException;
import com.vivero.fitodiagnostico.dominio.excepcion.InvitadoNoEncontradoException;
import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.Credencial;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoInvitacion;
import com.vivero.fitodiagnostico.dominio.modelo.Invitacion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rebanada web de compartir plantas (RF-E1 a RF-E6): forma de peticiones y
 * respuestas, validación en el borde, que el token solo viaje donde debe y la
 * traducción de cada excepción. Quién puede qué lo decide la aplicación
 * ({@code ServicioInvitacionesTest}) y el recorrido completo, la integración.
 */
@WebMvcTest(InvitacionesController.class)
@Import({SeguridadDePruebas.class, InvitacionesControllerTest.Reloj.class})
class InvitacionesControllerTest {

    private static final UUID YO = UUID.fromString("7d4f2a56-9d0a-4a58-9d11-0a3a8a4b8f10");
    private static final UUID PLANTA = UUID.fromString("0f6c1d3e-5b0a-4c55-8a53-1c1d2d3e4f50");
    private static final UUID INVITACION = UUID.fromString("5e8d2f10-3c3b-4b6c-9a07-4c6c7b3d9e11");
    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock reloj() {
            return Clock.fixed(AHORA, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioInvitaciones servicio;

    private static RequestPostProcessor conSesion() {
        return jwt().jwt(j -> j.subject(YO.toString())).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private static Planta pepa(UUID dueno) {
        return new Planta(PLANTA, dueno, "Pepa", Muestras.especie("helecho"), Instant.parse("2026-10-01T12:00:00Z"));
    }

    private static Invitacion invitacion(String correo) {
        return Invitacion.nueva(PLANTA, "huella-que-no-debe-salir", AHORA, correo);
    }

    // ---- RF-E1 ----

    @Test
    void generarDevuelve201ConElTokenYElEnlaceDelFrontUnicaVezQueSeEntregan() throws Exception {
        Invitacion creada = invitacion(null);
        when(servicio.generar(YO, PLANTA, null)).thenReturn(new CompartirPlantaPort.InvitacionGenerada(creada, "tok_en-1"));

        mockMvc.perform(post("/api/v1/plantas/{id}/invitaciones", PLANTA).with(conSesion()))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.token").value("tok_en-1"))
                .andExpect(jsonPath("$.enlace").value("http://localhost:5500/invitacion?token=tok_en-1"))
                .andExpect(jsonPath("$.plantaId").value(PLANTA.toString()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.expiraEn").value("2026-10-21T15:00:00Z"))
                .andExpect(content().string(not(containsString("huella-que-no-debe-salir"))));
    }

    @Test
    void generarConCorreoLoPasaAlServicioNormalizadoPorElDominioYValidaLaForma() throws Exception {
        when(servicio.generar(YO, PLANTA, "ana@vivero.co"))
                .thenReturn(new CompartirPlantaPort.InvitacionGenerada(invitacion("ana@vivero.co"), "t"));

        mockMvc.perform(post("/api/v1/plantas/{id}/invitaciones", PLANTA).with(conSesion())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"ana@vivero.co\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correoDestino").value("ana@vivero.co"));
        mockMvc.perform(post("/api/v1/plantas/{id}/invitaciones", PLANTA).with(conSesion())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"no-es-correo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.detalle.campo").value("correo"));
    }

    @Test
    void generarTraduceInvitadoAjenoYAutoInvitacion() throws Exception {
        when(servicio.generar(any(), any(), any()))
                .thenThrow(new OperacionNoPermitidaException("solo el dueño de la planta puede hacer esta operación"))
                .thenThrow(new PlantaNoEncontradaException(PLANTA));

        mockMvc.perform(post("/api/v1/plantas/{id}/invitaciones", PLANTA).with(conSesion()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("OPERACION_NO_PERMITIDA"));
        mockMvc.perform(post("/api/v1/plantas/{id}/invitaciones", PLANTA).with(conSesion()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PLANTA_NO_ENCONTRADA"));
    }

    // ---- listar y revocar invitaciones ----

    @Test
    void listarLasPendientesNoIncluyeNingunToken() throws Exception {
        when(servicio.pendientes(YO, PLANTA)).thenReturn(List.of(invitacion("ana@vivero.co")));

        mockMvc.perform(get("/api/v1/plantas/{id}/invitaciones", PLANTA).with(conSesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].correoDestino").value("ana@vivero.co"))
                .andExpect(jsonPath("$[0].token").doesNotExist())
                .andExpect(content().string(not(containsString("huella"))));
    }

    @Test
    void revocarUnaInvitacionDa204YTraduceLosErrores() throws Exception {
        mockMvc.perform(delete("/api/v1/plantas/{p}/invitaciones/{i}", PLANTA, INVITACION).with(conSesion()))
                .andExpect(status().isNoContent());
        verify(servicio).revocar(YO, PLANTA, INVITACION);

        doThrow(new InvitacionNoEncontradaException()).doThrow(new InvitacionNoVigenteException(EstadoInvitacion.ACEPTADA))
                .when(servicio).revocar(any(), any(), any());
        mockMvc.perform(delete("/api/v1/plantas/{p}/invitaciones/{i}", PLANTA, INVITACION).with(conSesion()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("INVITACION_NO_ENCONTRADA"));
        mockMvc.perform(delete("/api/v1/plantas/{p}/invitaciones/{i}", PLANTA, INVITACION).with(conSesion()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("INVITACION_YA_ACEPTADA"));
    }

    // ---- invitados ----

    @Test
    void listarInvitadosMuestraNombreCorreoYDesdeCuando() throws Exception {
        Usuario beto = new Usuario(UUID.randomUUID(), "Beto", "beto@vivero.co", new Credencial("h"), Set.of());
        when(servicio.invitados(YO, PLANTA)).thenReturn(List.of(new CompartirPlantaPort.Invitado(beto, AHORA)));

        mockMvc.perform(get("/api/v1/plantas/{id}/invitados", PLANTA).with(conSesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(beto.id().toString()))
                .andExpect(jsonPath("$[0].nombre").value("Beto"))
                .andExpect(jsonPath("$[0].correo").value("beto@vivero.co"))
                .andExpect(jsonPath("$[0].otorgadoEn").value("2026-10-14T15:00:00Z"))
                .andExpect(content().string(not(containsString("credencial"))));
    }

    @Test
    void revocarElAccesoDeUnInvitadoDa204YUnoQueNoEsInvitadoDa404() throws Exception {
        UUID beto = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/plantas/{p}/invitados/{i}", PLANTA, beto).with(conSesion()))
                .andExpect(status().isNoContent());
        verify(servicio).revocarAcceso(YO, PLANTA, beto);

        doThrow(new InvitadoNoEncontradoException(beto)).when(servicio).revocarAcceso(any(), any(), any());
        mockMvc.perform(delete("/api/v1/plantas/{p}/invitados/{i}", PLANTA, beto).with(conSesion()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("INVITADO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.detalle.id").value(beto.toString()));
    }

    // ---- RF-E5, RF-E6 ----

    @Test
    void lasCompartidasConmigoSeListanEnUnaRutaLiteralQueNoChocaConElDetalle() throws Exception {
        when(servicio.compartidasConmigo(YO)).thenReturn(List.of(pepa(UUID.randomUUID())));

        mockMvc.perform(get("/api/v1/plantas/compartidas").with(conSesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].apodo").value("Pepa"))
                .andExpect(jsonPath("$[0].esDueno").value(false));
    }

    @Test
    void renunciarDa204YUnaPlantaNoCompartidaDa404() throws Exception {
        mockMvc.perform(delete("/api/v1/plantas/compartidas/{id}", PLANTA).with(conSesion()))
                .andExpect(status().isNoContent());
        verify(servicio).renunciar(YO, PLANTA);

        doThrow(new PlantaNoEncontradaException(PLANTA)).when(servicio).renunciar(any(), any());
        mockMvc.perform(delete("/api/v1/plantas/compartidas/{id}", PLANTA).with(conSesion()))
                .andExpect(status().isNotFound());
    }

    // ---- RF-E2 ----

    @Test
    void aceptarConSesionDevuelveLaPlantaYElTokenViajaEnElCuerpoNoEnLaRuta() throws Exception {
        when(servicio.aceptar(YO, "tok-1")).thenReturn(pepa(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/invitaciones/aceptar").with(conSesion())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"tok-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PLANTA.toString()))
                .andExpect(jsonPath("$.esDueno").value(false));
    }

    @Test
    void aceptarValidaElCuerpoSinLlamarAlServicio() throws Exception {
        for (String cuerpo : new String[]{"{}", "{\"token\":\"  \"}", "{\"token\":\"" + "x".repeat(201) + "\"}", "{\"token\":"}) {
            mockMvc.perform(post("/api/v1/invitaciones/aceptar").with(conSesion())
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void cadaEstadoDeUnEnlaceInutilizableTieneSuPropioCodigoYUn410() throws Exception {
        when(servicio.aceptar(any(), anyString()))
                .thenThrow(new InvitacionNoVigenteException(EstadoInvitacion.ACEPTADA))
                .thenThrow(new InvitacionNoVigenteException(EstadoInvitacion.CADUCADA))
                .thenThrow(new InvitacionNoVigenteException(EstadoInvitacion.REVOCADA))
                .thenThrow(new InvitacionNoEncontradaException())
                .thenThrow(new OperacionNoPermitidaException("no puedes aceptar una invitación a tu propia planta"));
        String[] codigos = {"INVITACION_YA_ACEPTADA", "INVITACION_CADUCADA", "INVITACION_REVOCADA"};

        for (String codigo : codigos) {
            mockMvc.perform(post("/api/v1/invitaciones/aceptar").with(conSesion())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"t\"}"))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.error").value(codigo))
                    .andExpect(jsonPath("$.mensaje").isNotEmpty());
        }
        mockMvc.perform(post("/api/v1/invitaciones/aceptar").with(conSesion())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"t\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("INVITACION_NO_ENCONTRADA"));
        mockMvc.perform(post("/api/v1/invitaciones/aceptar").with(conSesion())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"t\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("OPERACION_NO_PERMITIDA"));
    }

    @Test
    void aceptarRegistrandoseEsPublicoDevuelve201ConLaCuentaYLaPlantaSinCredencial() throws Exception {
        Usuario carla = new Usuario(UUID.randomUUID(), "Carla", "carla@vivero.co", new Credencial("hash-secreto"), Set.of());
        when(servicio.registrarYAceptar("tok-1", "Carla", "carla@vivero.co", "clave-larga-1"))
                .thenReturn(new AceptarInvitacionPort.AceptacionConRegistro(carla, pepa(UUID.randomUUID())));

        mockMvc.perform(post("/api/v1/invitaciones/aceptar-con-registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"tok-1\",\"nombre\":\"Carla\",\"correo\":\"carla@vivero.co\","
                                + "\"contrasena\":\"clave-larga-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.correo").value("carla@vivero.co"))
                .andExpect(jsonPath("$.usuario.roles[0]").value("CLIENTE"))
                .andExpect(jsonPath("$.planta.id").value(PLANTA.toString()))
                .andExpect(jsonPath("$.planta.esDueno").value(false))
                .andExpect(content().string(not(containsString("hash-secreto"))));
    }

    @Test
    void aceptarRegistrandoseValidaLaFormaYTraduceElCorreoDuplicado() throws Exception {
        mockMvc.perform(post("/api/v1/invitaciones/aceptar-con-registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"t\",\"nombre\":\"C\",\"correo\":\"c@v.co\",\"contrasena\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.campo").value("contrasena"));
        verifyNoInteractions(servicio);

        when(servicio.registrarYAceptar(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new CorreoYaRegistradoException("c@v.co"));
        mockMvc.perform(post("/api/v1/invitaciones/aceptar-con-registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"t\",\"nombre\":\"C\",\"correo\":\"c@v.co\",\"contrasena\":\"clave-larga-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CORREO_YA_REGISTRADO"));
    }

    @Test
    void generarSinCuerpoLlamaAlServicioSinCorreo() throws Exception {
        when(servicio.generar(YO, PLANTA, null)).thenReturn(new CompartirPlantaPort.InvitacionGenerada(invitacion(null), "t"));

        mockMvc.perform(post("/api/v1/plantas/{id}/invitaciones", PLANTA).with(conSesion())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/plantas/" + PLANTA + "/invitaciones"));
        verify(servicio).generar(YO, PLANTA, null);
    }
}
