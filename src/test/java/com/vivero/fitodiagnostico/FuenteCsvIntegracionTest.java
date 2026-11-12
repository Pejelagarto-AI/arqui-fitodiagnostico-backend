package com.vivero.fitodiagnostico;

import com.jayway.jsonpath.JsonPath;
import com.vivero.fitodiagnostico.adaptador.salida.csv.AdaptadorEspecieCsv;
import com.vivero.fitodiagnostico.adaptador.salida.csv.CatalogoCsvSoloLectura;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoEditable;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RangosPorEspecie;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Arranca el contexto REAL con {@code fitodiagnostico.catalogo.fuente=csv}: lo único que cambia
 * respecto del modo por defecto es una propiedad. El diagnóstico y las plantas siguen funcionando
 * (lectura desde el CSV) y todo intento de modificar el catálogo responde 409 con el cuerpo uniforme.
 */
@SpringBootTest(properties = "fitodiagnostico.catalogo.fuente=csv")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FuenteCsvIntegracionTest {

    private static final String ADMIN_CORREO = "admin@pruebas.fitodiagnostico.local";
    private static final String ADMIN_CONTRASENA = "contrasena-de-pruebas-1";
    private static final String CLAVE = "clave-larga-1";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RangosPorEspecie rangos;
    @Autowired private CatalogoDeEspecies catalogo;
    @Autowired private CatalogoEditable editable;

    @AfterEach
    void limpiar() {
        jdbc.update("delete from diagnostico_planta where planta_id in (select p.id from planta p "
                + "join usuario u on u.id = p.dueno_id where u.correo like 'csv-%')");
        jdbc.update("delete from planta where dueno_id in (select id from usuario where correo like 'csv-%')");
        jdbc.update("delete from usuario_rol where usuario_id in (select id from usuario where correo like 'csv-%')");
        jdbc.update("delete from usuario where correo like 'csv-%'");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder p, String cuerpo) {
        return p.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    private String token(String correo, String contrasena) throws Exception {
        String cuerpo = mockMvc.perform(json(post("/api/v1/auth/sesion"),
                "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.token");
    }

    @Test
    void conFuenteCsvLosPuertosDeLecturaSalenDelCsvYElDeEscrituraEsDeSoloLectura() {
        assertThat(rangos).isInstanceOf(AdaptadorEspecieCsv.class);
        assertThat(catalogo).isInstanceOf(AdaptadorEspecieCsv.class);
        assertThat(editable).isInstanceOf(CatalogoCsvSoloLectura.class);
    }

    @Test
    void elListadoPublicoYElDiagnosticoSueltoFuncionanLeyendoElCsv() throws Exception {
        mockMvc.perform(get("/api/v1/especies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));

        mockMvc.perform(json(post("/api/v1/diagnosticos"),
                "{\"especie\":\"potos\",\"humedad\":55,\"luz\":700,\"temperatura\":22}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("SALUDABLE"));
    }

    @Test
    void unaPlantaSePuedeRegistrarYMedirConLaFuenteCsv() throws Exception {
        String correo = "csv-" + UUID.randomUUID().toString().substring(0, 8) + "@vivero.co";
        mockMvc.perform(json(post("/api/v1/auth/registro"),
                "{\"nombre\":\"Csv\",\"correo\":\"" + correo + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isCreated());
        String usuario = token(correo, CLAVE);

        String cuerpo = mockMvc.perform(json(post("/api/v1/plantas").header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario),
                "{\"apodo\":\"Pepa\",\"especie\":\"potos\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String planta = JsonPath.read(cuerpo, "$.id");

        mockMvc.perform(json(post("/api/v1/plantas/" + planta + "/mediciones")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + usuario),
                "{\"humedad\":55,\"luz\":700,\"temperatura\":22}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.diagnostico.estado").value("SALUDABLE"));
    }

    @Test
    void modificarElCatalogoResponde409ConElCuerpoUniforme() throws Exception {
        String admin = token(ADMIN_CORREO, ADMIN_CONTRASENA);
        String rangosNuevos = "\"rangos\":{\"humedad\":{\"min\":30,\"max\":60},\"luz\":{\"min\":500,\"max\":1000},"
                + "\"temperatura\":{\"min\":18,\"max\":26}}";

        mockMvc.perform(json(post("/api/v1/admin/especies").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin),
                "{\"nombre\":\"csv-nueva\"," + rangosNuevos + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CATALOGO_SOLO_LECTURA"))
                .andExpect(jsonPath("$.mensaje").isNotEmpty());

        mockMvc.perform(post("/api/v1/admin/especies/potos/desactivar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CATALOGO_SOLO_LECTURA"));
    }
}
