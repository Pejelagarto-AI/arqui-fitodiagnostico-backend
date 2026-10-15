package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioInvitaciones;
import com.vivero.fitodiagnostico.aplicacion.ServicioRecuperacion;
import com.vivero.fitodiagnostico.aplicacion.ServicioSolicitudes;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La política de seguridad de las rutas del bloque 4, con la cadena real y los
 * servicios simulados: qué exige sesión, qué es público y qué exige el rol de
 * administrador. Solo importa si la puerta abre, no lo que hay detrás.
 */
@WebMvcTest({InvitacionesController.class, RecuperacionController.class, SolicitudesEspecieController.class,
        AdministracionSolicitudesController.class})
@Import({SeguridadDePruebas.class, SeguridadCompartirYAvisosWebTest.Reloj.class})
class SeguridadCompartirYAvisosWebTest {

    private static final String UN_ID = "7d4f2a56-9d0a-4a58-9d11-0a3a8a4b8f10";

    @TestConfiguration
    static class Reloj {
        @Bean
        Clock reloj() {
            return Clock.systemUTC();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ServicioInvitaciones invitaciones;
    @MockBean private ServicioRecuperacion recuperacion;
    @MockBean private ServicioSolicitudes solicitudes;

    private static RequestPostProcessor comoCliente() {
        return jwt().jwt(j -> j.subject(UN_ID)).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private static List<MockHttpServletRequestBuilder> rutasProtegidas() {
        String otro = UUID.randomUUID().toString();
        return List.of(
                post("/api/v1/plantas/" + UN_ID + "/invitaciones"), get("/api/v1/plantas/" + UN_ID + "/invitaciones"),
                delete("/api/v1/plantas/" + UN_ID + "/invitaciones/" + otro),
                get("/api/v1/plantas/" + UN_ID + "/invitados"), delete("/api/v1/plantas/" + UN_ID + "/invitados/" + otro),
                get("/api/v1/plantas/compartidas"), delete("/api/v1/plantas/compartidas/" + UN_ID),
                post("/api/v1/invitaciones/aceptar"),
                post("/api/v1/solicitudes-especie"), get("/api/v1/solicitudes-especie"));
    }

    @Test
    void todasLasRutasDeCompartirYSolicitudesDan401SinTokenConCuerpoJson() throws Exception {
        for (MockHttpServletRequestBuilder peticion : rutasProtegidas()) {
            mockMvc.perform(peticion.contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
        }
    }

    @Test
    void lasRutasDelAdministradorDeSolicitudesDan401SinTokenY403ALosClientes() throws Exception {
        List<MockHttpServletRequestBuilder> admin = List.of(
                get("/api/v1/admin/solicitudes-especie"),
                post("/api/v1/admin/solicitudes-especie/" + UN_ID + "/aprobar"),
                post("/api/v1/admin/solicitudes-especie/" + UN_ID + "/rechazar"));
        for (MockHttpServletRequestBuilder peticion : admin) {
            mockMvc.perform(peticion.contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        for (MockHttpServletRequestBuilder peticion : admin) {
            mockMvc.perform(peticion.with(comoCliente()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("ACCESO_DENEGADO"));
        }
    }

    @Test
    void laRecuperacionYAceptarRegistrandoseSonPublicasPeroAceptarConSesionNo() throws Exception {
        // 400 por cuerpo vacío = la puerta abrió y se llegó a la validación; 401 sería puerta cerrada.
        mockMvc.perform(post("/api/v1/auth/recuperacion").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/restablecimiento").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/invitaciones/aceptar-con-registro").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/invitaciones/aceptar").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void soloElMetodoPublicoEsPublicoEnLasRutasNuevas() throws Exception {
        mockMvc.perform(get("/api/v1/auth/recuperacion")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/invitaciones/aceptar-con-registro")).andExpect(status().isUnauthorized());
    }

    @Test
    void unClienteConSesionEntraATodasSusRutasNuevas() throws Exception {
        for (MockHttpServletRequestBuilder peticion : rutasProtegidas()) {
            int estado = mockMvc.perform(peticion.with(comoCliente()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();
            org.assertj.core.api.Assertions.assertThat(estado).as(peticion.toString()).isNotIn(401, 403);
        }
    }
}
