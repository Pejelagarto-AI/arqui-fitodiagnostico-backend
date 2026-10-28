package com.vivero.fitodiagnostico;

import com.jayway.jsonpath.JsonPath;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.puerto.salida.Notificador;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El recorrido completo de plantas con el contexto real: seguridad JWT, los
 * servicios de aplicación, JPA y Flyway, sin simulacros salvo que el notificador
 * es un espía (conserva su comportamiento real, el log, y deja contar los avisos).
 *
 * <p>La H2 es compartida con otras pruebas de la JVM (el contrato del catálogo
 * espera exactamente 5 especies), así que esta clase solo toca datos propios con
 * prefijo {@code plantas-} y los borra al terminar, hijos antes que padres: las
 * claves foráneas no tienen cascada (V6, V7) y eso es lo que se quiere.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlujoDePlantasIntegracionTest {

    private static final String ADMIN_CORREO = "admin@pruebas.fitodiagnostico.local";
    private static final String ADMIN_CONTRASENA = "contrasena-de-pruebas-1";
    private static final String CLAVE = "clave-larga-1";

    private static final String SALUDABLE = "{\"humedad\":55,\"luz\":700,\"temperatura\":22}";   // potos: 40-70, 300-1200, 18-30
    private static final String CRITICA = "{\"humedad\":5,\"luz\":700,\"temperatura\":22}";
    private static final String EN_RIESGO = "{\"humedad\":38,\"luz\":700,\"temperatura\":22}";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RepositorioAccesos accesos;
    @SpyBean private Notificador notificador;

    private String dueno;
    private String invitado;
    private String ajeno;
    private String idDueno;
    private String idInvitado;

    @BeforeEach
    void preparar() throws Exception {
        limpiar();
        dueno = registrarEIniciarSesion();
        invitado = registrarEIniciarSesion();
        ajeno = registrarEIniciarSesion();
        idDueno = idDe(dueno);
        idInvitado = idDe(invitado);
        reset(notificador);
    }

    @AfterEach
    void limpiar() {
        jdbc.update("delete from diagnostico_planta where planta_id in (select p.id from planta p "
                + "join usuario u on u.id = p.dueno_id where u.correo like 'plantas-%')");
        jdbc.update("delete from acceso_compartido where planta_id in (select p.id from planta p "
                + "join usuario u on u.id = p.dueno_id where u.correo like 'plantas-%')");
        jdbc.update("delete from acceso_compartido where invitado_id in (select id from usuario where correo like 'plantas-%')");
        jdbc.update("delete from planta where dueno_id in (select id from usuario where correo like 'plantas-%')");
        jdbc.update("delete from usuario_rol where usuario_id in (select id from usuario where correo like 'plantas-%')");
        jdbc.update("delete from usuario where correo like 'plantas-%'");
        jdbc.update("delete from especie where nombre like 'plantas-%'");
    }

    // ---- utilidades ----

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private static MockHttpServletRequestBuilder con(MockHttpServletRequestBuilder peticion, String token) {
        return peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String leer(ResultActions resultado, String ruta) throws Exception {
        return String.valueOf((Object) JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), ruta));
    }

    private String registrarEIniciarSesion() throws Exception {
        String correo = "plantas-" + UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
        mockMvc.perform(json(post("/api/v1/auth/registro"),
                "{\"nombre\":\"Plantas\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isCreated());
        return iniciarSesion(correo, CLAVE);
    }

    private String iniciarSesion(String correo, String contrasena) throws Exception {
        return leer(mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isOk()), "$.token");
    }

    private String idDe(String token) throws Exception {
        return leer(mockMvc.perform(con(get("/api/v1/cuenta"), token)).andExpect(status().isOk()), "$.id");
    }

    private String crearPlanta(String token, String apodo, String especie) throws Exception {
        return leer(mockMvc.perform(json(con(post("/api/v1/plantas"), token),
                "{\"apodo\":\"" + apodo + "\",\"especie\":\"" + especie + "\"}"))
                .andExpect(status().isCreated()), "$.id");
    }

    private ResultActions medir(String token, String plantaId, String cuerpo) throws Exception {
        return mockMvc.perform(json(con(post("/api/v1/plantas/" + plantaId + "/mediciones"), token), cuerpo));
    }

    private void compartir(String plantaId, String invitadoId) {
        accesos.otorgar(AccesoCompartido.otorgadoAhora(UUID.fromString(plantaId), UUID.fromString(invitadoId)));
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("select count(*) from " + tabla, Integer.class);
    }

    /**
     * Los avisos "de negocio" que salieron: todos menos DIAGNOSTICO_NUEVO. Desde el bloque 4 cada
     * medición emite además un DIAGNOSTICO_NUEVO (RF-G2, vista en vivo); lo que estas pruebas
     * cuentan son los cambios de estado y las pérdidas de acceso.
     */
    private List<Aviso> avisos() {
        ArgumentCaptor<Aviso> captor = ArgumentCaptor.forClass(Aviso.class);
        verify(notificador, org.mockito.Mockito.atLeast(0)).avisar(captor.capture());
        return captor.getAllValues().stream().filter(a -> a.tipo() != TipoDeAviso.DIAGNOSTICO_NUEVO).toList();
    }

    // ---- el recorrido pedido: registrarse, crear, medir dos veces cambiando de estado, ver historial, un aviso ----

    @Test
    void registrarseCrearPlantaMedirDosVecesCambiandoDeEstadoVerElHistorialYUnSoloAviso() throws Exception {
        String planta = crearPlanta(dueno, "Pepa", "potos");
        compartir(planta, idInvitado);   // el endpoint de compartir llega en el bloque siguiente

        // RN9: recién creada no tiene diagnóstico, y consultarlo no es un error ni escribe nada.
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/diagnostico"), dueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hayDiagnostico").value(false))
                .andExpect(jsonPath("$.diagnostico").value((Object) null));
        assertThat(contar("diagnostico_planta")).isZero();

        // Primera medición: SALUDABLE. El primer diagnóstico no cuenta como cambio (D40).
        medir(dueno, planta, SALUDABLE)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origen").value("MANUAL"))
                .andExpect(jsonPath("$.diagnostico.estado").value("SALUDABLE"))
                .andExpect(jsonPath("$.diagnostico.especie").value("potos"));
        assertThat(avisos()).isEmpty();

        // Segunda medición: CRITICO. Ahora sí hay un cambio.
        medir(dueno, planta, CRITICA)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.diagnostico.estado").value("CRITICO"));

        // El aviso salió exactamente una vez, del tipo y a los destinatarios correctos (RF-G1).
        List<Aviso> avisos = avisos();
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).tipo()).isEqualTo(TipoDeAviso.CAMBIO_DE_ESTADO);
        assertThat(avisos.get(0).destinatarios())
                .isEqualTo(Set.of(UUID.fromString(idDueno), UUID.fromString(idInvitado)));
        assertThat(avisos.get(0).mensaje()).contains("Pepa").contains("SALUDABLE").contains("CRITICO");

        // El vigente es el último, y lo ven el dueño y el invitado.
        for (String quien : List.of(dueno, invitado)) {
            mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/diagnostico"), quien))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.hayDiagnostico").value(true))
                    .andExpect(jsonPath("$.diagnostico.diagnostico.estado").value("CRITICO"));
        }

        // El historial: dos, el más reciente primero, con recomendaciones y parámetros completos.
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/diagnosticos"), dueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.contenido.length()").value(2))
                .andExpect(jsonPath("$.contenido[0].diagnostico.estado").value("CRITICO"))
                .andExpect(jsonPath("$.contenido[1].diagnostico.estado").value("SALUDABLE"))
                .andExpect(jsonPath("$.contenido[0].diagnostico.parametros.length()").value(3))
                .andExpect(jsonPath("$.contenido[0].diagnostico.parametros[0].nombre").value("humedad"))
                .andExpect(jsonPath("$.contenido[0].diagnostico.recomendaciones.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].origen").value("MANUAL"));

        // Consultar no escribió: seguimos con dos filas y un solo aviso.
        assertThat(contar("diagnostico_planta")).isEqualTo(2);
        assertThat(avisos()).hasSize(1);
    }

    @Test
    void repetirElMismoEstadoNoVuelveAAvisar() throws Exception {
        String planta = crearPlanta(dueno, "Pepa", "potos");
        medir(dueno, planta, EN_RIESGO).andExpect(status().isCreated());
        medir(dueno, planta, EN_RIESGO).andExpect(status().isCreated());

        assertThat(avisos()).isEmpty();
    }

    @Test
    void elHistorialSePaginaDeVerdad() throws Exception {
        String planta = crearPlanta(dueno, "Pepa", "potos");
        medir(dueno, planta, SALUDABLE);
        medir(dueno, planta, EN_RIESGO);
        medir(dueno, planta, CRITICA);

        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/diagnosticos?pagina=0&tamano=2"), dueno))
                .andExpect(jsonPath("$.contenido.length()").value(2))
                .andExpect(jsonPath("$.contenido[0].diagnostico.estado").value("CRITICO"))
                .andExpect(jsonPath("$.contenido[1].diagnostico.estado").value("EN_RIESGO"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.totalDePaginas").value(2));
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/diagnosticos?pagina=1&tamano=2"), dueno))
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.contenido[0].diagnostico.estado").value("SALUDABLE"));
    }

    // ---- autorización: dueño, invitado, ajeno (RN3, RN5, D35) ----

    @Test
    void elInvitadoSoloLeeYElAjenoNoSabeQueLaPlantaExiste() throws Exception {
        String planta = crearPlanta(dueno, "Pepa", "potos");
        medir(dueno, planta, SALUDABLE).andExpect(status().isCreated());
        compartir(planta, idInvitado);
        String ruta = "/api/v1/plantas/" + planta;
        String cuerpoPlanta = "{\"apodo\":\"Otro\",\"especie\":\"potos\"}";

        // Invitado: lee todo, y es esDueno=false.
        mockMvc.perform(con(get(ruta), invitado)).andExpect(status().isOk()).andExpect(jsonPath("$.esDueno").value(false));
        mockMvc.perform(con(get(ruta + "/diagnostico"), invitado)).andExpect(status().isOk());
        mockMvc.perform(con(get(ruta + "/diagnosticos"), invitado)).andExpect(status().isOk());
        // Invitado: nada de lo que modifica (403, ya sabe que existe) y no queda rastro.
        mockMvc.perform(json(con(put(ruta), invitado), cuerpoPlanta))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("OPERACION_NO_PERMITIDA"));
        mockMvc.perform(con(delete(ruta), invitado)).andExpect(status().isForbidden());
        medir(invitado, planta, CRITICA).andExpect(status().isForbidden());
        assertThat(contar("diagnostico_planta")).isEqualTo(1);
        mockMvc.perform(con(get(ruta), dueno)).andExpect(jsonPath("$.apodo").value("Pepa"));

        // Ajeno (otro cliente) y administrador: 404, exactamente el mismo cuerpo que una planta inexistente.
        String inexistente = "/api/v1/plantas/" + UUID.randomUUID();
        String tokenAdmin = iniciarSesion(ADMIN_CORREO, ADMIN_CONTRASENA);
        for (String quien : List.of(ajeno, tokenAdmin)) {
            for (MockHttpServletRequestBuilder peticion : List.of(
                    get(ruta), get(ruta + "/diagnostico"), get(ruta + "/diagnosticos"), delete(ruta),
                    json(put(ruta), cuerpoPlanta), json(post(ruta + "/mediciones"), SALUDABLE))) {
                mockMvc.perform(con(peticion, quien))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.error").value("PLANTA_NO_ENCONTRADA"));
            }
        }
        String deAjena = mockMvc.perform(con(get(ruta), ajeno)).andReturn().getResponse().getContentAsString();
        String deInexistente = mockMvc.perform(con(get(inexistente), ajeno)).andReturn().getResponse().getContentAsString();
        assertThat(deAjena.replace(planta, "ID")).isEqualTo(deInexistente.replace(inexistente.substring(inexistente.lastIndexOf('/') + 1), "ID"));
        assertThat(contar("diagnostico_planta")).isEqualTo(1);
    }

    @Test
    void cadaClienteListaSoloSusPlantasYElAdministradorNoVeLasAjenas() throws Exception {
        crearPlanta(dueno, "De dueno", "potos");
        crearPlanta(ajeno, "De ajeno", "helecho");
        compartir(crearPlanta(ajeno, "Compartida", "helecho"), idDueno);

        mockMvc.perform(con(get("/api/v1/plantas"), dueno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].apodo").value("De dueno"));
        mockMvc.perform(con(get("/api/v1/plantas"), iniciarSesion(ADMIN_CORREO, ADMIN_CONTRASENA)))
                .andExpect(jsonPath("$.length()").value(0));   // ser administrador no da plantas ajenas
    }

    @Test
    void sinTokenNingunaRutaDePlantasResponde() throws Exception {
        mockMvc.perform(get("/api/v1/plantas")).andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/v1/plantas/" + UUID.randomUUID() + "/mediciones"), SALUDABLE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noSePuedeRegistrarUnaPlantaAUnDuenoDistintoDelDelToken() throws Exception {
        String id = leer(mockMvc.perform(json(con(post("/api/v1/plantas"), dueno),
                "{\"apodo\":\"Pepa\",\"especie\":\"potos\",\"duenoId\":\"" + idInvitado + "\"}"))
                .andExpect(status().isCreated()), "$.id");

        mockMvc.perform(con(get("/api/v1/plantas/" + id), dueno)).andExpect(jsonPath("$.duenoId").value(idDueno));
        mockMvc.perform(con(get("/api/v1/plantas/" + id), invitado)).andExpect(status().isNotFound());
    }

    // ---- edición, validación y errores ----

    @Test
    void editarCambiaApodoYEspecieYLaMedicionSiguienteUsaLaEspecieNueva() throws Exception {
        String planta = crearPlanta(dueno, "Pepa", "potos");
        // 15 % de humedad: crítico para potos (40-70) pero saludable para suculenta (10-30).
        String seca = "{\"humedad\":20,\"luz\":1500,\"temperatura\":22}";
        medir(dueno, planta, seca).andExpect(jsonPath("$.diagnostico.estado").value("CRITICO"));

        mockMvc.perform(json(con(put("/api/v1/plantas/" + planta), dueno), "{\"apodo\":\"Pepita\",\"especie\":\"suculenta\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apodo").value("Pepita"))
                .andExpect(jsonPath("$.especie").value("suculenta"));

        medir(dueno, planta, seca).andExpect(jsonPath("$.diagnostico.estado").value("SALUDABLE"))
                .andExpect(jsonPath("$.diagnostico.especie").value("suculenta"));
        // El historial conserva lo que dijo cada diagnóstico con la especie de ese momento.
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/diagnosticos"), dueno))
                .andExpect(jsonPath("$.contenido[0].diagnostico.especie").value("suculenta"))
                .andExpect(jsonPath("$.contenido[1].diagnostico.especie").value("potos"))
                .andExpect(jsonPath("$.contenido[1].diagnostico.estado").value("CRITICO"));
    }

    @Test
    void laEspecieDebeExistirYLosCamposSeValidanEnElBorde() throws Exception {
        mockMvc.perform(json(con(post("/api/v1/plantas"), dueno), "{\"apodo\":\"Pepa\",\"especie\":\"cactus\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("ESPECIE_NO_SOPORTADA"));
        mockMvc.perform(json(con(post("/api/v1/plantas"), dueno), "{\"apodo\":\"\",\"especie\":\"potos\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.campo").value("apodo"));
        String planta = crearPlanta(dueno, "Pepa", "potos");
        medir(dueno, planta, "{\"humedad\":-1,\"luz\":700,\"temperatura\":22}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.campo").value("humedad"));
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/diagnosticos?tamano=1000"), dueno))
                .andExpect(status().isBadRequest());
        assertThat(contar("diagnostico_planta")).isZero();
    }

    @Test
    void todasLasRespuestasDeLasRutasDePlantasSonJsonAunqueSePidaHtml() throws Exception {
        String planta = crearPlanta(dueno, "Pepa", "potos");
        for (MockHttpServletRequestBuilder peticion : List.of(
                get("/api/v1/plantas"), get("/api/v1/plantas/" + planta), get("/api/v1/plantas/" + planta + "/diagnostico"),
                get("/api/v1/plantas/" + planta + "/diagnosticos"), get("/api/v1/plantas/" + UUID.randomUUID()),
                get("/api/v1/plantas/no-es-uuid"))) {
            mockMvc.perform(con(peticion, dueno).accept(MediaType.TEXT_HTML))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }
    }

    // ---- RN11: especie descontinuada ----

    @Test
    void unaEspecieDescontinuadaNoSeOfreceParaPlantasNuevasPeroLasExistentesSiguenFuncionando() throws Exception {
        String admin = iniciarSesion(ADMIN_CORREO, ADMIN_CONTRASENA);
        String rangos = "\"rangos\":{\"humedad\":{\"min\":30,\"max\":60},\"luz\":{\"min\":500,\"max\":1000},"
                + "\"temperatura\":{\"min\":18,\"max\":26}}";
        mockMvc.perform(json(con(post("/api/v1/admin/especies"), admin), "{\"nombre\":\"plantas-efimera\"," + rangos + "}"))
                .andExpect(status().isCreated());
        String planta = crearPlanta(dueno, "Efimera", "plantas-efimera");
        medir(dueno, planta, "{\"humedad\":45,\"luz\":700,\"temperatura\":22}").andExpect(status().isCreated());

        mockMvc.perform(con(post("/api/v1/admin/especies/plantas-efimera/desactivar"), admin)).andExpect(status().isOk());

        // Nuevas: no. Ni registrar ni cambiar a ella.
        mockMvc.perform(json(con(post("/api/v1/plantas"), dueno), "{\"apodo\":\"Nueva\",\"especie\":\"plantas-efimera\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ESPECIE_DESCONTINUADA"));
        String otra = crearPlanta(dueno, "Otra", "potos");
        mockMvc.perform(json(con(put("/api/v1/plantas/" + otra), dueno), "{\"apodo\":\"Otra\",\"especie\":\"plantas-efimera\"}"))
                .andExpect(status().isConflict());
        // La existente: se consulta, se edita el apodo, se mide, y el estado sigue siendo el de su especie.
        mockMvc.perform(con(get("/api/v1/plantas/" + planta), dueno)).andExpect(status().isOk());
        mockMvc.perform(json(con(put("/api/v1/plantas/" + planta), dueno),
                        "{\"apodo\":\"Sigue viva\",\"especie\":\"plantas-efimera\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.apodo").value("Sigue viva"));
        medir(dueno, planta, "{\"humedad\":5,\"luz\":700,\"temperatura\":22}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.diagnostico.estado").value("CRITICO"));
    }

    // ---- cascadas (RN12, D11) ----

    @Test
    void eliminarUnaPlantaBorraSuHistorialYSusAccesosYAvisaAlInvitado() throws Exception {
        String planta = crearPlanta(dueno, "Pepa", "potos");
        String sobreviviente = crearPlanta(dueno, "Queda", "potos");
        compartir(planta, idInvitado);
        medir(dueno, planta, SALUDABLE);
        medir(dueno, planta, CRITICA);
        medir(dueno, sobreviviente, SALUDABLE);
        reset(notificador);

        mockMvc.perform(con(delete("/api/v1/plantas/" + planta), dueno)).andExpect(status().isNoContent());

        // Desaparece para todos, también para el invitado.
        for (String quien : List.of(dueno, invitado)) {
            mockMvc.perform(con(get("/api/v1/plantas/" + planta), quien)).andExpect(status().isNotFound());
        }
        assertThat(contar("acceso_compartido")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from diagnostico_planta where planta_id = ?", Integer.class,
                UUID.fromString(planta))).isZero();
        assertThat(jdbc.queryForObject("select count(*) from diagnostico_parametro where diagnostico_id not in "
                + "(select id from diagnostico_planta)", Integer.class)).isZero();   // sin huérfanos en las hijas
        // La otra planta y su historial no se tocaron.
        mockMvc.perform(con(get("/api/v1/plantas/" + sobreviviente + "/diagnosticos"), dueno))
                .andExpect(jsonPath("$.total").value(1));
        // Un solo aviso, ACCESO_PERDIDO, solo al invitado.
        List<Aviso> avisos = avisos();
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).tipo()).isEqualTo(TipoDeAviso.ACCESO_PERDIDO);
        assertThat(avisos.get(0).destinatarios()).containsExactly(UUID.fromString(idInvitado));
    }

    @Test
    void eliminarLaPropiaCuentaEliminaSusPlantasConLaMismaCascada() throws Exception {
        String planta = crearPlanta(dueno, "Pepa", "potos");
        String deAjeno = crearPlanta(ajeno, "Del ajeno", "potos");
        compartir(planta, idInvitado);        // el invitado pierde esta
        compartir(deAjeno, idDueno);          // y el dueño, al irse, deja de ser invitado de esta
        medir(dueno, planta, SALUDABLE);
        reset(notificador);

        mockMvc.perform(con(delete("/api/v1/cuenta"), dueno)).andExpect(status().isNoContent());

        assertThat(contar("planta where id = '" + planta + "'")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from diagnostico_planta where planta_id = ?", Integer.class,
                UUID.fromString(planta))).isZero();
        assertThat(jdbc.queryForObject("select count(*) from acceso_compartido where planta_id = ?", Integer.class,
                UUID.fromString(deAjeno))).isZero();
        mockMvc.perform(con(get("/api/v1/plantas/" + planta), invitado)).andExpect(status().isNotFound());
        mockMvc.perform(con(get("/api/v1/plantas/" + deAjeno), ajeno)).andExpect(status().isOk());   // la del ajeno sigue
        List<Aviso> avisos = avisos();
        assertThat(avisos).hasSize(1);
        assertThat(avisos.get(0).tipo()).isEqualTo(TipoDeAviso.ACCESO_PERDIDO);
        assertThat(avisos.get(0).destinatarios()).containsExactly(UUID.fromString(idInvitado));
    }

    @Test
    void elAdministradorVeLasPlantasDeUnUsuarioYAlEliminarloSeVanConLaCascada() throws Exception {
        String admin = iniciarSesion(ADMIN_CORREO, ADMIN_CONTRASENA);
        String planta = crearPlanta(ajeno, "De ajeno", "helecho");
        medir(ajeno, planta, "{\"humedad\":70,\"luz\":400,\"temperatura\":20}");
        compartir(planta, idInvitado);
        String idAjeno = idDe(ajeno);

        // RF-B2: el detalle trae un resumen de las plantas, no sus diagnósticos ni invitados.
        mockMvc.perform(con(get("/api/v1/admin/usuarios/" + idAjeno), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plantas.length()").value(1))
                .andExpect(jsonPath("$.plantas[0].apodo").value("De ajeno"))
                .andExpect(jsonPath("$.plantas[0].especie").value("helecho"))
                .andExpect(jsonPath("$.plantas[0].diagnostico").doesNotExist());
        // Y aun así el administrador no puede LEER la planta (RN3).
        mockMvc.perform(con(get("/api/v1/plantas/" + planta), admin)).andExpect(status().isNotFound());

        reset(notificador);
        mockMvc.perform(con(delete("/api/v1/admin/usuarios/" + idAjeno), admin)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("select count(*) from planta where id = ?", Integer.class, UUID.fromString(planta)))
                .isZero();
        assertThat(contar("acceso_compartido")).isZero();
        mockMvc.perform(con(get("/api/v1/plantas/" + planta), invitado)).andExpect(status().isNotFound());
        assertThat(avisos()).singleElement().satisfies(aviso -> {
            assertThat(aviso.tipo()).isEqualTo(TipoDeAviso.ACCESO_PERDIDO);
            assertThat(aviso.destinatarios()).containsExactly(UUID.fromString(idInvitado));
        });
    }

    @Test
    void eliminarUnaPlantaSinInvitadosNoAvisaANadie() throws Exception {
        String planta = crearPlanta(dueno, "Pepa", "potos");

        mockMvc.perform(con(delete("/api/v1/plantas/" + planta), dueno)).andExpect(status().isNoContent());

        assertThat(avisos()).isEmpty();
    }
}
