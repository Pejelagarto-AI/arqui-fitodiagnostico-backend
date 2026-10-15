package com.vivero.fitodiagnostico;

import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.puerto.Notificador;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Compartir plantas, recuperar la contraseña y solicitar especies con el contexto
 * REAL: seguridad JWT, servicios, JPA y Flyway, el notificador compuesto detrás
 * del decorador de commit y el "SMTP" de pruebas. Es donde por fin hay invitados
 * de verdad: aquí se comprueba que los destinatarios de CAMBIO_DE_ESTADO y las
 * cascadas del bloque 3 funcionan con ellos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FlujoDeCompartirIntegracionTest extends BaseDeIntegracionDeCompartir {

    @Autowired private Notificador notificador;
    @Autowired private PlatformTransactionManager transacciones;

    // ---- RF-E1, RF-E2: invitar y aceptar ----

    @Test
    void elDuenoInvitaPorCorreoYElVisitanteSinCuentaSeRegistraYAceptaEnUnSoloPaso() throws Exception {
        Cuenta dueno = registrar("Dueña");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        String visitante = "compartir-visitante-" + UUID.randomUUID().toString().substring(0, 6) + "@vivero.co";

        // El dueño invita: recibe el token y el enlace UNA vez, y el visitante recibe el enlace por correo.
        var respuesta = mockMvc.perform(json(con(post("/api/v1/plantas/" + planta + "/invitaciones"), dueno.token()),
                        "{\"correo\":\"" + visitante.toUpperCase() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.correoDestino").value(visitante));
        String token = leer(respuesta, "$.token");
        assertThat(leer(respuesta, "$.enlace")).isEqualTo(FRONT + "/invitacion?token=" + token);
        assertThat(correosDe(visitante, "invitaron")).singleElement().satisfies(mensaje -> {
            assertThat(mensaje.getText()).contains("Dueña").contains("Pepa").contains(FRONT + "/invitacion?token=" + token);
            assertThat(tokenDelEnlace(mensaje)).isEqualTo(token);
        });
        // Solo la huella queda en la base.
        assertThat(contar("invitacion where huella_token = '" + token + "'")).isZero();
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/invitaciones"), dueno.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].token").doesNotExist());

        // El visitante abre el enlace: no tiene cuenta, se registra y acepta en el mismo flujo (público).
        mockMvc.perform(json(post("/api/v1/invitaciones/aceptar-con-registro"),
                        "{\"token\":\"" + token + "\",\"nombre\":\"Visitante\",\"correo\":\"" + visitante
                                + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuario.correo").value(visitante))
                .andExpect(jsonPath("$.planta.id").value(planta))
                .andExpect(jsonPath("$.planta.esDueno").value(false));

        // Ya es invitado: inicia sesión, la ve compartida y puede leerla pero no modificarla.
        Cuenta invitado = iniciarSesion(visitante, CLAVE);
        mockMvc.perform(con(get("/api/v1/plantas/compartidas"), invitado.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].apodo").value("Pepa"));
        mockMvc.perform(con(get("/api/v1/plantas/" + planta), invitado.token())).andExpect(status().isOk());
        mockMvc.perform(con(delete("/api/v1/plantas/" + planta), invitado.token())).andExpect(status().isForbidden());
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/invitados"), dueno.token()))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].correo").value(visitante));
        // La invitación quedó consumida: ya no figura entre las pendientes.
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/invitaciones"), dueno.token()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void elTokenDeUnSoloUsoNoSirveDosVecesNiParaRegistrarOtraCuenta() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta beto = registrar("Beto");
        Cuenta carla = registrar("Carla");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        String token = invitar(dueno, planta);

        mockMvc.perform(json(con(post("/api/v1/invitaciones/aceptar"), beto.token()), "{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(planta));

        mockMvc.perform(json(con(post("/api/v1/invitaciones/aceptar"), carla.token()), "{\"token\":\"" + token + "\"}"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error").value("INVITACION_YA_ACEPTADA"))
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("una sola vez")));
        mockMvc.perform(json(post("/api/v1/invitaciones/aceptar-con-registro"),
                        "{\"token\":\"" + token + "\",\"nombre\":\"Otro\",\"correo\":\"compartir-otro@vivero.co\",\"contrasena\":\""
                                + CLAVE + "\"}"))
                .andExpect(status().isGone());
        assertThat(contar("usuario where correo = 'compartir-otro@vivero.co'")).as("no se creó la cuenta").isZero();
        mockMvc.perform(con(get("/api/v1/plantas/" + planta), carla.token())).andExpect(status().isNotFound());
    }

    @Test
    void unEnlaceCaducadoDa410ConSuPropioCodigoYNoDejaCrearLaCuenta() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta beto = registrar("Beto");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        String token = invitar(dueno, planta);
        // Se envejece la invitación entera (creada y caducidad): el dominio exige que caduque DESPUÉS de crearse.
        jdbc.update("update invitacion set creada_en = ?, expira_en = ? where planta_id = ?",
                OffsetDateTime.now().minusDays(8), OffsetDateTime.now().minusMinutes(1), UUID.fromString(planta));

        mockMvc.perform(json(con(post("/api/v1/invitaciones/aceptar"), beto.token()), "{\"token\":\"" + token + "\"}"))
                .andExpect(status().isGone()).andExpect(jsonPath("$.error").value("INVITACION_CADUCADA"));
        mockMvc.perform(json(post("/api/v1/invitaciones/aceptar-con-registro"),
                        "{\"token\":\"" + token + "\",\"nombre\":\"Otro\",\"correo\":\"compartir-tarde@vivero.co\",\"contrasena\":\""
                                + CLAVE + "\"}"))
                .andExpect(status().isGone()).andExpect(jsonPath("$.error").value("INVITACION_CADUCADA"));
        assertThat(contar("usuario where correo = 'compartir-tarde@vivero.co'")).isZero();
        // Una caducada ya no se lista entre las pendientes del dueño.
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/invitaciones"), dueno.token()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void unTokenInventadoDa404YUnoMalFormadoDa400() throws Exception {
        Cuenta beto = registrar("Beto");

        mockMvc.perform(json(con(post("/api/v1/invitaciones/aceptar"), beto.token()), "{\"token\":\"inventado\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("INVITACION_NO_ENCONTRADA"));
        mockMvc.perform(json(con(post("/api/v1/invitaciones/aceptar"), beto.token()), "{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/api/v1/invitaciones/aceptar-con-registro"),
                        "{\"token\":\"inventado\",\"nombre\":\"X\",\"correo\":\"compartir-x@vivero.co\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isNotFound());
        assertThat(contar("usuario where correo = 'compartir-x@vivero.co'")).isZero();
    }

    @Test
    void siElCorreoDelRegistroYaTieneCuentaDa409YLaInvitacionSigueVigente() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta beto = registrar("Beto");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        String token = invitar(dueno, planta);

        mockMvc.perform(json(post("/api/v1/invitaciones/aceptar-con-registro"),
                        "{\"token\":\"" + token + "\",\"nombre\":\"Beto 2\",\"correo\":\"" + beto.correo() + "\",\"contrasena\":\""
                                + CLAVE + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("CORREO_YA_REGISTRADO"));

        // El mismo token sigue sirviendo para quien sí debe usarlo.
        mockMvc.perform(json(con(post("/api/v1/invitaciones/aceptar"), beto.token()), "{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());
    }

    // ---- RF-E3: revocar invitaciones ----

    @Test
    void revocarUnaInvitacionLaInutilizaYSoloSeRevocaUnaPendiente() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta beto = registrar("Beto");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        var generada = mockMvc.perform(con(post("/api/v1/plantas/" + planta + "/invitaciones"), dueno.token()))
                .andExpect(status().isCreated());
        String token = leer(generada, "$.token");
        String id = leer(generada, "$.id");

        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/invitaciones/" + id), dueno.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(json(con(post("/api/v1/invitaciones/aceptar"), beto.token()), "{\"token\":\"" + token + "\"}"))
                .andExpect(status().isGone()).andExpect(jsonPath("$.error").value("INVITACION_REVOCADA"));
        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/invitaciones/" + id), dueno.token()))
                .andExpect(status().isGone());   // ya no estaba pendiente
        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/invitaciones/" + UUID.randomUUID()), dueno.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void noSePuedeRevocarUnaInvitacionYaAceptadaPorLaViaDeLasInvitaciones() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta beto = registrar("Beto");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        var generada = mockMvc.perform(con(post("/api/v1/plantas/" + planta + "/invitaciones"), dueno.token()));
        mockMvc.perform(json(con(post("/api/v1/invitaciones/aceptar"), beto.token()),
                "{\"token\":\"" + leer(generada, "$.token") + "\"}")).andExpect(status().isOk());

        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/invitaciones/" + leer(generada, "$.id")), dueno.token()))
                .andExpect(status().isGone()).andExpect(jsonPath("$.error").value("INVITACION_YA_ACEPTADA"));
        mockMvc.perform(con(get("/api/v1/plantas/" + planta), beto.token())).andExpect(status().isOk());   // sigue con acceso
    }

    // ---- quién puede qué ----

    @Test
    void soloElDuenoGestionaInvitacionesEInvitadosElInvitadoRecibe403YElAjeno404() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta invitado = registrar("Beto");
        Cuenta ajeno = registrar("Ajeno");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        compartir(dueno, planta, invitado);
        String base = "/api/v1/plantas/" + planta;
        UUID cualquiera = UUID.randomUUID();

        for (var peticion : List.of(post(base + "/invitaciones"), get(base + "/invitaciones"), get(base + "/invitados"),
                delete(base + "/invitaciones/" + cualquiera), delete(base + "/invitados/" + invitado.id()))) {
            mockMvc.perform(con(peticion, invitado.token())).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("OPERACION_NO_PERMITIDA"));
        }
        for (var peticion : List.of(post(base + "/invitaciones"), get(base + "/invitaciones"), get(base + "/invitados"),
                delete(base + "/invitaciones/" + cualquiera), delete(base + "/invitados/" + invitado.id()))) {
            mockMvc.perform(con(peticion, ajeno.token())).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("PLANTA_NO_ENCONTRADA"));
        }
        // El administrador tampoco: ser administrador no da plantas ajenas.
        mockMvc.perform(con(get(base + "/invitados"), tokenDelAdministrador())).andExpect(status().isNotFound());
        assertThat(contar("acceso_compartido where planta_id = '" + planta + "'")).isEqualTo(1);
    }

    @Test
    void elDuenoNoPuedeInvitarseNiAceptarSuPropiaInvitacion() throws Exception {
        Cuenta dueno = registrar("Dueña");
        String planta = crearPlanta(dueno, "Pepa", "potos");

        mockMvc.perform(json(con(post("/api/v1/plantas/" + planta + "/invitaciones"), dueno.token()),
                        "{\"correo\":\"" + dueno.correo().toUpperCase() + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("OPERACION_NO_PERMITIDA"));
        assertThat(contar("invitacion where planta_id = '" + planta + "'")).isZero();
        assertThat(correo.enviados()).isEmpty();

        String token = invitar(dueno, planta);
        mockMvc.perform(json(con(post("/api/v1/invitaciones/aceptar"), dueno.token()), "{\"token\":\"" + token + "\"}"))
                .andExpect(status().isForbidden());
        assertThat(contar("acceso_compartido where planta_id = '" + planta + "'")).isZero();
        // Y la invitación no se gastó: otro sí puede usarla.
        Cuenta beto = registrar("Beto");
        mockMvc.perform(json(con(post("/api/v1/invitaciones/aceptar"), beto.token()), "{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk());
    }

    // ---- RF-E4, RF-E6, RF-G3 ----

    @Test
    void elDuenoRevocaElAccesoYElInvitadoRecibeElAvisoPorCorreo() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta invitado = registrar("Beto");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        compartir(dueno, planta, invitado);
        correo.limpiar();

        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/invitados/" + invitado.id()), dueno.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(con(get("/api/v1/plantas/" + planta), invitado.token())).andExpect(status().isNotFound());
        mockMvc.perform(con(get("/api/v1/plantas/compartidas"), invitado.token())).andExpect(jsonPath("$.length()").value(0));
        assertThat(correosDe(invitado.correo(), "Perdiste el acceso")).singleElement()
                .satisfies(mensaje -> assertThat(mensaje.getText()).contains("Pepa").contains("te lo quitó"));
        assertThat(correo.para(dueno.correo())).as("el dueño no recibe nada").isEmpty();
        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/invitados/" + invitado.id()), dueno.token()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("INVITADO_NO_ENCONTRADO"));
    }

    @Test
    void elInvitadoRenunciaYNoSeAvisaANadiePeroUnAjenoNoPuedeRenunciar() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta invitado = registrar("Beto");
        Cuenta ajeno = registrar("Ajeno");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        compartir(dueno, planta, invitado);
        correo.limpiar();

        mockMvc.perform(con(delete("/api/v1/plantas/compartidas/" + planta), ajeno.token())).andExpect(status().isNotFound());
        mockMvc.perform(con(delete("/api/v1/plantas/compartidas/" + planta), dueno.token())).andExpect(status().isNotFound());
        mockMvc.perform(con(delete("/api/v1/plantas/compartidas/" + planta), invitado.token())).andExpect(status().isNoContent());

        mockMvc.perform(con(get("/api/v1/plantas/" + planta), invitado.token())).andExpect(status().isNotFound());
        mockMvc.perform(con(get("/api/v1/plantas/" + planta), dueno.token())).andExpect(status().isOk());
        assertThat(correo.enviados()).isEmpty();
        mockMvc.perform(con(delete("/api/v1/plantas/compartidas/" + planta), invitado.token())).andExpect(status().isNotFound());
    }

    // ---- RF-G1 con invitados reales ----

    @Test
    void elCambioDeEstadoLlegaPorCorreoAlDuenoYAlInvitadoRealYSoloCuandoCambia() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta invitado = registrar("Beto");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        compartir(dueno, planta, invitado);
        correo.limpiar();

        medir(dueno, planta, SALUDABLE).andExpect(status().isCreated());          // primero: no es cambio
        medir(dueno, planta, SALUDABLE).andExpect(status().isCreated());          // mismo estado
        assertThat(correo.enviados()).isEmpty();

        medir(dueno, planta, CRITICA).andExpect(status().isCreated());            // SALUDABLE -> CRITICO
        for (Cuenta destinatario : List.of(dueno, invitado)) {
            assertThat(correosDe(destinatario.correo(), "Cambió el estado")).singleElement().satisfies(mensaje -> {
                assertThat(mensaje.getText()).contains("Pepa").contains("SALUDABLE").contains("CRITICO");
                assertThat(mensaje.getTo()).as("un correo por persona, sin copias").hasSize(1);
                assertThat(mensaje.getBcc()).isNull();
            });
        }
        assertThat(correo.enviados()).hasSize(2);
    }

    @Test
    void despuesDeRevocarElInvitadoYaNoRecibeLosCambiosDeEstado() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta invitado = registrar("Beto");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        compartir(dueno, planta, invitado);
        medir(dueno, planta, SALUDABLE);
        mockMvc.perform(con(delete("/api/v1/plantas/" + planta + "/invitados/" + invitado.id()), dueno.token()))
                .andExpect(status().isNoContent());
        correo.limpiar();

        medir(dueno, planta, CRITICA);

        assertThat(correo.para(invitado.correo())).isEmpty();
        assertThat(correosDe(dueno.correo(), "Cambió el estado")).hasSize(1);
    }

    // ---- cascadas con invitados reales (RN12, D11, D37, D56) ----

    @Test
    void eliminarUnaPlantaConInvitadoRealAvisaPorCorreoYBorraSusInvitacionesPendientes() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta invitado = registrar("Beto");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        compartir(dueno, planta, invitado);
        String pendiente = invitar(dueno, planta);          // un enlace repartido que nadie usó todavía
        medir(dueno, planta, SALUDABLE);
        correo.limpiar();

        mockMvc.perform(con(delete("/api/v1/plantas/" + planta), dueno.token())).andExpect(status().isNoContent());

        mockMvc.perform(con(get("/api/v1/plantas/" + planta), invitado.token())).andExpect(status().isNotFound());
        assertThat(contar("acceso_compartido where planta_id = '" + planta + "'")).isZero();
        assertThat(contar("invitacion where planta_id = '" + planta + "'")).isZero();
        assertThat(correosDe(invitado.correo(), "Perdiste el acceso")).singleElement()
                .satisfies(mensaje -> assertThat(mensaje.getText()).contains("Pepa").contains("eliminó"));
        assertThat(correo.para(dueno.correo())).isEmpty();
        // El enlace que seguía circulando ya no lleva a ninguna parte.
        Cuenta otro = registrar("Otro");
        mockMvc.perform(json(con(post("/api/v1/invitaciones/aceptar"), otro.token()), "{\"token\":\"" + pendiente + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void eliminarLaCuentaDelDuenoDejaSinAccesoAlInvitadoRealYLeAvisa() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta invitado = registrar("Beto");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        compartir(dueno, planta, invitado);
        invitar(dueno, planta);
        correo.limpiar();

        mockMvc.perform(con(delete("/api/v1/cuenta"), dueno.token())).andExpect(status().isNoContent());

        mockMvc.perform(con(get("/api/v1/plantas/" + planta), invitado.token())).andExpect(status().isNotFound());
        assertThat(contar("invitacion where planta_id = '" + planta + "'")).isZero();
        assertThat(correosDe(invitado.correo(), "Perdiste el acceso")).hasSize(1);
        mockMvc.perform(con(get("/api/v1/plantas/compartidas"), invitado.token())).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void eliminarLaCuentaDelInvitadoQuitaSuAccesoPeroLaPlantaSigueYSoloAvisaAlDueno() throws Exception {
        Cuenta dueno = registrar("Dueña");
        Cuenta invitado = registrar("Beto");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        compartir(dueno, planta, invitado);
        medir(dueno, planta, SALUDABLE);

        mockMvc.perform(con(delete("/api/v1/cuenta"), invitado.token())).andExpect(status().isNoContent());

        assertThat(contar("acceso_compartido where planta_id = '" + planta + "'")).isZero();
        mockMvc.perform(con(get("/api/v1/plantas/" + planta + "/invitados"), dueno.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        correo.limpiar();
        medir(dueno, planta, CRITICA).andExpect(status().isCreated());
        assertThat(correo.enviados()).singleElement()
                .satisfies(mensaje -> assertThat(mensaje.getTo()).containsExactly(dueno.correo()));
    }

    // ---- RF-A7, RF-A8: recuperación de contraseña ----

    @Test
    void solicitarRestablecimientoResponde202IgualExistaONoElCorreoYSoloAlExistenteLeLlegaElEnlace() throws Exception {
        Cuenta ana = registrar("Ana");

        String existente = mockMvc.perform(json(post("/api/v1/auth/recuperacion"), "{\"correo\":\"" + ana.correo() + "\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String inexistente = mockMvc.perform(json(post("/api/v1/auth/recuperacion"),
                        "{\"correo\":\"compartir-nadie@vivero.co\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        assertThat(inexistente).as("no se puede enumerar quién tiene cuenta").isEqualTo(existente);
        assertThat(correo.enviados()).singleElement().satisfies(mensaje -> {
            assertThat(mensaje.getTo()).containsExactly(ana.correo());
            assertThat(mensaje.getSubject()).contains("Restablece tu contraseña");
            assertThat(mensaje.getText()).contains(FRONT + "/restablecer?token=").contains("una sola vez");
        });
        assertThat(contar("restablecimiento")).isEqualTo(1);
    }

    @Test
    void restablecerConElTokenDelCorreoCambiaLaContrasenaYElEnlaceNoSirveDosVeces() throws Exception {
        Cuenta ana = registrar("Ana");
        mockMvc.perform(json(post("/api/v1/auth/recuperacion"), "{\"correo\":\"" + ana.correo() + "\"}"));
        String token = tokenDelEnlace(correosDe(ana.correo(), "Restablece").get(0));

        mockMvc.perform(json(post("/api/v1/auth/restablecimiento"),
                        "{\"token\":\"" + token + "\",\"contrasenaNueva\":\"clave-nueva-9\"}"))
                .andExpect(status().isNoContent());

        iniciarSesion(ana.correo(), "clave-nueva-9");
        mockMvc.perform(json(post("/api/v1/auth/sesion"), "{\"correo\":\"" + ana.correo() + "\",\"contrasena\":\"" + CLAVE + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json(post("/api/v1/auth/restablecimiento"),
                        "{\"token\":\"" + token + "\",\"contrasenaNueva\":\"otra-clave-9\"}"))
                .andExpect(status().isGone()).andExpect(jsonPath("$.error").value("RESTABLECIMIENTO_NO_VIGENTE"));
        iniciarSesion(ana.correo(), "clave-nueva-9");   // la segunda no cambió nada
    }

    @Test
    void elEnlaceCaducaYUnTokenInventadoDaElMismoError() throws Exception {
        Cuenta ana = registrar("Ana");
        mockMvc.perform(json(post("/api/v1/auth/recuperacion"), "{\"correo\":\"" + ana.correo() + "\"}"));
        String token = tokenDelEnlace(correo.para(ana.correo()).get(0));
        jdbc.update("update restablecimiento set creada_en = ?, expira_en = ?",
                OffsetDateTime.now().minusHours(2), OffsetDateTime.now().minusSeconds(1));

        String caducado = mockMvc.perform(json(post("/api/v1/auth/restablecimiento"),
                        "{\"token\":\"" + token + "\",\"contrasenaNueva\":\"clave-nueva-9\"}"))
                .andExpect(status().isGone()).andReturn().getResponse().getContentAsString();
        String inventado = mockMvc.perform(json(post("/api/v1/auth/restablecimiento"),
                        "{\"token\":\"inventado\",\"contrasenaNueva\":\"clave-nueva-9\"}"))
                .andExpect(status().isGone()).andReturn().getResponse().getContentAsString();

        assertThat(inventado).isEqualTo(caducado);
        iniciarSesion(ana.correo(), CLAVE);   // la contraseña vieja sigue valiendo
    }

    @Test
    void siLaContrasenaCambiaPorOtraViaElEnlaceSeInvalida() throws Exception {
        Cuenta ana = registrar("Ana");
        mockMvc.perform(json(post("/api/v1/auth/recuperacion"), "{\"correo\":\"" + ana.correo() + "\"}"));
        String token = tokenDelEnlace(correo.para(ana.correo()).get(0));

        // RF-A5: Ana recuerda su contraseña y la cambia estando autenticada.
        mockMvc.perform(json(con(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/cuenta/contrasena"),
                        ana.token()), "{\"contrasenaActual\":\"" + CLAVE + "\",\"contrasenaNueva\":\"por-otra-via-9\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(json(post("/api/v1/auth/restablecimiento"),
                        "{\"token\":\"" + token + "\",\"contrasenaNueva\":\"del-atacante-9\"}"))
                .andExpect(status().isGone());
        iniciarSesion(ana.correo(), "por-otra-via-9");
    }

    @Test
    void elTokenDelCorreoNuncaApareceEnLaBaseNiEnLaRespuestaDeLaSolicitud() throws Exception {
        Cuenta ana = registrar("Ana");
        String respuesta = mockMvc.perform(json(post("/api/v1/auth/recuperacion"), "{\"correo\":\"" + ana.correo() + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = tokenDelEnlace(correo.para(ana.correo()).get(0));

        assertThat(respuesta).doesNotContain(token);
        assertThat(contar("restablecimiento where huella_token = '" + token + "'")).isZero();
    }

    // ---- RF-H1..H5: solicitudes de especie ----

    @Test
    void elClienteSolicitaUnaEspecieElAdministradorLaApruebaYElSolicitanteRecibeLaRespuestaPorCorreo() throws Exception {
        Cuenta ana = registrar("Ana");
        String admin = tokenDelAdministrador();
        String id = leer(mockMvc.perform(json(con(post("/api/v1/solicitudes-especie"), ana.token()),
                        "{\"nombre\":\"compartir-Monstera\",\"justificacion\":\"la tengo y no está\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE")), "$.id");

        mockMvc.perform(con(get("/api/v1/solicitudes-especie"), ana.token()))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
        mockMvc.perform(con(get("/api/v1/admin/solicitudes-especie"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].nombre").value("compartir-Monstera"));

        mockMvc.perform(json(con(post("/api/v1/admin/solicitudes-especie/" + id + "/aprobar"), admin),
                        "{\"respuesta\":\"la damos de alta esta semana\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.respuesta").value("la damos de alta esta semana"))
                .andExpect(jsonPath("$.resueltaEn").isNotEmpty());

        assertThat(correosDe(ana.correo(), "Respondieron tu solicitud")).singleElement().satisfies(mensaje ->
                assertThat(mensaje.getText()).contains("compartir-Monstera").contains("aprobada")
                        .contains("la damos de alta esta semana"));
        // D55: aprobar no da de alta la especie.
        mockMvc.perform(get("/api/v1/especies")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("compartir-Monstera"))));
        mockMvc.perform(con(get("/api/v1/admin/solicitudes-especie"), admin))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").isEmpty());   // ya no está pendiente
        mockMvc.perform(con(get("/api/v1/solicitudes-especie"), ana.token()))
                .andExpect(jsonPath("$[0].estado").value("APROBADA"));
    }

    @Test
    void rechazarExigeMotivoNotificaYUnaResueltaNoSeResuelveDeNuevo() throws Exception {
        Cuenta ana = registrar("Ana");
        String admin = tokenDelAdministrador();
        String id = leer(mockMvc.perform(json(con(post("/api/v1/solicitudes-especie"), ana.token()),
                "{\"nombre\":\"compartir-Ficus\"}")).andExpect(status().isCreated()), "$.id");

        mockMvc.perform(con(post("/api/v1/admin/solicitudes-especie/" + id + "/rechazar"), admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("PARAMETRO_INVALIDO"));
        assertThat(correo.enviados()).isEmpty();

        mockMvc.perform(json(con(post("/api/v1/admin/solicitudes-especie/" + id + "/rechazar"), admin),
                        "{\"respuesta\":\"ya existe como Ficus lyrata\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADA"));
        assertThat(correosDe(ana.correo(), "Respondieron")).singleElement().satisfies(mensaje ->
                assertThat(mensaje.getText()).contains("rechazada").contains("ya existe como Ficus lyrata"));

        mockMvc.perform(con(post("/api/v1/admin/solicitudes-especie/" + id + "/aprobar"), admin))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("SOLICITUD_YA_RESUELTA"));
        mockMvc.perform(con(post("/api/v1/admin/solicitudes-especie/" + UUID.randomUUID() + "/aprobar"), admin))
                .andExpect(status().isNotFound());
        assertThat(correo.enviados()).hasSize(1);
    }

    @Test
    void unClienteNoResuelveNiVeLasPendientesYSolicitarUnaEspecieExistenteDa409() throws Exception {
        Cuenta ana = registrar("Ana");

        mockMvc.perform(con(get("/api/v1/admin/solicitudes-especie"), ana.token())).andExpect(status().isForbidden());
        mockMvc.perform(con(post("/api/v1/admin/solicitudes-especie/" + UUID.randomUUID() + "/aprobar"), ana.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(con(post("/api/v1/solicitudes-especie"), ana.token()), "{\"nombre\":\"POTOS\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ESPECIE_YA_EXISTE"));
        assertThat(contar("solicitud_especie")).isZero();
    }

    @Test
    void cadaClienteVeSoloSusSolicitudes() throws Exception {
        Cuenta ana = registrar("Ana");
        Cuenta beto = registrar("Beto");
        mockMvc.perform(json(con(post("/api/v1/solicitudes-especie"), ana.token()), "{\"nombre\":\"compartir-Uno\"}"));
        mockMvc.perform(json(con(post("/api/v1/solicitudes-especie"), beto.token()), "{\"nombre\":\"compartir-Dos\"}"));

        mockMvc.perform(con(get("/api/v1/solicitudes-especie"), ana.token()))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nombre").value("compartir-Uno"));
        mockMvc.perform(con(get("/api/v1/solicitudes-especie"), beto.token()))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].nombre").value("compartir-Dos"));
    }

    @Test
    void eliminarLaCuentaBorraSusSolicitudesDeEspecieYDeRestablecimiento() throws Exception {
        Cuenta ana = registrar("Ana");
        Cuenta beto = registrar("Beto");
        mockMvc.perform(json(con(post("/api/v1/solicitudes-especie"), ana.token()), "{\"nombre\":\"compartir-Uno\"}"));
        mockMvc.perform(json(con(post("/api/v1/solicitudes-especie"), beto.token()), "{\"nombre\":\"compartir-Dos\"}"));
        mockMvc.perform(json(post("/api/v1/auth/recuperacion"), "{\"correo\":\"" + ana.correo() + "\"}"));
        mockMvc.perform(json(post("/api/v1/auth/recuperacion"), "{\"correo\":\"" + beto.correo() + "\"}"));

        mockMvc.perform(con(delete("/api/v1/cuenta"), ana.token())).andExpect(status().isNoContent());

        assertThat(contar("solicitud_especie where solicitante_id = '" + ana.id() + "'")).isZero();
        assertThat(contar("restablecimiento where usuario_id = '" + ana.id() + "'")).isZero();
        assertThat(contar("solicitud_especie where solicitante_id = '" + beto.id() + "'")).isEqualTo(1);
        assertThat(contar("restablecimiento where usuario_id = '" + beto.id() + "'")).isEqualTo(1);
    }

    // ---- D57: los avisos salen después del commit ----

    @Test
    void unAvisoDentroDeUnaTransaccionRevertidaNoSaleYUnoConfirmadoSi() throws Exception {
        Cuenta ana = registrar("Ana");
        TransactionTemplate transaccion = new TransactionTemplate(transacciones);
        Aviso aviso = new Aviso(Set.of(UUID.fromString(ana.id())), TipoDeAviso.CAMBIO_DE_ESTADO, "La planta cambió.");

        transaccion.executeWithoutResult(estado -> {
            notificador.avisar(aviso);
            estado.setRollbackOnly();
        });
        assertThat(correo.enviados()).as("rollback: el aviso no salió").isEmpty();

        transaccion.executeWithoutResult(estado -> {
            notificador.avisar(aviso);
            assertThat(correo.enviados()).as("dentro de la transacción todavía no sale").isEmpty();
        });
        assertThat(correo.enviados()).as("commit: ahora sí").hasSize(1);
        assertThat(correo.enviados().get(0).getTo()).containsExactly(ana.correo());
    }

    @Test
    void unaMedicionQueFallaDespuesDeDiagnosticarNoDejaAvisosFantasma() throws Exception {
        Cuenta dueno = registrar("Dueña");
        String planta = crearPlanta(dueno, "Pepa", "potos");
        medir(dueno, planta, SALUDABLE);
        correo.limpiar();

        // Humedad fuera del rango físico: el dominio lo rechaza ANTES de avisar, y no queda nada.
        medir(dueno, planta, "{\"humedad\":500,\"luz\":700,\"temperatura\":22}").andExpect(status().isBadRequest());

        assertThat(correo.enviados()).isEmpty();
        assertThat(contar("diagnostico_planta where planta_id = '" + planta + "'")).isEqualTo(1);
    }
}
