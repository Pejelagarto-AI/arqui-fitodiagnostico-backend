package com.vivero.fitodiagnostico;

import com.jayway.jsonpath.JsonPath;
import com.vivero.fitodiagnostico.adaptador.salida.notificacion.PublicadorDeMascotaPorWebSocket;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.puerto.salida.EmisorTokenDispositivoPort;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;
import com.vivero.fitodiagnostico.mascota.modelo.ActualizacionDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.Accesorio;
import com.vivero.fitodiagnostico.mascota.modelo.AvisoDeMascota;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La mascota completa (fase 13) de punta a punta con el contexto real: seguridad JWT con token de persona y de
 * dispositivo, el puente después del commit, JPA y Flyway (V23). Datos propios con prefijo {@code completa-} y
 * dispositivos {@code esp32-comp*}. Helecho: humedad 60-85, luz 150-800, temperatura 16-26.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MascotaCompletaIntegracionTest {

    private static final String CLAVE = "clave-larga-1";
    private static final String SERIAL = "esp32-comp1";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EmisorTokenDispositivoPort emisor;
    @Autowired private RepositorioAccesos accesos;
    @Autowired private EntityManagerFactory fabrica;
    @SpyBean private PublicadorDeMascotaPorWebSocket vivo;

    private String dueno;
    private String invitado;
    private String planta;
    private String token;   // del dispositivo SERIAL, vinculado a planta

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        dueno = registrarEIniciarSesion();
        invitado = registrarEIniciarSesion();
        planta = crearPlanta("Pepa");
        accesos.otorgar(AccesoCompartido.otorgadoAhora(UUID.fromString(planta), UUID.fromString(idDe(invitado))));
        token = vincular(planta, SERIAL);
        reset(vivo);
    }

    @AfterEach
    void limpiar() {
        String plantas = "select p.id from planta p join usuario u on u.id = p.dueno_id where u.correo like 'completa-%'";
        jdbc.update("delete from dispositivo_salud where serial like 'esp32-comp%'");
        jdbc.update("delete from dispositivo where serial like 'esp32-comp%'");
        jdbc.update("delete from diagnostico_planta where planta_id in (" + plantas + ")");
        jdbc.update("delete from acceso_compartido where planta_id in (" + plantas + ")");
        jdbc.update("delete from planta where dueno_id in (select id from usuario where correo like 'completa-%')");
        jdbc.update("delete from usuario_rol where usuario_id in (select id from usuario where correo like 'completa-%')");
        jdbc.update("delete from usuario where correo like 'completa-%'");
    }

    // ---- utilidades ----

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder p, String cuerpo) {
        return p.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder p, String token) {
        return p.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private static String leer(ResultActions r, String ruta) throws Exception {
        return String.valueOf((Object) JsonPath.read(r.andReturn().getResponse().getContentAsString(), ruta));
    }

    private String registrarEIniciarSesion() throws Exception {
        String correo = "completa-" + UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
        mockMvc.perform(json(post("/api/v1/auth/registro"),
                "{\"nombre\":\"Completa\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isCreated());
        return leer(mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}")).andExpect(status().isOk()), "$.token");
    }

    private String idDe(String tokenDePersona) throws Exception {
        return leer(mockMvc.perform(con(get("/api/v1/cuenta"), tokenDePersona)).andExpect(status().isOk()), "$.id");
    }

    private String crearPlanta(String apodo) throws Exception {
        return leer(mockMvc.perform(json(con(post("/api/v1/plantas"), dueno),
                "{\"apodo\":\"" + apodo + "\",\"especie\":\"helecho\"}")).andExpect(status().isCreated()), "$.id");
    }

    /** El token se emite directo por el puerto; desde la D96 el dispositivo debe seguir reclamado por la planta. */
    private String vincular(String plantaId, String serial) {
        jdbc.update("delete from dispositivo where serial = ?", serial);
        jdbc.update("insert into dispositivo (serial, secreto, planta_id, reclamado_en, creado_en, registrado_por) "
                + "values (?, 's', ?, now(), now(), 'ADMIN')", serial, UUID.fromString(plantaId));
        return emisor.emitir(serial, UUID.fromString(plantaId)).valor();
    }

    private ResultActions lectura(String tokenDeDispositivo, String serial, Instant marca, int humedad) throws Exception {
        return mockMvc.perform(json(con(post("/api/v1/dispositivos/" + serial + "/lecturas"), tokenDeDispositivo),
                "{\"marcaDeTiempo\":\"" + marca + "\",\"humedad\":" + humedad + ",\"luz\":500,\"temperatura\":22}"));
    }

    private ResultActions lectura(int humedad) throws Exception {
        return lectura(token, SERIAL, Instant.now().minusSeconds(1), humedad);
    }

    private ResultActions acariciarDesdeLaMatera(String tokenDeDispositivo, String serial, String cuerpo) throws Exception {
        return mockMvc.perform(json(con(post("/api/v1/dispositivos/" + serial + "/mascota/acciones"), tokenDeDispositivo),
                cuerpo));
    }

    private ResultActions acariciarDesdeElFront() throws Exception {
        return mockMvc.perform(json(con(post("/api/v1/plantas/" + planta + "/mascota/acciones"), dueno),
                "{\"tipo\":\"ACARICIAR\"}"));
    }

    private ResultActions accesorio(String tokenDePersona, String plantaId, String cuerpo) throws Exception {
        return mockMvc.perform(json(con(patch("/api/v1/plantas/" + plantaId + "/mascota"), tokenDePersona), cuerpo));
    }

    // ---- A: la respuesta de la lectura lleva la mascota ----

    @Test
    void laPrimeraLecturaTraeLaMascotaSinEventosNiAccesorio() throws Exception {
        lectura(70).andExpect(status().isCreated())
                .andExpect(jsonPath("$.mascota.vitalidad").value(80))
                .andExpect(jsonPath("$.mascota.animo").value("FELIZ"))
                .andExpect(jsonPath("$.mascota.racha").value(0))
                .andExpect(jsonPath("$.mascota.accesorio").value("NINGUNO"))
                .andExpect(jsonPath("$.mascota.eventos").isArray())
                .andExpect(jsonPath("$.mascota.eventos.length()").value(0));
    }

    @Test
    void elEventoQueProdujoLaMismaLecturaYaVieneEnSuRespuesta() throws Exception {
        Instant base = Instant.now().minus(10, ChronoUnit.MINUTES);
        for (int i = 0; i < 4; i++) {
            lectura(token, SERIAL, base.plusSeconds(60L * i), 40).andExpect(status().isCreated());
        }
        lectura(token, SERIAL, base.plusSeconds(60 * 4), 75).andExpect(status().isCreated())
                .andExpect(jsonPath("$.mascota.eventos.length()").value(0));   // una sola lectura alta no dispara

        lectura(token, SERIAL, base.plusSeconds(60 * 5), 76).andExpect(status().isCreated())
                .andExpect(jsonPath("$.mascota.eventos[*].tipo").value(hasItem("REGADA")))
                .andExpect(jsonPath("$.mascota.eventos[0].ocurridoEn").exists());

        // y no se repite en la lectura siguiente: solo salen los registrados después de la anterior
        lectura(token, SERIAL, base.plusSeconds(60 * 6), 77).andExpect(status().isCreated())
                .andExpect(jsonPath("$.mascota.eventos.length()").value(0));
    }

    @Test
    void laCariciaDelFrontLlegaALaMateraPorHttpEnSuSiguienteLecturaUnaSolaVez() throws Exception {
        lectura(70).andExpect(status().isCreated());
        acariciarDesdeElFront().andExpect(status().isCreated());

        lectura(70).andExpect(status().isCreated())
                .andExpect(jsonPath("$.mascota.eventos.length()").value(1))
                .andExpect(jsonPath("$.mascota.eventos[0].tipo").value("ACARICIADA"));
        lectura(70).andExpect(status().isCreated())
                .andExpect(jsonPath("$.mascota.eventos.length()").value(0));
    }

    // ---- B: la matera acaricia ----

    @Test
    void laMateraAcariciaCon201YLaSegundaDentroDelEnfriamientoEs200Repetida() throws Exception {
        lectura(70).andExpect(status().isCreated());

        ResultActions primera = acariciarDesdeLaMatera(token, SERIAL, "{\"tipo\":\"ACARICIAR\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ACARICIADA"))
                .andExpect(jsonPath("$.repetida").value(false))
                .andExpect(jsonPath("$.vitalidad").value(80))
                .andExpect(jsonPath("$.animo").value("FELIZ"))
                .andExpect(jsonPath("$.ocurridoEn").exists());
        String id = leer(primera, "$.id");

        acariciarDesdeLaMatera(token, SERIAL, "{\"tipo\":\"ACARICIAR\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.repetida").value(true))
                .andExpect(jsonPath("$.tipo").value("ACARICIADA"));
        assertThat(jdbc.queryForObject("select count(*) from evento_mascota where planta_id = ? and tipo = 'ACARICIADA'",
                Integer.class, UUID.fromString(planta))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select detalle from evento_mascota where planta_id = ?", String.class,
                UUID.fromString(planta))).contains("\"origen\":\"MATERA\"");
    }

    @Test
    void laCariciaDeLaMateraAvisaAlFrontConOrigenMateraYElDelFrontConOrigenPersona() throws Exception {
        acariciarDesdeLaMatera(token, SERIAL, "{\"tipo\":\"ACARICIAR\"}").andExpect(status().isCreated());

        ArgumentCaptor<AvisoDeMascota> aviso = ArgumentCaptor.forClass(AvisoDeMascota.class);
        verify(vivo, atLeastOnce()).publicar(aviso.capture());
        assertThat(aviso.getValue().evento().detalle()).containsEntry("origen", "MATERA");
        assertThat(aviso.getValue().seriales()).doesNotContain(SERIAL);   // no se le devuelve a la matera que la originó
    }

    @Test
    void laRutaDeLaMateraRechazaSinTokenConPersonaYConOtroDispositivo() throws Exception {
        String cuerpo = "{\"tipo\":\"ACARICIAR\"}";
        mockMvc.perform(json(post("/api/v1/dispositivos/" + SERIAL + "/mascota/acciones"), cuerpo))
                .andExpect(status().isUnauthorized());
        acariciarDesdeLaMatera(dueno, SERIAL, cuerpo).andExpect(status().isForbidden());

        String otraPlanta = crearPlanta("Otra");
        String tokenAjeno = vincular(otraPlanta, "esp32-comp2");
        acariciarDesdeLaMatera(tokenAjeno, SERIAL, cuerpo).andExpect(status().isForbidden());
    }

    @Test
    void unaMateraYaDesvinculadaRecibe401() throws Exception {
        jdbc.update("delete from dispositivo where serial = ?", SERIAL);

        acariciarDesdeLaMatera(token, SERIAL, "{\"tipo\":\"ACARICIAR\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void declararUnRiegoDesdeLaMateraEs400() throws Exception {
        acariciarDesdeLaMatera(token, SERIAL, "{\"tipo\":\"LA_REGUE\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
        acariciarDesdeLaMatera(token, SERIAL, "{}").andExpect(status().isBadRequest());
        acariciarDesdeLaMatera(token, SERIAL, "{\"tipo\":\"BAILAR\"}").andExpect(status().isBadRequest());
    }

    @Test
    void laCariciaDelFrontSaleConOrigenPersona() throws Exception {
        acariciarDesdeElFront().andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("select detalle from evento_mascota where planta_id = ?", String.class,
                UUID.fromString(planta))).contains("\"origen\":\"PERSONA\"");
    }

    // ---- C: accesorio ----

    @Test
    void elDuenoCambiaElAccesorioYSinFilaDeMascotaLaCreaConLaVitalidadInicial() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from mascota where planta_id = ?", Integer.class,
                UUID.fromString(planta))).isZero();

        accesorio(dueno, planta, "{\"accesorio\":\"SOMBRERO\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.accesorio").value("SOMBRERO"))
                .andExpect(jsonPath("$.vitalidad").value(80))
                .andExpect(jsonPath("$.hayLecturas").value(false))
                .andExpect(jsonPath("$.animo").value("SIN_DATOS"));

        assertThat(jdbc.queryForObject("select accesorio from mascota where planta_id = ?", String.class,
                UUID.fromString(planta))).isEqualTo("SOMBRERO");
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/mascota"), invitado)).andExpect(status().isOk())
                .andExpect(jsonPath("$.accesorio").value("SOMBRERO"))
                .andExpect(jsonPath("$.ultimosEventos.length()").value(0));   // no es un evento de la línea de tiempo
    }

    @Test
    void elAccesorioLlegaALaMateraConSuSiguienteLecturaYSobreviveAEllas() throws Exception {
        lectura(70).andExpect(status().isCreated());
        accesorio(dueno, planta, "{\"accesorio\":\"GAFAS\"}").andExpect(status().isOk());

        lectura(70).andExpect(status().isCreated()).andExpect(jsonPath("$.mascota.accesorio").value("GAFAS"));
        lectura(70).andExpect(status().isCreated()).andExpect(jsonPath("$.mascota.accesorio").value("GAFAS"));
    }

    @Test
    void elInvitadoNoCambiaElAccesorio_elAjenoEs404_yUnValorRaroEs400() throws Exception {
        accesorio(invitado, planta, "{\"accesorio\":\"MONO\"}").andExpect(status().isForbidden());
        String ajeno = registrarEIniciarSesion();
        accesorio(ajeno, planta, "{\"accesorio\":\"MONO\"}").andExpect(status().isNotFound());
        accesorio(dueno, planta, "{\"accesorio\":\"CORONA\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
        accesorio(dueno, planta, "{}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from mascota where planta_id = ?", Integer.class,
                UUID.fromString(planta))).isZero();
    }

    @Test
    void cambiarElAccesorioAvisaPorWebSocketConElTipoNuevo() throws Exception {
        accesorio(dueno, planta, "{\"accesorio\":\"MONO\"}").andExpect(status().isOk());

        ArgumentCaptor<ActualizacionDeMascota> cambio = ArgumentCaptor.forClass(ActualizacionDeMascota.class);
        verify(vivo, atLeastOnce()).publicarActualizacion(cambio.capture());
        assertThat(cambio.getValue().accesorio()).isEqualTo(Accesorio.MONO);
        assertThat(cambio.getValue().destinatarios()).hasSize(2);   // dueño e invitado
    }

    // ---- D: el tablero en una llamada ----

    @Test
    void elListadoTraeMascotaYSaludDeLaPlantaQueLasTieneYNullEnLaQueNo() throws Exception {
        String sola = crearPlanta("Sola");
        lectura(70).andExpect(status().isCreated());
        accesorio(dueno, planta, "{\"accesorio\":\"SOMBRERO\"}").andExpect(status().isOk());
        mockMvc.perform(json(con(post("/api/v1/dispositivos/" + SERIAL + "/salud"), token),
                "{\"sensores\":{\"humedad\":\"OK\",\"luz\":\"OK\",\"temperatura\":\"FALLA\"}}")).andExpect(status().isOk());

        mockMvc.perform(con(get("/api/v1/plantas"), dueno)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + planta + "')].mascota.vitalidad").value(80))
                .andExpect(jsonPath("$[?(@.id=='" + planta + "')].mascota.animo").value("FELIZ"))
                .andExpect(jsonPath("$[?(@.id=='" + planta + "')].mascota.racha").value(0))
                .andExpect(jsonPath("$[?(@.id=='" + planta + "')].mascota.accesorio").value("SOMBRERO"))
                .andExpect(jsonPath("$[?(@.id=='" + planta + "')].salud.estado").value("FALLA"))
                .andExpect(jsonPath("$[?(@.id=='" + planta + "')].salud.mensaje").value("no te siento la temperatura"))
                .andExpect(jsonPath("$[?(@.id=='" + sola + "')].mascota").value((Object) null))
                .andExpect(jsonPath("$[?(@.id=='" + sola + "')].salud").value((Object) null));
    }

    @Test
    void laPeorSaludDeLasMaterasDeLaPlantaEsLaQueSeMuestra() throws Exception {
        lectura(70).andExpect(status().isCreated());   // SERIAL queda OK
        String segundo = vincular(planta, "esp32-comp3");
        mockMvc.perform(json(con(post("/api/v1/dispositivos/esp32-comp3/salud"), segundo),
                "{\"sensores\":{\"humedad\":\"FALLA\",\"luz\":\"OK\",\"temperatura\":\"OK\"}}")).andExpect(status().isOk());

        mockMvc.perform(con(get("/api/v1/plantas"), dueno)).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + planta + "')].salud.estado").value("FALLA"))
                .andExpect(jsonPath("$[?(@.id=='" + planta + "')].salud.mensaje").value("no te siento la humedad"));
    }

    @Test
    void elListadoDeCompartidasTambienTraeMascotaYSalud() throws Exception {
        lectura(70).andExpect(status().isCreated());

        mockMvc.perform(con(get("/api/v1/plantas/compartidas"), invitado)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(planta))
                .andExpect(jsonPath("$[0].esDueno").value(false))
                .andExpect(jsonPath("$[0].mascota.vitalidad").value(80))
                .andExpect(jsonPath("$[0].salud.estado").value("OK"));
    }

    @Test
    void elNumeroDeSentenciasDelListadoNoCreceConLaCantidadDePlantas() throws Exception {
        Statistics estadisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
        estadisticas.setStatisticsEnabled(true);

        // Una planta con mascota y salud: la medición base. Los resultados se descartan: se mide el segundo.
        lectura(70).andExpect(status().isCreated());
        mockMvc.perform(con(get("/api/v1/plantas"), dueno)).andExpect(status().isOk());   // calienta
        estadisticas.clear();
        mockMvc.perform(con(get("/api/v1/plantas"), dueno)).andExpect(status().isOk());
        long conUna = estadisticas.getPrepareStatementCount();

        // Seis plantas más, cada una con su matera, su lectura (mascota) y su salud.
        for (int i = 0; i < 6; i++) {
            String otra = crearPlanta("P" + i);
            String serial = "esp32-comp-x" + i;
            String t = vincular(otra, serial);
            lectura(t, serial, Instant.now().minusSeconds(1), 70).andExpect(status().isCreated());
        }
        mockMvc.perform(con(get("/api/v1/plantas"), dueno)).andExpect(status().isOk());   // calienta
        estadisticas.clear();
        mockMvc.perform(con(get("/api/v1/plantas"), dueno)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[*].mascota.vitalidad").value(hasItem(80)));
        long conSiete = estadisticas.getPrepareStatementCount();

        System.out.println("MEDICION listado GET /plantas con mascota y salud: 1 planta=" + conUna + " sentencias, 7 plantas="
                + conSiete + " sentencias");
        assertThat(conSiete).isEqualTo(conUna);
    }
}
