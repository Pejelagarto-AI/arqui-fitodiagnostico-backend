package com.vivero.fitodiagnostico;

import com.jayway.jsonpath.JsonPath;
import com.vivero.fitodiagnostico.dobles.CorreoCapturado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lo que comparten las pruebas de contexto completo del bloque 4: cuentas con
 * prefijo {@code compartir-} (la H2 es compartida con otras pruebas de la JVM,
 * así que cada clase solo toca datos propios y los borra, hijos antes que
 * padres: las claves foráneas no tienen cascada), el MockMvc, el "SMTP" de pruebas
 * ({@link CorreoCapturado}) y los pasos de siempre (registrar, medir, compartir).
 * Las subclases declaran su propio {@code @SpringBootTest}.
 */
abstract class BaseDeIntegracionDeCompartir {

    protected static final String ADMIN_CORREO = "admin@pruebas.fitodiagnostico.local";
    protected static final String ADMIN_CONTRASENA = "contrasena-de-pruebas-1";
    protected static final String CLAVE = "clave-larga-1";
    protected static final String SALUDABLE = "{\"humedad\":55,\"luz\":700,\"temperatura\":22}";   // potos: 40-70, 300-1200, 18-30
    protected static final String CRITICA = "{\"humedad\":5,\"luz\":700,\"temperatura\":22}";
    protected static final String FRONT = "http://front.pruebas.local";

    /** Una cuenta de la prueba: con quién entra y quién es. */
    protected record Cuenta(String correo, String token, String id) { }

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected CorreoCapturado correo;

    @BeforeEach
    void limpiarAntes() {
        limpiar();
        correo.limpiar();
    }

    @AfterEach
    void limpiar() {
        String propias = "(select id from usuario where correo like 'compartir-%')";
        String plantasPropias = "(select p.id from planta p join usuario u on u.id = p.dueno_id where u.correo like 'compartir-%')";
        jdbc.update("delete from invitacion where planta_id in " + plantasPropias);
        jdbc.update("delete from restablecimiento where usuario_id in " + propias);
        jdbc.update("delete from solicitud_especie where solicitante_id in " + propias);
        jdbc.update("delete from diagnostico_planta where planta_id in " + plantasPropias);
        jdbc.update("delete from acceso_compartido where planta_id in " + plantasPropias);
        jdbc.update("delete from acceso_compartido where invitado_id in " + propias);
        jdbc.update("delete from planta where dueno_id in " + propias);
        jdbc.update("delete from usuario_rol where usuario_id in " + propias);
        jdbc.update("delete from usuario where correo like 'compartir-%'");
    }

    // ---- utilidades ----

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    protected static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected String leer(ResultActions resultado, String ruta) throws Exception {
        return String.valueOf((Object) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), ruta));
    }

    protected Cuenta registrar(String nombre) throws Exception {
        String correo = "compartir-" + UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
        mockMvc.perform(json(post("/api/v1/auth/registro"),
                "{\"nombre\":\"" + nombre + "\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isCreated());
        return iniciarSesion(correo, CLAVE);
    }

    protected Cuenta iniciarSesion(String correo, String contrasena) throws Exception {
        ResultActions sesion = mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}")).andExpect(status().isOk());
        return new Cuenta(correo, leer(sesion, "$.token"), leer(sesion, "$.usuario.id"));
    }

    protected String tokenDelAdministrador() throws Exception {
        return iniciarSesion(ADMIN_CORREO, ADMIN_CONTRASENA).token();
    }

    protected String crearPlanta(Cuenta de, String apodo, String especie) throws Exception {
        return leer(mockMvc.perform(json(con(post("/api/v1/plantas"), de.token()),
                "{\"apodo\":\"" + apodo + "\",\"especie\":\"" + especie + "\"}")).andExpect(status().isCreated()), "$.id");
    }

    protected ResultActions medir(Cuenta quien, String plantaId, String cuerpo) throws Exception {
        return mockMvc.perform(json(con(post("/api/v1/plantas/" + plantaId + "/mediciones"), quien.token()), cuerpo));
    }

    /** El dueño genera una invitación sin correo y devuelve el token. */
    protected String invitar(Cuenta dueno, String plantaId) throws Exception {
        return leer(mockMvc.perform(con(post("/api/v1/plantas/" + plantaId + "/invitaciones"), dueno.token()))
                .andExpect(status().isCreated()), "$.token");
    }

    /** El dueño invita y {@code invitado} acepta con su sesión: la forma corta de "dar acceso". */
    protected void compartir(Cuenta dueno, String plantaId, Cuenta invitado) throws Exception {
        mockMvc.perform(json(con(post("/api/v1/invitaciones/aceptar"), invitado.token()),
                "{\"token\":\"" + invitar(dueno, plantaId) + "\"}")).andExpect(status().isOk());
    }

    protected int contar(String tablaYFiltro) {
        return jdbc.queryForObject("select count(*) from " + tablaYFiltro, Integer.class);
    }

    /** Los correos que recibió esa dirección con ese asunto. */
    protected List<SimpleMailMessage> correosDe(String direccion, String fragmentoDelAsunto) {
        return correo.para(direccion).stream()
                .filter(m -> m.getSubject() != null && m.getSubject().contains(fragmentoDelAsunto)).toList();
    }

    protected static String tokenDelEnlace(SimpleMailMessage mensaje) {
        Matcher matcher = Pattern.compile("token=([A-Za-z0-9_%.-]+)").matcher(mensaje.getText());
        assertThat(matcher.find()).as("el correo debe traer un enlace con token: %s", mensaje.getText()).isTrue();
        return matcher.group(1);
    }
}
