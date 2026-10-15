package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioAdministracion;
import com.vivero.fitodiagnostico.aplicacion.ServicioCatalogo;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieYaExisteException;
import com.vivero.fitodiagnostico.dominio.excepcion.UltimoAdministradorException;
import com.vivero.fitodiagnostico.dominio.excepcion.UsuarioNoEncontradoException;
import com.vivero.fitodiagnostico.dominio.modelo.Credencial;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rebanada web de administración de usuarios (RF-B1 a RF-B5) y de especies
 * (RF-C2 a RF-C6): forma de peticiones y respuestas y traducción de errores,
 * con los servicios simulados. El control de acceso por rol se prueba en
 * {@link SeguridadWebTest}; aquí todas las peticiones llevan rol de administrador.
 */
@WebMvcTest({AdministracionUsuariosController.class, AdministracionEspeciesController.class})
@Import(SeguridadDePruebas.class)
class AdministracionControllerTest {

    private static final UUID ID = UUID.fromString("7d4f2a56-9d0a-4a58-9d11-0a3a8a4b8f10");

    @Autowired
    private MockMvc mockMvc;

    @MockBean private ServicioAdministracion administracion;
    @MockBean private ServicioCatalogo catalogo;

    private static RequestPostProcessor comoAdministrador() {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));
    }

    private static Usuario ana() {
        return new Usuario(ID, "Ana", "ana@vivero.co", new Credencial("hash-secreto"), Set.of());
    }

    private static Map<Magnitud, Rango> rangos() {
        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        rangos.put(Magnitud.HUMEDAD, new Rango(30.0, 60.0));
        rangos.put(Magnitud.LUZ, new Rango(500.0, 1000.0));
        rangos.put(Magnitud.TEMPERATURA, new Rango(18.0, 26.0));
        return rangos;
    }

    private static final String CUERPO_RANGOS = "\"rangos\":{\"humedad\":{\"min\":30,\"max\":60},"
            + "\"luz\":{\"min\":500,\"max\":1000},\"temperatura\":{\"min\":18,\"max\":26}}";

    // ---- usuarios ----

    @Test
    void listarUsuariosPasaElTextoDeBusquedaYNoExponeCredenciales() throws Exception {
        when(administracion.listarUsuarios("ana")).thenReturn(List.of(ana()));

        mockMvc.perform(get("/api/v1/admin/usuarios").param("q", "ana").with(comoAdministrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].correo").value("ana@vivero.co"))
                .andExpect(jsonPath("$[0].credencial").doesNotExist());
        verify(administracion).listarUsuarios("ana");
    }

    @Test
    void detalleDeUsuarioInexistenteDa404() throws Exception {
        when(administracion.detalleDeUsuario(ID)).thenThrow(new UsuarioNoEncontradoException(ID));

        mockMvc.perform(get("/api/v1/admin/usuarios/" + ID).with(comoAdministrador()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USUARIO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.detalle.id").value(ID.toString()));
    }

    @Test
    void unIdQueNoEsUuidDa400() throws Exception {
        mockMvc.perform(get("/api/v1/admin/usuarios/no-es-uuid").with(comoAdministrador()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.detalle.campo").value("id"));
    }

    @Test
    void editarUnUsuario() throws Exception {
        when(administracion.editarUsuario(ID, "Ana Z.", "ana.z@vivero.co"))
                .thenReturn(new Usuario(ID, "Ana Z.", "ana.z@vivero.co", new Credencial("h"), Set.of()));

        mockMvc.perform(put("/api/v1/admin/usuarios/" + ID).with(comoAdministrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana Z.\",\"correo\":\"ana.z@vivero.co\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ana Z."));
    }

    @Test
    void eliminarUnUsuarioDa204YElUltimoAdministradorDa409() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/usuarios/" + ID).with(comoAdministrador()))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.doThrow(new UltimoAdministradorException()).when(administracion).eliminarUsuario(ID);
        mockMvc.perform(delete("/api/v1/admin/usuarios/" + ID).with(comoAdministrador()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ULTIMO_ADMINISTRADOR"));
    }

    @Test
    void otorgarYRevocarElRolDeAdministrador() throws Exception {
        when(administracion.otorgarAdministrador(ID)).thenReturn(ana().conRolAdministrador());
        when(administracion.revocarAdministrador(ID)).thenReturn(ana());

        mockMvc.perform(put("/api/v1/admin/usuarios/" + ID + "/rol-administrador").with(comoAdministrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("CLIENTE"))
                .andExpect(jsonPath("$.roles[1]").value("ADMINISTRADOR"));
        mockMvc.perform(delete("/api/v1/admin/usuarios/" + ID + "/rol-administrador").with(comoAdministrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(1));
    }

    @Test
    void revocarElRolDelUltimoAdministradorDa409() throws Exception {
        when(administracion.revocarAdministrador(ID)).thenThrow(new UltimoAdministradorException());

        mockMvc.perform(delete("/api/v1/admin/usuarios/" + ID + "/rol-administrador").with(comoAdministrador()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ULTIMO_ADMINISTRADOR"));
    }

    // ---- especies ----

    @Test
    void listarTodasIncluyeElEstadoDeActivacion() throws Exception {
        when(catalogo.listarTodas()).thenReturn(List.of(
                new Especie("helecho", rangos()), new Especie("lavanda", rangos()).desactivada()));

        mockMvc.perform(get("/api/v1/admin/especies").with(comoAdministrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].nombre").value("helecho"))
                .andExpect(jsonPath("$[0].activa").value(true))
                .andExpect(jsonPath("$[1].activa").value(false))
                .andExpect(jsonPath("$[0].rangos.humedad.min").value(30.0))
                .andExpect(jsonPath("$[0].rangos.luz.unidad").value("lux"));
    }

    @Test
    void crearUnaEspecieDa201YConvierteLasClavesDePresentacionAMagnitudes() throws Exception {
        when(administracion.crearEspecie(eq("orquidea"), anyMap())).thenReturn(new Especie("orquidea", rangos()));

        mockMvc.perform(post("/api/v1/admin/especies").with(comoAdministrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"orquidea\"," + CUERPO_RANGOS + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("orquidea"))
                .andExpect(jsonPath("$.activa").value(true));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Magnitud, Rango>> captor = ArgumentCaptor.forClass(Map.class);
        verify(administracion).crearEspecie(eq("orquidea"), captor.capture());
        assertThat(captor.getValue()).isEqualTo(rangos());
    }

    @Test
    void crearUnaEspecieExistenteDa409() throws Exception {
        when(administracion.crearEspecie(anyString(), anyMap())).thenThrow(new EspecieYaExisteException("helecho"));

        mockMvc.perform(post("/api/v1/admin/especies").with(comoAdministrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"helecho\"," + CUERPO_RANGOS + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ESPECIE_YA_EXISTE"))
                .andExpect(jsonPath("$.detalle.especie").value("helecho"));
    }

    @Test
    void crearConUnaMagnitudDesconocidaOUnRangoInvertidoDa400SinLlamarAlServicio() throws Exception {
        mockMvc.perform(post("/api/v1/admin/especies").with(comoAdministrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"x\",\"rangos\":{\"ph\":{\"min\":5,\"max\":7}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
        mockMvc.perform(post("/api/v1/admin/especies").with(comoAdministrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"x\",\"rangos\":{\"humedad\":{\"min\":60,\"max\":30}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
        verify(administracion, never()).crearEspecie(anyString(), anyMap());
    }

    @Test
    void crearSinNombreOSinRangosDa400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/especies").with(comoAdministrador())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rangos\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.campo").value("nombre"));
        mockMvc.perform(post("/api/v1/admin/especies").with(comoAdministrador())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.campo").value("rangos"));
        verifyNoInteractions(administracion);
    }

    @Test
    void unRangoSinMinODelServicioFaltanteDa400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/especies").with(comoAdministrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"x\",\"rangos\":{\"humedad\":{\"max\":60}}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void editarLosRangosDeUnaEspecie() throws Exception {
        when(administracion.editarEspecie(eq("helecho"), anyMap())).thenReturn(new Especie("helecho", rangos()));

        mockMvc.perform(put("/api/v1/admin/especies/helecho").with(comoAdministrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + CUERPO_RANGOS + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("helecho"));
    }

    @Test
    void editarUnaEspecieInexistenteDa404() throws Exception {
        when(administracion.editarEspecie(anyString(), anyMap())).thenThrow(new EspecieNoEncontradaException("fantasma"));

        mockMvc.perform(put("/api/v1/admin/especies/fantasma").with(comoAdministrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + CUERPO_RANGOS + "}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ESPECIE_NO_SOPORTADA"));
    }

    @Test
    void desactivarYReactivarDevuelvenLaEspecieConSuNuevoEstado() throws Exception {
        when(administracion.desactivarEspecie("helecho")).thenReturn(new Especie("helecho", rangos()).desactivada());
        when(administracion.reactivarEspecie("helecho")).thenReturn(new Especie("helecho", rangos()));

        mockMvc.perform(post("/api/v1/admin/especies/helecho/desactivar").with(comoAdministrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false));
        mockMvc.perform(post("/api/v1/admin/especies/helecho/reactivar").with(comoAdministrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    void noExisteUnVerboDeBorradoParaEspecies() throws Exception {
        // D10: nunca se eliminan. DELETE no está mapeado: 405, no un borrado silencioso.
        mockMvc.perform(delete("/api/v1/admin/especies/helecho").with(comoAdministrador()))
                .andExpect(status().isMethodNotAllowed());
    }
}
