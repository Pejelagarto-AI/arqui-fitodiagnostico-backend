package com.vivero.fitodiagnostico;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El recorrido completo con el contexto real: Spring Security, JWT, BCrypt,
 * JPA, Flyway y los servicios de aplicación, sin simulacros. Es la prueba de
 * que las piezas que las demás pruebas verifican por separado encajan.
 *
 * <p>La base H2 en memoria es compartida con otras pruebas de la misma JVM
 * (p. ej. el contrato del catálogo espera exactamente las 5 especies del
 * Anexo B), así que esta clase solo toca datos propios con prefijo
 * {@code flujo-} y los borra al terminar. El administrador de arranque
 * ({@code admin@pruebas...}) viene de {@code application-test.yml} y se usa
 * solo para leer y para probar la regla del último administrador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlujoDeCuentasIntegracionTest {

    private static final String ADMIN_CORREO = "admin@pruebas.fitodiagnostico.local";
    private static final String ADMIN_CONTRASENA = "contrasena-de-pruebas-1";
    private static final String CLAVE = "clave-larga-1";
    private static final String RANGOS = "\"rangos\":{\"humedad\":{\"min\":30,\"max\":60},"
            + "\"luz\":{\"min\":500,\"max\":1000},\"temperatura\":{\"min\":18,\"max\":26}}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void limpiarLoCreado() {
        jdbc.update("delete from usuario_rol where usuario_id in (select id from usuario where correo like 'flujo-%')");
        jdbc.update("delete from usuario where correo like 'flujo-%'");
        jdbc.update("delete from especie where nombre like 'flujo-%'");
    }

    // ---- utilidades ----

    private static String correoUnico() {
        return "flujo-" + UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String leer(ResultActions resultado, String ruta) throws Exception {
        MvcResult r = resultado.andReturn();
        return String.valueOf((Object) JsonPath.read(r.getResponse().getContentAsString(), ruta));
    }

    private void registrar(String correo, String contrasena) throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/registro"),
                        "{\"nombre\":\"Flujo\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isCreated());
    }

    private String iniciarSesion(String correo, String contrasena) throws Exception {
        return leer(mockMvc.perform(json(post("/api/v1/auth/sesion"),
                        "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isOk()), "$.token");
    }

    private String registrarEIniciarSesion(String correo) throws Exception {
        registrar(correo, CLAVE);
        return iniciarSesion(correo, CLAVE);
    }

    private String tokenDeAdministrador() throws Exception {
        return iniciarSesion(ADMIN_CORREO, ADMIN_CONTRASENA);
    }

    private String idDe(String token) throws Exception {
        return leer(mockMvc.perform(conToken(get("/api/v1/cuenta"), token)).andExpect(status().isOk()), "$.id");
    }

    // ---- cuentas ----

    @Test
    void registrarseIniciarSesionYConsultarLaPropiaCuenta() throws Exception {
        String correo = correoUnico();
        String token = registrarEIniciarSesion(correo);

        mockMvc.perform(conToken(get("/api/v1/cuenta"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(correo))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("CLIENTE"));
    }

    @Test
    void laContrasenaSeGuardaCifradaConBcrypt() throws Exception {
        String correo = correoUnico();
        registrar(correo, CLAVE);

        String almacenado = jdbc.queryForObject("select hash_contrasena from usuario where correo = ?", String.class, correo);

        assertThat(almacenado).startsWith("$2").doesNotContain(CLAVE);
    }

    @Test
    void unCorreoRepetidoDa409AunqueCambienLasMayusculas() throws Exception {
        String correo = correoUnico();
        registrar(correo, CLAVE);

        mockMvc.perform(json(post("/api/v1/auth/registro"),
                        "{\"nombre\":\"Otra\",\"correo\":\"" + correo.toUpperCase() + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CORREO_YA_REGISTRADO"));
    }

    @Test
    void contrasenaErroneaYCorreoInexistenteDanLaMismaRespuesta() throws Exception {
        String correo = correoUnico();
        registrar(correo, CLAVE);

        String conCorreoReal = mockMvc.perform(json(post("/api/v1/auth/sesion"),
                        "{\"correo\":\"" + correo + "\",\"contrasena\":\"incorrecta-123\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String conCorreoFalso = mockMvc.perform(json(post("/api/v1/auth/sesion"),
                        "{\"correo\":\"flujo-no-existe@vivero.co\",\"contrasena\":\"incorrecta-123\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(conCorreoReal).isEqualTo(conCorreoFalso).contains("CREDENCIALES_INVALIDAS");
    }

    @Test
    void cambiarLaContrasenaInvalidaLaAntiguaYExigeLaActual() throws Exception {
        String correo = correoUnico();
        String token = registrarEIniciarSesion(correo);

        mockMvc.perform(conToken(json(put("/api/v1/cuenta/contrasena"),
                        "{\"contrasenaActual\":\"equivocada-1\",\"contrasenaNueva\":\"clave-nueva-22\"}"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CONTRASENA_ACTUAL_INCORRECTA"));

        mockMvc.perform(conToken(json(put("/api/v1/cuenta/contrasena"),
                        "{\"contrasenaActual\":\"" + CLAVE + "\",\"contrasenaNueva\":\"clave-nueva-22\"}"), token))
                .andExpect(status().isNoContent());

        mockMvc.perform(json(post("/api/v1/auth/sesion"), "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(iniciarSesion(correo, "clave-nueva-22")).isNotBlank();
    }

    @Test
    void editarLosPropiosDatosYElCorreoNuevoEsElQueInicia() throws Exception {
        String correo = correoUnico();
        String nuevo = correoUnico();
        String token = registrarEIniciarSesion(correo);

        mockMvc.perform(conToken(json(put("/api/v1/cuenta"),
                        "{\"nombre\":\"Nuevo Nombre\",\"correo\":\"" + nuevo + "\"}"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nuevo Nombre"));

        assertThat(iniciarSesion(nuevo, CLAVE)).isNotBlank();
    }

    @Test
    void despuesDeEliminarLaCuentaElTokenDejaDeServir() throws Exception {
        String token = registrarEIniciarSesion(correoUnico());

        mockMvc.perform(conToken(delete("/api/v1/cuenta"), token)).andExpect(status().isNoContent());

        // El token sigue firmado y sin caducar, pero la cuenta ya no existe: 401.
        mockMvc.perform(conToken(get("/api/v1/cuenta"), token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("NO_AUTENTICADO"));
    }

    // ---- administración ----

    @Test
    void unClienteNoEntraALaAdministracionPeroUnAdministradorSi() throws Exception {
        String correo = correoUnico();
        String cliente = registrarEIniciarSesion(correo);

        mockMvc.perform(conToken(get("/api/v1/admin/usuarios"), cliente))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESO_DENEGADO"));
        mockMvc.perform(conToken(get("/api/v1/admin/usuarios").param("q", correo), tokenDeAdministrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].correo").value(correo));
    }

    @Test
    void otorgarYRevocarElRolSurteEfectoAlInstanteConElMismoToken() throws Exception {
        String cliente = registrarEIniciarSesion(correoUnico());
        String id = idDe(cliente);
        String admin = tokenDeAdministrador();
        mockMvc.perform(conToken(get("/api/v1/admin/usuarios"), cliente)).andExpect(status().isForbidden());

        mockMvc.perform(conToken(put("/api/v1/admin/usuarios/" + id + "/rol-administrador"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));
        // Mismo token, sin volver a iniciar sesión: ya es administrador.
        mockMvc.perform(conToken(get("/api/v1/admin/usuarios"), cliente)).andExpect(status().isOk());

        mockMvc.perform(conToken(delete("/api/v1/admin/usuarios/" + id + "/rol-administrador"), admin))
                .andExpect(status().isOk());
        // Mismo token otra vez: ya no lo es. Con roles dentro del JWT esto seguiría siendo 200 hasta que caducara.
        mockMvc.perform(conToken(get("/api/v1/admin/usuarios"), cliente)).andExpect(status().isForbidden());
    }

    @Test
    void elUnicoAdministradorNoPuedeQuedarseSinElRolNiSerEliminadoPorNingunCamino() throws Exception {
        String admin = tokenDeAdministrador();
        String id = idDe(admin);

        // Los tres caminos de la RN4 (D18): revocar el rol, eliminar desde administración
        // y eliminar la propia cuenta. Los tres terminan en el mismo 409.
        mockMvc.perform(conToken(delete("/api/v1/admin/usuarios/" + id + "/rol-administrador"), admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ULTIMO_ADMINISTRADOR"));
        mockMvc.perform(conToken(delete("/api/v1/admin/usuarios/" + id), admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ULTIMO_ADMINISTRADOR"));
        mockMvc.perform(conToken(delete("/api/v1/cuenta"), admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ULTIMO_ADMINISTRADOR"));

        mockMvc.perform(conToken(get("/api/v1/cuenta"), admin)).andExpect(status().isOk());
    }

    @Test
    void conUnSegundoAdministradorYaSePuedeRevocarAlPrimero() throws Exception {
        String clienteToken = registrarEIniciarSesion(correoUnico());
        String clienteId = idDe(clienteToken);
        String admin = tokenDeAdministrador();
        mockMvc.perform(conToken(put("/api/v1/admin/usuarios/" + clienteId + "/rol-administrador"), admin))
                .andExpect(status().isOk());

        // Ahora hay dos: el nuevo administrador puede quitarle el rol al de arranque... y devolvérselo.
        String adminId = idDe(admin);
        mockMvc.perform(conToken(delete("/api/v1/admin/usuarios/" + adminId + "/rol-administrador"), clienteToken))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(put("/api/v1/admin/usuarios/" + adminId + "/rol-administrador"), clienteToken))
                .andExpect(status().isOk());
    }

    @Test
    void elAdministradorEditaYEliminaAOtroUsuario() throws Exception {
        String correo = correoUnico();
        String id = idDe(registrarEIniciarSesion(correo));
        String admin = tokenDeAdministrador();

        mockMvc.perform(conToken(json(put("/api/v1/admin/usuarios/" + id),
                        "{\"nombre\":\"Editado\",\"correo\":\"" + correo + "\"}"), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Editado"));
        mockMvc.perform(conToken(get("/api/v1/admin/usuarios/" + id), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.correo").value(correo));
        mockMvc.perform(conToken(delete("/api/v1/admin/usuarios/" + id), admin)).andExpect(status().isNoContent());
        mockMvc.perform(conToken(get("/api/v1/admin/usuarios/" + id), admin))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("USUARIO_NO_ENCONTRADO"));
    }

    // ---- catálogo de especies ----

    @Test
    void desactivarUnaEspecieLaSacaDelListadoPublicoPeroSigueDiagnosticandose() throws Exception {
        String nombre = "flujo-" + UUID.randomUUID().toString().substring(0, 8);
        String admin = tokenDeAdministrador();

        mockMvc.perform(conToken(json(post("/api/v1/admin/especies"),
                        "{\"nombre\":\"" + nombre + "\"," + RANGOS + "}"), admin))
                .andExpect(status().isCreated());
        assertThat(listadoPublico()).contains(nombre);

        mockMvc.perform(conToken(post("/api/v1/admin/especies/" + nombre + "/desactivar"), admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(false));

        // RF-C1 / RN11: ya no se ofrece...
        assertThat(listadoPublico()).doesNotContain(nombre);
        // ... RF-C2: el administrador sigue viéndola...
        mockMvc.perform(conToken(get("/api/v1/admin/especies"), admin))
                .andExpect(jsonPath("$[?(@.nombre=='" + nombre + "')].activa").value(false));
        // ... y RN11: las plantas que ya la usan se siguen diagnosticando, sin sesión.
        mockMvc.perform(json(post("/api/v1/diagnosticos"),
                        "{\"especie\":\"" + nombre + "\",\"humedad\":45,\"luz\":700,\"temperatura\":22}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SALUDABLE"));

        mockMvc.perform(conToken(post("/api/v1/admin/especies/" + nombre + "/reactivar"), admin))
                .andExpect(status().isOk());
        assertThat(listadoPublico()).contains(nombre);
    }

    @Test
    void crearUnaEspecieRepetidaDa409YEditarSusRangosCambiaElDiagnostico() throws Exception {
        String nombre = "flujo-" + UUID.randomUUID().toString().substring(0, 8);
        String admin = tokenDeAdministrador();
        mockMvc.perform(conToken(json(post("/api/v1/admin/especies"), "{\"nombre\":\"" + nombre + "\"," + RANGOS + "}"), admin))
                .andExpect(status().isCreated());

        mockMvc.perform(conToken(json(post("/api/v1/admin/especies"),
                        "{\"nombre\":\"" + nombre.toUpperCase() + "\"," + RANGOS + "}"), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ESPECIE_YA_EXISTE"));

        String lectura = "{\"especie\":\"" + nombre + "\",\"humedad\":80,\"luz\":700,\"temperatura\":22}";
        mockMvc.perform(json(post("/api/v1/diagnosticos"), lectura))
                .andExpect(jsonPath("$.parametros[0].estado").value("ALTO"));
        mockMvc.perform(conToken(json(put("/api/v1/admin/especies/" + nombre),
                        "{\"rangos\":{\"humedad\":{\"min\":60,\"max\":90},\"luz\":{\"min\":500,\"max\":1000},"
                                + "\"temperatura\":{\"min\":18,\"max\":26}}}"), admin))
                .andExpect(status().isOk());
        mockMvc.perform(json(post("/api/v1/diagnosticos"), lectura))
                .andExpect(jsonPath("$.parametros[0].estado").value("OPTIMO"));
    }

    @Test
    void unaEspecieConUnRangoFueraDelSensorOIncompletoDa400() throws Exception {
        String admin = tokenDeAdministrador();

        mockMvc.perform(conToken(json(post("/api/v1/admin/especies"),
                        "{\"nombre\":\"flujo-mala\",\"rangos\":{\"humedad\":{\"min\":50,\"max\":150},"
                                + "\"luz\":{\"min\":500,\"max\":1000},\"temperatura\":{\"min\":18,\"max\":26}}}"), admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(json(post("/api/v1/admin/especies"),
                        "{\"nombre\":\"flujo-mala\",\"rangos\":{\"humedad\":{\"min\":30,\"max\":60}}}"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
    }

    private String listadoPublico() throws Exception {
        return mockMvc.perform(get("/api/v1/especies")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ---- lo público sigue público ----

    @Test
    void lasRutasPublicasFuncionanSinToken() throws Exception {
        mockMvc.perform(get("/api/v1/especies")).andExpect(status().isOk());
        mockMvc.perform(json(post("/api/v1/diagnosticos"),
                        "{\"especie\":\"sansevieria\",\"humedad\":32.5,\"luz\":850,\"temperatura\":21}"))
                .andExpect(status().isOk());
    }

    @Test
    void elPrimerAdministradorSeCreoAlArrancarConRolesClienteYAdministrador() throws Exception {
        mockMvc.perform(conToken(get("/api/v1/cuenta"), tokenDeAdministrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(ADMIN_CORREO))
                .andExpect(jsonPath("$.roles[0]").value("CLIENTE"))
                .andExpect(jsonPath("$.roles[1]").value("ADMINISTRADOR"));
    }
}
