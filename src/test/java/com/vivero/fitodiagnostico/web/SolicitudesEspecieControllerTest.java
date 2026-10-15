package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioSolicitudes;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieYaExisteException;
import com.vivero.fitodiagnostico.dominio.excepcion.SolicitudNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.SolicitudYaResueltaException;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoSolicitud;
import com.vivero.fitodiagnostico.dominio.modelo.SolicitudEspecie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rebanada web de las solicitudes de especie (RF-H1 a RF-H5), la del cliente y la del administrador. */
@WebMvcTest({SolicitudesEspecieController.class, AdministracionSolicitudesController.class})
@Import(SeguridadDePruebas.class)
class SolicitudesEspecieControllerTest {

    private static final UUID YO = UUID.fromString("7d4f2a56-9d0a-4a58-9d11-0a3a8a4b8f10");
    private static final UUID SOLICITUD = UUID.fromString("9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d");
    private static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioSolicitudes servicio;

    private static RequestPostProcessor comoCliente() {
        return jwt().jwt(j -> j.subject(YO.toString())).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private static RequestPostProcessor comoAdministrador() {
        return jwt().jwt(j -> j.subject(YO.toString())).authorities(
                new SimpleGrantedAuthority("ROLE_CLIENTE"), new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    private static SolicitudEspecie pendiente() {
        return new SolicitudEspecie(SOLICITUD, YO, "Monstera", "no está", EstadoSolicitud.PENDIENTE, null, AHORA, null);
    }

    @Test
    void enviarDevuelve201ConLaSolicitudPendienteYElSolicitanteSaleDelToken() throws Exception {
        when(servicio.enviar(YO, "Monstera", "no está")).thenReturn(pendiente());

        mockMvc.perform(post("/api/v1/solicitudes-especie").with(comoCliente()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Monstera\",\"justificacion\":\"no está\","
                                + "\"solicitanteId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/solicitudes-especie"))
                .andExpect(jsonPath("$.id").value(SOLICITUD.toString()))
                .andExpect(jsonPath("$.nombre").value("Monstera"))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.respuesta").value((Object) null))
                .andExpect(jsonPath("$.solicitanteId").value(YO.toString()));
        verify(servicio).enviar(YO, "Monstera", "no está");
    }

    @Test
    void enviarValidaLaFormaEnElBorde() throws Exception {
        for (String cuerpo : new String[]{"{}", "{\"nombre\":\"  \"}", "{\"nombre\":\"" + "x".repeat(121) + "\"}",
                "{\"nombre\":\"Monstera\",\"justificacion\":\"" + "j".repeat(1001) + "\"}"}) {
            mockMvc.perform(post("/api/v1/solicitudes-especie").with(comoCliente())
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
        }
        verifyNoInteractions(servicio);
    }

    @Test
    void pedirUnaEspecieQueYaExisteDa409() throws Exception {
        when(servicio.enviar(any(), any(), any())).thenThrow(new EspecieYaExisteException("potos"));

        mockMvc.perform(post("/api/v1/solicitudes-especie").with(comoCliente()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"potos\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ESPECIE_YA_EXISTE"));
    }

    @Test
    void lasPropiasSeListanConSuEstadoYRespuesta() throws Exception {
        SolicitudEspecie rechazada = pendiente().rechazada("ya existe", AHORA.plusSeconds(60));
        when(servicio.deSolicitante(YO)).thenReturn(List.of(rechazada));

        mockMvc.perform(get("/api/v1/solicitudes-especie").with(comoCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("RECHAZADA"))
                .andExpect(jsonPath("$[0].respuesta").value("ya existe"))
                .andExpect(jsonPath("$[0].resueltaEn").value("2026-10-14T15:01:00Z"));
    }

    @Test
    void elAdministradorListaLasPendientes() throws Exception {
        when(servicio.pendientes()).thenReturn(List.of(pendiente()));

        mockMvc.perform(get("/api/v1/admin/solicitudes-especie").with(comoAdministrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
    }

    @Test
    void aprobarConYSinCuerpo() throws Exception {
        when(servicio.aprobar(SOLICITUD, "la agregamos")).thenReturn(pendiente().aprobada("la agregamos", AHORA));
        when(servicio.aprobar(SOLICITUD, null)).thenReturn(pendiente().aprobada(null, AHORA));

        mockMvc.perform(post("/api/v1/admin/solicitudes-especie/{id}/aprobar", SOLICITUD).with(comoAdministrador())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"respuesta\":\"la agregamos\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.respuesta").value("la agregamos"));
        mockMvc.perform(post("/api/v1/admin/solicitudes-especie/{id}/aprobar", SOLICITUD).with(comoAdministrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"));
        verify(servicio).aprobar(SOLICITUD, null);
    }

    @Test
    void rechazarSinRespuestaDa400PorqueElDominioLaExige() throws Exception {
        when(servicio.rechazar(SOLICITUD, null)).thenThrow(new IllegalArgumentException(
                "rechazar una solicitud requiere una respuesta con el motivo"));

        mockMvc.perform(post("/api/v1/admin/solicitudes-especie/{id}/rechazar", SOLICITUD).with(comoAdministrador()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.mensaje").value("rechazar una solicitud requiere una respuesta con el motivo"));
    }

    @Test
    void rechazarConMotivoDevuelveLaSolicitudRechazada() throws Exception {
        when(servicio.rechazar(SOLICITUD, "ya existe")).thenReturn(pendiente().rechazada("ya existe", AHORA));

        mockMvc.perform(post("/api/v1/admin/solicitudes-especie/{id}/rechazar", SOLICITUD).with(comoAdministrador())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"respuesta\":\"ya existe\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"));
    }

    @Test
    void unaYaResueltaDa409YUnaInexistenteDa404() throws Exception {
        doThrow(new SolicitudYaResueltaException(EstadoSolicitud.APROBADA)).when(servicio).aprobar(any(), any());
        doThrow(new SolicitudNoEncontradaException(SOLICITUD)).when(servicio).rechazar(any(), any());

        mockMvc.perform(post("/api/v1/admin/solicitudes-especie/{id}/aprobar", SOLICITUD).with(comoAdministrador()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SOLICITUD_YA_RESUELTA"))
                .andExpect(jsonPath("$.detalle.estado").value("APROBADA"));
        mockMvc.perform(post("/api/v1/admin/solicitudes-especie/{id}/rechazar", SOLICITUD).with(comoAdministrador())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"respuesta\":\"no\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SOLICITUD_NO_ENCONTRADA"));
    }

    @Test
    void unClienteNoPuedeUsarLasRutasDelAdministradorYNingunaLlegaAlServicio() throws Exception {
        mockMvc.perform(get("/api/v1/admin/solicitudes-especie").with(comoCliente())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/solicitudes-especie/{id}/aprobar", SOLICITUD).with(comoCliente()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/solicitudes-especie/{id}/rechazar", SOLICITUD).with(comoCliente())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"respuesta\":\"x\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(servicio);
    }
}
