package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.excepcion.CorreoYaRegistradoException;
import com.vivero.fitodiagnostico.dominio.modelo.Credencial;
import com.vivero.fitodiagnostico.dominio.modelo.Rol;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El adaptador de usuarios contra H2 con Flyway real (V5). Cada prueba corre en
 * una transacción que se deshace, pero la base H2 es compartida entre pruebas
 * de la JVM, así que ninguna asume que está vacía: cuentan lo que ellas mismas
 * crearon, con correos propios.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AdaptadorUsuarioJpa.class, MapeadorUsuario.class})
class AdaptadorUsuarioJpaTest {

    @Autowired
    private AdaptadorUsuarioJpa adaptador;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    private static Usuario usuario(String nombre, String correo) {
        return Usuario.nuevo(nombre, correo, new Credencial("hash-de-" + nombre));
    }

    @Test
    void guardarYBuscarPorIdYCorreoDevuelveElMismoUsuario() {
        Usuario ana = usuario("Ana", "ana-jpa@vivero.co");

        adaptador.guardar(ana);

        assertThat(adaptador.buscarPorId(ana.id())).contains(ana);
        assertThat(adaptador.buscarPorCorreo("ana-jpa@vivero.co")).contains(ana);
        assertThat(adaptador.buscarPorCorreo("otro@vivero.co")).isEmpty();
    }

    @Test
    void guardarDeNuevoActualizaYNoDuplica() {
        Usuario ana = usuario("Ana", "ana-upd@vivero.co");
        adaptador.guardar(ana);

        adaptador.guardar(ana.conDatos("Ana María", "ana-upd2@vivero.co").conRolAdministrador());

        Usuario leido = adaptador.buscarPorId(ana.id()).orElseThrow();
        assertThat(leido.nombre()).isEqualTo("Ana María");
        assertThat(leido.correo()).isEqualTo("ana-upd2@vivero.co");
        assertThat(leido.roles()).containsExactlyInAnyOrder(Rol.CLIENTE, Rol.ADMINISTRADOR);
        assertThat(jdbc.queryForObject("select count(*) from usuario where id = ?", Integer.class, ana.id()))
                .isEqualTo(1);
    }

    @Test
    void revocarElRolBorraSoloLaFilaDeAdministrador() {
        Usuario admin = usuario("Admin", "admin-jpa@vivero.co").conRolAdministrador();
        adaptador.guardar(admin);

        adaptador.guardar(admin.sinRolAdministrador());

        assertThat(adaptador.buscarPorId(admin.id()).orElseThrow().roles()).containsExactly(Rol.CLIENTE);
        assertThat(jdbc.queryForObject("select count(*) from usuario_rol where usuario_id = ?", Integer.class, admin.id()))
                .isEqualTo(1);
    }

    @Test
    void elCorreoDuplicadoLoRechazaLaBaseYSeTraduceAlErrorDeNegocio() {
        adaptador.guardar(usuario("Ana", "dup-jpa@vivero.co"));

        // RN1 como red de seguridad: aunque el servicio no hubiera comprobado antes.
        assertThatThrownBy(() -> adaptador.guardar(usuario("Otra", "dup-jpa@vivero.co")))
                .isInstanceOf(CorreoYaRegistradoException.class);
    }

    @Test
    void contarAdministradoresCuentaSoloLosQueTienenElRol() {
        long antes = adaptador.contarAdministradores();

        adaptador.guardar(usuario("Cliente", "cli-jpa@vivero.co"));
        adaptador.guardar(usuario("Admin1", "adm1-jpa@vivero.co").conRolAdministrador());
        adaptador.guardar(usuario("Admin2", "adm2-jpa@vivero.co").conRolAdministrador());

        assertThat(adaptador.contarAdministradores()).isEqualTo(antes + 2);
    }

    @Test
    void buscarFiltraPorNombreOCorreoSinDistinguirMayusculas() {
        adaptador.guardar(usuario("Zulema Búsqueda", "zul-jpa@vivero.co"));
        adaptador.guardar(usuario("Otro", "otro-jpa-zulema@vivero.co"));
        adaptador.guardar(usuario("Nadie", "nadie-jpa@vivero.co"));

        assertThat(adaptador.buscar("ZULEMA")).extracting(Usuario::correo)
                .containsExactlyInAnyOrder("zul-jpa@vivero.co", "otro-jpa-zulema@vivero.co");
    }

    @Test
    void buscarTrataLosComodinesComoTextoLiteral() {
        adaptador.guardar(usuario("Cien%", "pct-jpa@vivero.co"));
        adaptador.guardar(usuario("Cienxx", "nopct-jpa@vivero.co"));

        // Si "%" fuera comodín, "cien%" listaría a los dos.
        assertThat(adaptador.buscar("cien%")).extracting(Usuario::correo).containsExactly("pct-jpa@vivero.co");
        assertThat(adaptador.buscar("%")).extracting(Usuario::correo).containsExactly("pct-jpa@vivero.co");
        assertThat(adaptador.buscar("_")).isEmpty();
    }

    @Test
    void listarDevuelveOrdenadosPorNombre() {
        adaptador.guardar(usuario("Zeta-orden", "z-ord-jpa@vivero.co"));
        adaptador.guardar(usuario("Alfa-orden", "a-ord-jpa@vivero.co"));

        var nombres = adaptador.listar().stream().map(Usuario::nombre).toList();

        assertThat(nombres.indexOf("Alfa-orden")).isLessThan(nombres.indexOf("Zeta-orden"));
    }

    @Test
    void eliminarBorraAlUsuarioYSusRoles() {
        Usuario admin = usuario("Admin", "del-jpa@vivero.co").conRolAdministrador();
        adaptador.guardar(admin);

        adaptador.eliminar(admin.id());
        entityManager.flush();   // el borrado queda pendiente en la sesión hasta el flush; JDBC no lo vería

        assertThat(adaptador.buscarPorId(admin.id())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from usuario_rol where usuario_id = ?", Integer.class, admin.id()))
                .isZero();
    }

    @Test
    void eliminarUnIdInexistenteNoFalla() {
        adaptador.eliminar(java.util.UUID.randomUUID());
    }

    @Test
    void laBaseNuncaGuardaLaContrasenaEnClaroSoloElHashQueSeLeEntrego() {
        Usuario ana = usuario("Ana", "hash-jpa@vivero.co");
        adaptador.guardar(ana);

        assertThat(jdbc.queryForObject("select hash_contrasena from usuario where id = ?", String.class, ana.id()))
                .isEqualTo("hash-de-Ana");
    }
}
