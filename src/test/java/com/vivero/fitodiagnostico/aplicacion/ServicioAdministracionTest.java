package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dobles.CifradorFalso;
import com.vivero.fitodiagnostico.dobles.EspeciesEnMemoria;
import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dobles.MundoEnMemoria;
import com.vivero.fitodiagnostico.dobles.UsuariosEnMemoria;
import com.vivero.fitodiagnostico.dominio.excepcion.CorreoYaRegistradoException;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieYaExisteException;
import com.vivero.fitodiagnostico.dominio.excepcion.UltimoAdministradorException;
import com.vivero.fitodiagnostico.dominio.excepcion.UsuarioNoEncontradoException;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAdministradores;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prueba de aplicación: {@link ServicioAdministracion} con dobles en memoria, sin Spring ni base. */
class ServicioAdministracionTest {

    private UsuariosEnMemoria usuarios;
    private EspeciesEnMemoria especies;
    private MundoEnMemoria mundo;
    private ServicioAdministracion servicio;
    private Usuario admin;
    private Usuario ana;

    private static Map<Magnitud, Rango> rangosValidos() {
        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        rangos.put(Magnitud.HUMEDAD, new Rango(30.0, 60.0));
        rangos.put(Magnitud.LUZ, new Rango(500.0, 1000.0));
        rangos.put(Magnitud.TEMPERATURA, new Rango(18.0, 26.0));
        return rangos;
    }

    @BeforeEach
    void preparar() {
        usuarios = new UsuariosEnMemoria();
        especies = new EspeciesEnMemoria(new Especie("helecho", rangosValidos()));
        mundo = new MundoEnMemoria();
        servicio = new ServicioAdministracion(usuarios, new PoliticaDeAdministradores(), especies, especies,
                mundo.plantas, mundo.cascada);
        CifradorFalso cifrador = new CifradorFalso();
        admin = Usuario.nuevo("Admin", "admin@vivero.co", cifrador.cifrar("x")).conRolAdministrador();
        ana = Usuario.nuevo("Ana", "ana@vivero.co", cifrador.cifrar("x"));
        usuarios.guardar(admin);
        usuarios.guardar(ana);
    }

    // ---- usuarios ----

    @Test
    void listarSinTextoDevuelveTodosYConTextoFiltraPorNombreOCorreo() {
        assertThat(servicio.listarUsuarios(null)).hasSize(2);
        assertThat(servicio.listarUsuarios("  ")).hasSize(2);
        assertThat(servicio.listarUsuarios("ANA")).containsExactly(ana);
        assertThat(servicio.listarUsuarios("admin@")).containsExactly(admin);
        assertThat(servicio.listarUsuarios("nadie")).isEmpty();
    }

    @Test
    void detalleDeUnUsuarioInexistenteLanza() {
        assertThatThrownBy(() -> servicio.detalleDeUsuario(UUID.randomUUID()))
                .isInstanceOf(UsuarioNoEncontradoException.class);
    }

    @Test
    void elDetalleIncluyeLasPlantasDelUsuarioYSoloLasSuyas() {
        // RF-B2.
        Planta deAna = Planta.nueva(ana.id(), "Pepa", Muestras.especie("helecho"));
        Planta deAdmin = Planta.nueva(admin.id(), "Otra", Muestras.especie("helecho"));
        mundo.plantas.guardar(deAna);
        mundo.plantas.guardar(deAdmin);

        ServicioAdministracion.DetalleDeUsuario detalle = servicio.detalleDeUsuario(ana.id());

        assertThat(detalle.usuario()).isEqualTo(ana);
        assertThat(detalle.plantas()).containsExactly(deAna);
        assertThat(servicio.detalleDeUsuario(admin.id()).plantas())
                .containsExactly(deAdmin);
    }

    @Test
    void eliminarUnUsuarioEliminaSusPlantasYAvisaALosInvitados() {
        // RN12 por la vía del administrador (RF-B4): misma cascada que la baja propia.
        Planta deAna = Planta.nueva(ana.id(), "Pepa", Muestras.especie("helecho"));
        mundo.plantas.guardar(deAna);
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(deAna.id(), admin.id()));

        servicio.eliminarUsuario(ana.id());

        assertThat(mundo.plantas.buscar(deAna.id())).isEmpty();
        assertThat(mundo.accesos.invitadosDe(deAna.id())).isEmpty();
        assertThat(mundo.notificador.avisosDeTipo(TipoDeAviso.ACCESO_PERDIDO)).singleElement()
                .satisfies(aviso -> assertThat(aviso.destinatarios()).containsExactly(admin.id()));
    }

    @Test
    void editarUsuarioCambiaSusDatosYRespetaLaUnicidadDelCorreo() {
        assertThat(servicio.editarUsuario(ana.id(), "Ana Z.", "ana.z@vivero.co").correo()).isEqualTo("ana.z@vivero.co");
        assertThatThrownBy(() -> servicio.editarUsuario(ana.id(), "Ana", "admin@vivero.co"))
                .isInstanceOf(CorreoYaRegistradoException.class);
    }

    @Test
    void otorgarYRevocarElRolSonIdempotentes() {
        assertThat(servicio.otorgarAdministrador(ana.id()).esAdministrador()).isTrue();
        assertThat(servicio.otorgarAdministrador(ana.id()).esAdministrador()).isTrue();
        assertThat(usuarios.contarAdministradores()).isEqualTo(2);

        assertThat(servicio.revocarAdministrador(ana.id()).esAdministrador()).isFalse();
        assertThat(servicio.revocarAdministrador(ana.id()).esAdministrador()).isFalse();
    }

    @Test
    void noSePuedeRevocarElRolAlUnicoAdministrador() {
        assertThatThrownBy(() -> servicio.revocarAdministrador(admin.id()))
                .isInstanceOf(UltimoAdministradorException.class);
        assertThat(usuarios.buscarPorId(admin.id()).orElseThrow().esAdministrador()).isTrue();
    }

    @Test
    void conDosAdministradoresSePuedeRevocarAUno() {
        servicio.otorgarAdministrador(ana.id());

        servicio.revocarAdministrador(admin.id());

        assertThat(usuarios.contarAdministradores()).isEqualTo(1);
    }

    @Test
    void noSePuedeEliminarAlUnicoAdministradorPeroSiAUnCliente() {
        assertThatThrownBy(() -> servicio.eliminarUsuario(admin.id()))
                .isInstanceOf(UltimoAdministradorException.class);

        servicio.eliminarUsuario(ana.id());

        assertThat(usuarios.buscarPorId(ana.id())).isEmpty();
        assertThat(usuarios.buscarPorId(admin.id())).isPresent();
    }

    @Test
    void eliminarYRevocarAUnUsuarioInexistenteLanza() {
        UUID fantasma = UUID.randomUUID();
        assertThatThrownBy(() -> servicio.eliminarUsuario(fantasma)).isInstanceOf(UsuarioNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.revocarAdministrador(fantasma)).isInstanceOf(UsuarioNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.otorgarAdministrador(fantasma)).isInstanceOf(UsuarioNoEncontradoException.class);
    }

    // ---- especies ----

    @Test
    void crearEspecieLaGuardaActiva() {
        Especie creada = servicio.crearEspecie("orquidea", rangosValidos());

        assertThat(creada.activa()).isTrue();
        assertThat(especies.buscar("orquidea")).contains(creada);
    }

    @Test
    void crearEspecieConUnNombreExistenteLanzaAunqueEstéInactiva() {
        servicio.desactivarEspecie("helecho");

        assertThatThrownBy(() -> servicio.crearEspecie("HELECHO", rangosValidos()))
                .isInstanceOf(EspecieYaExisteException.class);
    }

    @Test
    void crearEspecieExigeLasTresMagnitudes() {
        Map<Magnitud, Rango> incompletos = new EnumMap<>(Magnitud.class);
        incompletos.put(Magnitud.HUMEDAD, new Rango(30.0, 60.0));

        assertThatThrownBy(() -> servicio.crearEspecie("orquidea", incompletos))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("luz");
        assertThat(especies.buscar("orquidea")).isEmpty();
    }

    @Test
    void elRangoOptimoDebeCaberEnElRangoFisicoDelSensor() {
        Map<Magnitud, Rango> fueraDeSensor = rangosValidos();
        fueraDeSensor.put(Magnitud.HUMEDAD, new Rango(50.0, 150.0));

        assertThatThrownBy(() -> servicio.crearEspecie("orquidea", fueraDeSensor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("humedad");
    }

    @Test
    void editarEspecieCambiaLosRangosYConservaLaActivacion() {
        servicio.desactivarEspecie("helecho");
        Map<Magnitud, Rango> nuevos = rangosValidos();
        nuevos.put(Magnitud.HUMEDAD, new Rango(10.0, 20.0));

        Especie editada = servicio.editarEspecie("helecho", nuevos);

        assertThat(editada.activa()).isFalse();
        assertThat(especies.buscar("helecho").orElseThrow().rango(Magnitud.HUMEDAD)).isEqualTo(new Rango(10.0, 20.0));
    }

    @Test
    void editarUnaEspecieInexistenteLanza() {
        assertThatThrownBy(() -> servicio.editarEspecie("fantasma", rangosValidos()))
                .isInstanceOf(EspecieNoEncontradaException.class);
    }

    @Test
    void desactivarYReactivarCambianSoloElEstadoYSonIdempotentes() {
        assertThat(servicio.desactivarEspecie("helecho").activa()).isFalse();
        assertThat(servicio.desactivarEspecie("helecho").activa()).isFalse();
        assertThat(especies.buscar("helecho").orElseThrow().activa()).isFalse();
        // sigue en el catálogo y se puede buscar: RN11
        assertThat(especies.listar()).hasSize(1);

        assertThat(servicio.reactivarEspecie("helecho").activa()).isTrue();
        assertThat(especies.buscar("helecho").orElseThrow().activa()).isTrue();
    }

    @Test
    void desactivarOReactivarUnaEspecieInexistenteLanza() {
        assertThatThrownBy(() -> servicio.desactivarEspecie("fantasma")).isInstanceOf(EspecieNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.reactivarEspecie("fantasma")).isInstanceOf(EspecieNoEncontradaException.class);
    }
}
