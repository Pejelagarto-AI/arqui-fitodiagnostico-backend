package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioCuentas;
import com.vivero.fitodiagnostico.dominio.excepcion.ContrasenaActualIncorrectaException;
import com.vivero.fitodiagnostico.dominio.excepcion.CorreoYaRegistradoException;
import com.vivero.fitodiagnostico.dominio.excepcion.CredencialesInvalidasException;
import com.vivero.fitodiagnostico.dominio.excepcion.UltimoAdministradorException;
import com.vivero.fitodiagnostico.dominio.modelo.Credencial;
import com.vivero.fitodiagnostico.dominio.modelo.TokenDeSesion;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rebanada web de registro, inicio de sesión y cuenta propia (RF-A1 a RF-A6):
 * forma de las peticiones y respuestas y traducción de cada excepción de
 * negocio a su código HTTP, con {@link ServicioCuentas} simulado.
 */
@WebMvcTest({AutenticacionController.class, CuentaController.class})
@Import(SeguridadDePruebas.class)
class CuentasControllerTest {

    private static final UUID ID = UUID.fromString("7d4f2a56-9d0a-4a58-9d11-0a3a8a4b8f10");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServicioCuentas servicio;

    private static Usuario ana() {
        return new Usuario(ID, "Ana", "ana@vivero.co", new Credencial("hash-secreto"), Set.of());
    }

    private static RequestPostProcessor conSesion() {
        return jwt().jwt(j -> j.subject(ID.toString())).authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    private static final String REGISTRO_VALIDO =
            "{\"nombre\":\"Ana\",\"correo\":\"ana@vivero.co\",\"contrasena\":\"clave-larga-1\"}";

    // ---- registro ----

    @Test
    void registrarDevuelve201ConElUsuarioSinCredencial() throws Exception {
        when(servicio.registrar("Ana", "ana@vivero.co", "clave-larga-1")).thenReturn(ana());

        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(REGISTRO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.correo").value("ana@vivero.co"))
                .andExpect(jsonPath("$.roles[0]").value("CLIENTE"))
                // RN10: ni la contraseña ni su hash salen de la API, bajo ningún nombre.
                .andExpect(jsonPath("$.contrasena").doesNotExist())
                .andExpect(jsonPath("$.credencial").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hash-secreto"))));
    }

    @Test
    void registrarConUnCorreoRepetidoDa409() throws Exception {
        when(servicio.registrar(anyString(), anyString(), anyString()))
                .thenThrow(new CorreoYaRegistradoException("ana@vivero.co"));

        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(REGISTRO_VALIDO))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CORREO_YA_REGISTRADO"))
                .andExpect(jsonPath("$.detalle.campo").value("correo"));
    }

    @Test
    void registrarValidaLaFormaEnElBordeSinLlamarAlServicio() throws Exception {
        String[] cuerpos = {
            "{\"correo\":\"ana@vivero.co\",\"contrasena\":\"clave-larga-1\"}",                                  // sin nombre
            "{\"nombre\":\"Ana\",\"correo\":\"no-es-correo\",\"contrasena\":\"clave-larga-1\"}",               // correo mal
            "{\"nombre\":\"Ana\",\"correo\":\"ana@vivero.co\",\"contrasena\":\"corta\"}",                      // < 8
            "{\"nombre\":\"Ana\",\"correo\":\"ana@vivero.co\",\"contrasena\":\"" + "x".repeat(73) + "\"}",     // > 72 (límite de BCrypt)
            "{\"nombre\":\"  \",\"correo\":\"ana@vivero.co\",\"contrasena\":\"clave-larga-1\"}",               // nombre en blanco
        };
        for (String cuerpo : cuerpos) {
            mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
        }
        org.mockito.Mockito.verifyNoInteractions(servicio);
    }

    @Test
    void elCampoReportadoEsElPrimeroEnElOrdenDeterminista() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.campo").value("nombre"));
    }

    // ---- inicio de sesión ----

    @Test
    void iniciarSesionDevuelveTokenTipoCaducidadYUsuario() throws Exception {
        when(servicio.iniciarSesion("ana@vivero.co", "clave-larga-1")).thenReturn(new ServicioCuentas.Sesion(
                ana(), new TokenDeSesion("el.token.jwt", Instant.parse("2030-01-01T00:00:00Z"))));

        mockMvc.perform(post("/api/v1/auth/sesion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@vivero.co\",\"contrasena\":\"clave-larga-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("el.token.jwt"))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").value("2030-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.usuario.correo").value("ana@vivero.co"));
    }

    @Test
    void iniciarSesionConCredencialesMalasDa401ConCodigoPropio() throws Exception {
        when(servicio.iniciarSesion(anyString(), anyString())).thenThrow(new CredencialesInvalidasException());

        mockMvc.perform(post("/api/v1/auth/sesion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@vivero.co\",\"contrasena\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void iniciarSesionSinCamposDa400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/sesion").contentType(MediaType.APPLICATION_JSON).content("{\"correo\":\"a@b.co\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.campo").value("contrasena"));
    }

    // ---- cuenta propia ----

    @Test
    void consultarLaCuentaUsaElIdDelTokenNuncaUnoDeLaRuta() throws Exception {
        when(servicio.consultar(ID)).thenReturn(ana());

        mockMvc.perform(get("/api/v1/cuenta").with(conSesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()));
        verify(servicio).consultar(ID);
    }

    @Test
    void conAcceptHtmlLaRespuestaSigueSiendoJson() throws Exception {
        // RA1 (ConfiguracionContenido): sin ella, esto sería un 500 por HttpMediaTypeNotAcceptableException.
        when(servicio.consultar(ID)).thenReturn(ana());

        mockMvc.perform(get("/api/v1/cuenta").with(conSesion()).accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.correo").value("ana@vivero.co"));
    }

    @Test
    void actualizarLosDatosPropios() throws Exception {
        when(servicio.actualizarDatos(ID, "Ana M.", "ana.m@vivero.co"))
                .thenReturn(new Usuario(ID, "Ana M.", "ana.m@vivero.co", new Credencial("h"), Set.of()));

        mockMvc.perform(put("/api/v1/cuenta").with(conSesion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana M.\",\"correo\":\"ana.m@vivero.co\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana M."));
    }

    @Test
    void actualizarConUnCorreoDeOtroDa409() throws Exception {
        when(servicio.actualizarDatos(any(), anyString(), anyString())).thenThrow(new CorreoYaRegistradoException("x@y.co"));

        mockMvc.perform(put("/api/v1/cuenta").with(conSesion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"correo\":\"x@y.co\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void cambiarLaContrasenaDa204() throws Exception {
        mockMvc.perform(put("/api/v1/cuenta/contrasena").with(conSesion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\":\"clave-larga-1\",\"contrasenaNueva\":\"clave-nueva-22\"}"))
                .andExpect(status().isNoContent());
        verify(servicio).cambiarContrasena(ID, "clave-larga-1", "clave-nueva-22");
    }

    @Test
    void cambiarLaContrasenaConLaActualIncorrectaDa400YNoUn401() throws Exception {
        doThrow(new ContrasenaActualIncorrectaException()).when(servicio).cambiarContrasena(any(), anyString(), anyString());

        mockMvc.perform(put("/api/v1/cuenta/contrasena").with(conSesion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\":\"mala\",\"contrasenaNueva\":\"clave-nueva-22\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CONTRASENA_ACTUAL_INCORRECTA"))
                .andExpect(jsonPath("$.detalle.campo").value("contrasenaActual"));
    }

    @Test
    void laContrasenaNuevaCortaSeRechazaEnElBorde() throws Exception {
        mockMvc.perform(put("/api/v1/cuenta/contrasena").with(conSesion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contrasenaActual\":\"clave-larga-1\",\"contrasenaNueva\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.campo").value("contrasenaNueva"));
        org.mockito.Mockito.verifyNoInteractions(servicio);
    }

    @Test
    void eliminarLaPropiaCuentaDa204() throws Exception {
        mockMvc.perform(delete("/api/v1/cuenta").with(conSesion()))
                .andExpect(status().isNoContent());
        verify(servicio).eliminar(eq(ID));
    }

    @Test
    void eliminarLaPropiaCuentaDelUnicoAdministradorDa409() throws Exception {
        doThrow(new UltimoAdministradorException()).when(servicio).eliminar(any());

        mockMvc.perform(delete("/api/v1/cuenta").with(conSesion()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ULTIMO_ADMINISTRADOR"));
    }

    @Test
    void unCorreoSinFormaDeDireccionQueEscapaAlBordeDa400() throws Exception {
        // @Email deja pasar "a@b"; el dominio la rechaza con IllegalArgumentException y el
        // manejador global la traduce a 400 (no a 500).
        when(servicio.actualizarDatos(any(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("el correo no tiene forma de dirección: x"));

        mockMvc.perform(put("/api/v1/cuenta").with(conSesion()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana\",\"correo\":\"a@b\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
    }
}
