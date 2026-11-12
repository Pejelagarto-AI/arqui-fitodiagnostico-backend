package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D96, el traspaso de una matera de un usuario a otro con el contexto real: el token que el chip ya tenía deja de
 * valer apenas se desvincula (no una hora después), y la matera solo reporta a la planta nueva cuando renueva.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TraspasoDeDispositivoIntegracionTest {

    private static final String CLAVE = "clave-larga-1";
    private static final String PREFIJO = "tras-";
    private static final String LECTURA = "{\"marcaDeTiempo\":\"2026-11-05T10:00:00Z\",\"humedad\":55,\"luz\":700,\"temperatura\":22}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    private String duenoA;
    private String duenoB;
    private String plantaA;
    private String plantaB;
    private String serial;
    private String secreto;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        String admin = leer(mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"admin@pruebas.fitodiagnostico.local\",\"contrasena\":\"contrasena-de-pruebas-1\"}"))
                .andExpect(status().isOk()), "$.token");
        duenoA = registrarEIniciarSesion();
        duenoB = registrarEIniciarSesion();
        plantaA = crearPlanta(duenoA);
        plantaB = crearPlanta(duenoB);
        serial = PREFIJO + UUID.randomUUID().toString().substring(0, 6);
        secreto = leer(mockMvc.perform(json(con(post("/api/v1/admin/dispositivos"), admin), "{\"serial\":\"" + serial + "\"}"))
                .andExpect(status().isCreated()), "$.secreto");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("delete from dispositivo where serial like 'tras-%'");
        jdbc.update("delete from diagnostico_planta where planta_id in (select p.id from planta p "
                + "join usuario u on u.id = p.dueno_id where u.correo like 'tras-%')");
        jdbc.update("delete from planta where dueno_id in (select id from usuario where correo like 'tras-%')");
        jdbc.update("delete from usuario_rol where usuario_id in (select id from usuario where correo like 'tras-%')");
        jdbc.update("delete from usuario where correo like 'tras-%'");
    }

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
        String correo = PREFIJO + UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
        mockMvc.perform(json(post("/api/v1/auth/registro"),
                "{\"nombre\":\"Tras\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}")).andExpect(status().isCreated());
        return leer(mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}")).andExpect(status().isOk()), "$.token");
    }

    private String crearPlanta(String token) throws Exception {
        return leer(mockMvc.perform(json(con(post("/api/v1/plantas"), token),
                "{\"apodo\":\"Pepa\",\"especie\":\"potos\"}")).andExpect(status().isCreated()), "$.id");
    }

    private void reclamar(String token, String planta) throws Exception {
        mockMvc.perform(json(con(post("/api/v1/plantas/" + planta + "/dispositivos"), token), "{\"serial\":\"" + serial + "\"}"))
                .andExpect(status().isCreated());
    }

    private void desvincular(String token, String planta) throws Exception {
        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/dispositivos/" + serial), token)).andExpect(status().isNoContent());
    }

    private String respuesta(String reto) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(reto.getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions pedirCredencial() throws Exception {
        String reto = leer(mockMvc.perform(post("/api/v1/dispositivos/" + serial + "/reto")).andExpect(status().isOk()), "$.reto");
        return mockMvc.perform(json(post("/api/v1/dispositivos/" + serial + "/credencial"),
                "{\"respuesta\":\"" + respuesta(reto) + "\"}"));
    }

    private String tokenDelChip() throws Exception {
        return leer(pedirCredencial().andExpect(status().isOk()), "$.token");
    }

    private ResultActions lectura(String token) throws Exception {
        return mockMvc.perform(json(con(post("/api/v1/dispositivos/" + serial + "/lecturas"), token), LECTURA));
    }

    private int diagnosticosDe(String planta) {
        return jdbc.queryForObject("select count(*) from diagnostico_planta where planta_id = ?", Integer.class, UUID.fromString(planta));
    }

    @Test
    void laCredencialYLaLecturaTraenElApodoDeLaPlantaYUnCambioDeNombreLlegaEnLaSiguienteLectura() throws Exception {
        reclamar(duenoA, plantaA);
        ResultActions credencial = pedirCredencial().andExpect(status().isOk()).andExpect(jsonPath("$.apodo").value("Pepa"));
        String token = leer(credencial, "$.token");
        lectura(token).andExpect(status().isCreated()).andExpect(jsonPath("$.apodo").value("Pepa"));

        mockMvc.perform(json(con(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/v1/plantas/" + plantaA), duenoA), "{\"apodo\":\"Poti\",\"especie\":\"potos\"}"))
                .andExpect(status().isOk());

        lectura(token).andExpect(status().isCreated()).andExpect(jsonPath("$.apodo").value("Poti"));
    }

    @Test
    void trasDesvincularLaLecturaConElTokenVigenteEs401CredencialRechazada() throws Exception {
        reclamar(duenoA, plantaA);
        String token = tokenDelChip();
        lectura(token).andExpect(status().isCreated());

        desvincular(duenoA, plantaA);

        lectura(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("CREDENCIAL_RECHAZADA"));
        assertThat(diagnosticosDe(plantaA)).isEqualTo(1);
        // al renovar, el chip se entera: sin dueño, 409 y vuelve a la pantalla del QR
        pedirCredencial().andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("DISPOSITIVO_SIN_RECLAMAR"));
    }

    @Test
    void reclamadoPorOtraPlantaElTokenViejoSigueRechazado() throws Exception {
        reclamar(duenoA, plantaA);
        String tokenViejo = tokenDelChip();
        desvincular(duenoA, plantaA);
        reclamar(duenoB, plantaB);

        lectura(tokenViejo).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("CREDENCIAL_RECHAZADA"));
        assertThat(diagnosticosDe(plantaA)).isZero();
        assertThat(diagnosticosDe(plantaB)).isZero();
    }

    @Test
    void elTraspasoCompletoLlevaLasLecturasALaPlantaDelNuevoDueno() throws Exception {
        reclamar(duenoA, plantaA);
        String tokenViejo = tokenDelChip();
        lectura(tokenViejo).andExpect(status().isCreated());
        desvincular(duenoA, plantaA);

        reclamar(duenoB, plantaB);
        // la planta nueva no la ve «conectada» con la fecha de la vieja
        mockMvc.perform(con(get("/api/v1/plantas/" + plantaB + "/dispositivos"), duenoB))
                .andExpect(jsonPath("$[0].ultimaCredencialEn").value((Object) null));

        String tokenNuevo = tokenDelChip();
        lectura(tokenNuevo).andExpect(status().isCreated());

        assertThat(diagnosticosDe(plantaB)).isEqualTo(1);
        assertThat(diagnosticosDe(plantaA)).isEqualTo(1);   // solo la de antes del traspaso
        lectura(tokenViejo).andExpect(status().isUnauthorized());
        mockMvc.perform(con(get("/api/v1/plantas/" + plantaB + "/dispositivos"), duenoB))
                .andExpect(jsonPath("$[0].ultimaCredencialEn").isNotEmpty());
    }
}
