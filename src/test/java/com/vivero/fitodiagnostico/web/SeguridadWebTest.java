package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioAdministracion;
import com.vivero.fitodiagnostico.aplicacion.ServicioCatalogo;
import com.vivero.fitodiagnostico.aplicacion.ServicioCuentas;
import com.vivero.fitodiagnostico.aplicacion.ServicioDiagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Credencial;
import com.vivero.fitodiagnostico.dominio.modelo.TokenDeSesion;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La política de seguridad de {@link ConfiguracionSeguridad}, probada con la
 * cadena real y los servicios simulados: qué es público, qué exige sesión, qué
 * exige el rol, y que los 401/403 salen como el JSON uniforme (RA1) aunque el
 * cliente pida HTML. La lógica de cada ruta se prueba en sus propias pruebas;
 * aquí solo importa si la puerta abre o no.
 */
@WebMvcTest({DiagnosticoController.class, EspeciesController.class, AutenticacionController.class,
        CuentaController.class, AdministracionUsuariosController.class, AdministracionEspeciesController.class})
@Import(SeguridadDePruebas.class)
class SeguridadWebTest {

    private static final String UN_ID = "7d4f2a56-9d0a-4a58-9d11-0a3a8a4b8f10";

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ServicioDiagnostico diagnostico;
    @MockBean private ServicioCatalogo catalogo;
    @MockBean private ServicioAdministracion administracion;
    @MockBean private ServicioCuentas cuentas;

    private static RequestPostProcessor comoCliente() {
        return jwt().jwt(j -> j.subject(UN_ID)).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private static RequestPostProcessor comoAdministrador() {
        return jwt().jwt(j -> j.subject(UN_ID)).authorities(
                new SimpleGrantedAuthority("ROLE_CLIENTE"), new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    private static Usuario ana() {
        return new Usuario(UUID.fromString(UN_ID), "Ana", "ana@vivero.co", new Credencial("h"), java.util.Set.of());
    }

    // ---- 401: sin sesión ----

    @Test
    void sinTokenUnaRutaProtegidaDa401ConCuerpoJsonUniforme() throws Exception {
        mockMvc.perform(get("/api/v1/cuenta"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.detalle").isEmpty());
    }

    @Test
    void unTokenInvalidoDa401ConCuerpoJson() throws Exception {
        mockMvc.perform(get("/api/v1/cuenta").header(HttpHeaders.AUTHORIZATION, "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
    }

    @Test
    void el401SigueSiendoJsonAunqueElClientePidaHtml() throws Exception {
        mockMvc.perform(get("/api/v1/cuenta").accept(MediaType.TEXT_HTML))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
    }

    @Test
    void todasLasRutasDeAdministracionYDeCuentaDan401SinToken() throws Exception {
        for (MockHttpServletRequestBuilder peticion : List.of(
                get("/api/v1/cuenta"), put("/api/v1/cuenta"), delete("/api/v1/cuenta"),
                put("/api/v1/cuenta/contrasena"),
                get("/api/v1/admin/usuarios"), get("/api/v1/admin/usuarios/" + UN_ID),
                put("/api/v1/admin/usuarios/" + UN_ID), delete("/api/v1/admin/usuarios/" + UN_ID),
                put("/api/v1/admin/usuarios/" + UN_ID + "/rol-administrador"),
                delete("/api/v1/admin/usuarios/" + UN_ID + "/rol-administrador"),
                get("/api/v1/admin/especies"), post("/api/v1/admin/especies"),
                put("/api/v1/admin/especies/helecho"), post("/api/v1/admin/especies/helecho/desactivar"),
                post("/api/v1/admin/especies/helecho/reactivar"))) {
            mockMvc.perform(peticion.contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void unaRutaQueNoExisteSinTokenDa401NoUn404() throws Exception {
        // anyRequest().authenticated(): por defecto todo está cerrado, también lo que aún no existe.
        mockMvc.perform(get("/api/v1/plantas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
    }

    @Test
    void soloElMetodoPublicoEsPublico() throws Exception {
        // POST /diagnosticos es público; un GET a la misma ruta no lo es.
        mockMvc.perform(get("/api/v1/diagnosticos")).andExpect(status().isUnauthorized());
        // GET /especies es público; un POST a la misma ruta no lo es.
        mockMvc.perform(post("/api/v1/especies").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 403: sesión sin el rol ----

    @Test
    void unClienteEnRutasDeAdministracionDa403ConCuerpoJsonUniforme() throws Exception {
        mockMvc.perform(get("/api/v1/admin/usuarios").with(comoCliente()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("ACCESO_DENEGADO"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty())
                .andExpect(jsonPath("$.detalle").isEmpty());
    }

    @Test
    void el403SigueSiendoJsonAunqueElClientePidaHtml() throws Exception {
        mockMvc.perform(get("/api/v1/admin/especies").with(comoCliente()).accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("ACCESO_DENEGADO"));
    }

    @Test
    void unClienteNoPuedeUsarNingunaRutaDeAdministracion() throws Exception {
        for (MockHttpServletRequestBuilder peticion : List.of(
                get("/api/v1/admin/usuarios"), get("/api/v1/admin/usuarios/" + UN_ID),
                put("/api/v1/admin/usuarios/" + UN_ID), delete("/api/v1/admin/usuarios/" + UN_ID),
                put("/api/v1/admin/usuarios/" + UN_ID + "/rol-administrador"),
                delete("/api/v1/admin/usuarios/" + UN_ID + "/rol-administrador"),
                get("/api/v1/admin/especies"), post("/api/v1/admin/especies"),
                put("/api/v1/admin/especies/helecho"), post("/api/v1/admin/especies/helecho/desactivar"),
                post("/api/v1/admin/especies/helecho/reactivar"))) {
            mockMvc.perform(peticion.with(comoCliente()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    // ---- 200: con el rol ----

    @Test
    void unAdministradorSiEntraALasRutasDeAdministracion() throws Exception {
        when(administracion.listarUsuarios(any())).thenReturn(List.of(ana()));
        when(catalogo.listarTodas()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/usuarios").with(comoAdministrador()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/admin/especies").with(comoAdministrador()))
                .andExpect(status().isOk());
    }

    @Test
    void unClienteConSesionSiEntraASuCuenta() throws Exception {
        when(cuentas.consultar(UUID.fromString(UN_ID))).thenReturn(ana());

        mockMvc.perform(get("/api/v1/cuenta").with(comoCliente()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("ana@vivero.co"));
    }

    // ---- rutas públicas siguen públicas ----

    @Test
    void diagnosticarSigueSiendoPublicoSinToken() throws Exception {
        when(diagnostico.diagnosticar(anyString(), any())).thenThrow(
                new com.vivero.fitodiagnostico.dominio.excepcion.EspecieNoEncontradaException("x"));

        // 404 de negocio, no 401: la puerta está abierta y se llegó al controlador.
        mockMvc.perform(post("/api/v1/diagnosticos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"especie\":\"x\",\"humedad\":30,\"luz\":800,\"temperatura\":22}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ESPECIE_NO_SOPORTADA"));
    }

    @Test
    void listarEspeciesSigueSiendoPublicoSinToken() throws Exception {
        when(catalogo.listar()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/especies")).andExpect(status().isOk());
    }

    @Test
    void registroEInicioDeSesionSonPublicos() throws Exception {
        when(cuentas.registrar(anyString(), anyString(), anyString())).thenReturn(ana());
        when(cuentas.iniciarSesion(anyString(), anyString())).thenReturn(
                new ServicioCuentas.Sesion(ana(), new TokenDeSesion("t", Instant.parse("2030-01-01T00:00:00Z"))));

        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"correo\":\"ana@vivero.co\",\"contrasena\":\"clave-larga-1\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/sesion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@vivero.co\",\"contrasena\":\"clave-larga-1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void lasRutasPublicasNoExigenTokenCsrf() throws Exception {
        // Sin cookies de sesión no hay nada que falsificar; un POST público sin token CSRF no es 403.
        mockMvc.perform(post("/api/v1/auth/sesion").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
