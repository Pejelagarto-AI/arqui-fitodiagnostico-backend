package com.vivero.fitodiagnostico;

import com.jayway.jsonpath.JsonPath;
import com.vivero.fitodiagnostico.adaptador.salida.notificacion.PublicadorDeMascotaPorWebSocket;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;
import com.vivero.fitodiagnostico.mascota.modelo.AvisoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeEvento;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La mascota de punta a punta (fase 9) con el contexto real: seguridad JWT, el caso de uso de medir, el puente
 * después del commit, JPA y Flyway (V16 a V19). El canal WebSocket es un espía. Datos propios con prefijo
 * {@code mascota-}; borrar la planta borra su mascota por la cascada de V16 a V18.
 *
 * <p>Helecho: humedad 60-85, luz 150-800, temperatura 16-26.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MascotaIntegracionTest {

    private static final String CLAVE = "clave-larga-1";
    private static final String SECO = "{\"humedad\":62,\"luz\":500,\"temperatura\":22}";
    private static final String REGADO = "{\"humedad\":80,\"luz\":500,\"temperatura\":22}";
    private static final String SEDIENTO = "{\"humedad\":20,\"luz\":500,\"temperatura\":22}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RepositorioAccesos accesos;
    @SpyBean private PublicadorDeMascotaPorWebSocket vivo;

    private String dueno;
    private String invitado;
    private String ajeno;
    private String planta;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        dueno = registrarEIniciarSesion();
        invitado = registrarEIniciarSesion();
        ajeno = registrarEIniciarSesion();
        planta = leer(mockMvc.perform(json(con(post("/api/v1/plantas"), dueno),
                "{\"apodo\":\"Pepa\",\"especie\":\"helecho\"}")).andExpect(status().isCreated()), "$.id");
        accesos.otorgar(AccesoCompartido.otorgadoAhora(UUID.fromString(planta), UUID.fromString(idDe(invitado))));
        reset(vivo);
    }

    @AfterEach
    void limpiar() {
        String plantas = "select p.id from planta p join usuario u on u.id = p.dueno_id where u.correo like 'mascota-%'";
        jdbc.update("delete from diagnostico_planta where planta_id in (" + plantas + ")");
        jdbc.update("delete from acceso_compartido where planta_id in (" + plantas + ")");
        jdbc.update("delete from planta where dueno_id in (select id from usuario where correo like 'mascota-%')");
        jdbc.update("delete from usuario_rol where usuario_id in (select id from usuario where correo like 'mascota-%')");
        jdbc.update("delete from usuario where correo like 'mascota-%'");
    }

    // ---- utilidades ----

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String leer(ResultActions resultado, String ruta) throws Exception {
        return String.valueOf((Object) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), ruta));
    }

    private String registrarEIniciarSesion() throws Exception {
        String correo = "mascota-" + UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
        mockMvc.perform(json(post("/api/v1/auth/registro"),
                "{\"nombre\":\"Mascota\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isCreated());
        return leer(mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}")).andExpect(status().isOk()), "$.token");
    }

    private String idDe(String token) throws Exception {
        return leer(mockMvc.perform(con(get("/api/v1/cuenta"), token)).andExpect(status().isOk()), "$.id");
    }

    private void medir(String cuerpo) throws Exception {
        mockMvc.perform(json(con(post("/api/v1/plantas/" + planta + "/mediciones"), dueno), cuerpo))
                .andExpect(status().isCreated());
    }

    private ResultActions mascota(String token) throws Exception {
        return mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/mascota"), token));
    }

    private ResultActions actuar(String token, String tipo) throws Exception {
        return mockMvc.perform(json(con(post("/api/v1/plantas/" + planta + "/mascota/acciones"), token),
                "{\"tipo\":\"" + tipo + "\"}"));
    }

    private int filas(String tabla) {
        return jdbc.queryForObject("select count(*) from " + tabla + " where planta_id = ?", Integer.class,
                UUID.fromString(planta));
    }

    // ---- pruebas ----

    @Test
    void sinLecturasLaMascotaResponde200SinDatos() throws Exception {
        mascota(dueno).andExpect(status().isOk())
                .andExpect(jsonPath("$.plantaId").value(planta))
                .andExpect(jsonPath("$.hayLecturas").value(false))
                .andExpect(jsonPath("$.vitalidad").value(80))
                .andExpect(jsonPath("$.animo").value("SIN_DATOS"))
                .andExpect(jsonPath("$.racha.dias").value(0))
                .andExpect(jsonPath("$.ultimosEventos").isEmpty())
                .andExpect(jsonPath("$.accionPendiente").value(nullValue()));
        assertThat(filas("mascota")).isZero();   // consultar nunca escribe
    }

    @Test
    void cadaLecturaAlimentaALaMascotaYElAnimoSaleDelDiagnostico() throws Exception {
        medir(SEDIENTO);

        mascota(dueno).andExpect(status().isOk())
                .andExpect(jsonPath("$.hayLecturas").value(true))
                .andExpect(jsonPath("$.animo").value("SEDIENTA"))
                .andExpect(jsonPath("$.causa.parametro").value("humedad"))
                .andExpect(jsonPath("$.causa.direccion").value("BAJO"))
                .andExpect(jsonPath("$.estadoDelDiagnostico").value("CRITICO"));
        assertThat(filas("mascota")).isEqualTo(1);
    }

    @Test
    void elDuenoEInvitadoLeenSoloElDuenoActuaYUnAjenoNoVeNada() throws Exception {
        mascota(invitado).andExpect(status().isOk());
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/mascota/eventos"), invitado)).andExpect(status().isOk());

        actuar(invitado, "ACARICIAR").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("OPERACION_NO_PERMITIDA"));
        mascota(ajeno).andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("PLANTA_NO_ENCONTRADA"));
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/mascota/eventos"), ajeno)).andExpect(status().isNotFound());
        actuar(ajeno, "ACARICIAR").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/plantas/" + planta + "/mascota")).andExpect(status().isUnauthorized());
    }

    @Test
    void acariciarRegistraElEventoYLoEmpujaALaVistaEnVivo() throws Exception {
        actuar(dueno, "acariciar").andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ACARICIAR"))
                .andExpect(jsonPath("$.estado").value("HECHA"));

        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/mascota/eventos?tamano=5"), invitado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.contenido[0].tipo").value("ACARICIADA"));
        ArgumentCaptor<AvisoDeMascota> captor = ArgumentCaptor.forClass(AvisoDeMascota.class);
        verify(vivo).publicar(captor.capture());
        assertThat(captor.getValue().evento().tipo()).isEqualTo(TipoDeEvento.ACARICIADA);
        assertThat(captor.getValue().destinatarios()).hasSize(2);
    }

    @Test
    void unTipoDeAccionDesconocidoEs400() throws Exception {
        actuar(dueno, "ALIMENTAR").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
        mockMvc.perform(json(con(post("/api/v1/plantas/" + planta + "/mascota/acciones"), dueno), "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laRegueQuedaPendienteYDosLecturasHumedasLaConfirmanConUnaRegada() throws Exception {
        for (int i = 0; i < 4; i++) {
            medir(SECO);
        }
        String accion = leer(actuar(dueno, "LA_REGUE").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("PENDIENTE")), "$.id");
        mascota(dueno).andExpect(jsonPath("$.accionPendiente.id").value(accion));

        medir(REGADO);
        medir(REGADO);

        mascota(dueno).andExpect(jsonPath("$.accionPendiente").value(nullValue()))
                .andExpect(jsonPath("$.ultimosEventos[*].tipo", hasItem("REGADA")))
                .andExpect(jsonPath("$.ultimosEventos[*].tipo", hasItem("RIEGO_CONFIRMADO")));
        verify(vivo, atLeastOnce()).publicar(org.mockito.ArgumentMatchers.any());
        List<String> tipos = jdbc.queryForList("select tipo from evento_mascota where planta_id = ?", String.class,
                UUID.fromString(planta));
        assertThat(tipos).contains("RIEGO_DECLARADO", "REGADA", "RIEGO_CONFIRMADO");
    }

    @Test
    void borrarLaPlantaBorraSuMascotaPorLaCascada() throws Exception {
        medir(SECO);
        actuar(dueno, "ACARICIAR").andExpect(status().isCreated());
        actuar(dueno, "LA_REGUE").andExpect(status().isAccepted());
        assertThat(filas("mascota") + filas("evento_mascota") + filas("accion_mascota")).isGreaterThanOrEqualTo(3);

        mockMvc.perform(con(delete("/api/v1/plantas/" + planta), dueno)).andExpect(status().isNoContent());

        assertThat(filas("mascota") + filas("evento_mascota") + filas("accion_mascota")).isZero();
    }
}
