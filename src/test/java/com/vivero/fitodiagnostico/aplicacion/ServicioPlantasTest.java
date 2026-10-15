package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dobles.EspeciesEnMemoria;
import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dobles.MundoEnMemoria;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieInactivaException;
import com.vivero.fitodiagnostico.dominio.excepcion.EspecieNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.excepcion.OperacionNoPermitidaException;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Prueba de aplicación (RA4/RA5): {@link ServicioPlantas} con dobles en memoria y la política real. Sin Spring ni base. */
class ServicioPlantasTest {

    private final UUID dueno = UUID.randomUUID();
    private final UUID invitado = UUID.randomUUID();
    private final UUID ajeno = UUID.randomUUID();

    private MundoEnMemoria mundo;
    private EspeciesEnMemoria especies;
    private ServicioPlantas servicio;

    @BeforeEach
    void preparar() {
        mundo = new MundoEnMemoria();
        especies = new EspeciesEnMemoria(Muestras.especie("helecho"), Muestras.especie("potos"),
                Muestras.especieInactiva("lavanda"));
        servicio = new ServicioPlantas(mundo.plantas, mundo.accesos, new PoliticaDeAcceso(), especies, mundo.cascada);
    }

    private Planta plantaConInvitado() {
        Planta planta = servicio.registrar(dueno, "Pepa", "helecho");
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), invitado));
        return planta;
    }

    // ---- registrar (RF-D1, RN11) ----

    @Test
    void registrarGuardaLaPlantaDelSolicitanteConLaEspecieDelCatalogo() {
        Planta planta = servicio.registrar(dueno, " Pepa ", "HELECHO");

        assertThat(mundo.plantas.buscar(planta.id())).contains(planta);
        assertThat(planta.duenoId()).isEqualTo(dueno);
        assertThat(planta.apodo()).isEqualTo("Pepa");
        assertThat(planta.especie().nombre()).isEqualTo("helecho");   // la del catálogo, no la que se escribió
    }

    @Test
    void registrarConEspecieInexistenteLanzaYNoGuarda() {
        assertThatThrownBy(() -> servicio.registrar(dueno, "Pepa", "cactus"))
                .isInstanceOf(EspecieNoEncontradaException.class);
        assertThat(mundo.plantas.cuantas()).isZero();
    }

    @Test
    void registrarConEspecieDescontinuadaLanzaYNoGuarda() {
        // RN11.
        assertThatThrownBy(() -> servicio.registrar(dueno, "Pepa", "lavanda"))
                .isInstanceOf(EspecieInactivaException.class);
        assertThat(mundo.plantas.cuantas()).isZero();
    }

    // ---- listar y detalle (RF-D2, RF-D3, RN3, RN7) ----

    @Test
    void listarPropiasDevuelveSoloLasDelSolicitante() {
        Planta mia = servicio.registrar(dueno, "Mia", "helecho");
        servicio.registrar(ajeno, "De otro", "potos");
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(
                servicio.registrar(ajeno, "Compartida", "potos").id(), dueno));

        assertThat(servicio.listarPropias(dueno)).containsExactly(mia);   // las compartidas no son "propias"
        assertThat(servicio.listarPropias(UUID.randomUUID())).isEmpty();   // RN7: 0..N
    }

    @Test
    void detalleLoVenElDuenoYElInvitado() {
        Planta planta = plantaConInvitado();

        assertThat(servicio.detalle(dueno, planta.id())).isEqualTo(planta);
        assertThat(servicio.detalle(invitado, planta.id())).isEqualTo(planta);
    }

    @Test
    void detalleDeUnaPlantaAjenaYDeUnaInexistenteDanLaMismaExcepcion() {
        // RN3 + D35: no se filtra qué ids existen.
        Planta planta = plantaConInvitado();
        UUID inexistente = UUID.randomUUID();

        Throwable deAjena = org.assertj.core.api.Assertions.catchThrowable(() -> servicio.detalle(ajeno, planta.id()));
        Throwable deInexistente = org.assertj.core.api.Assertions.catchThrowable(() -> servicio.detalle(ajeno, inexistente));

        assertThat(deAjena).isInstanceOf(PlantaNoEncontradaException.class);
        assertThat(deInexistente).isInstanceOf(PlantaNoEncontradaException.class);
        assertThat(deAjena.getMessage().replace(planta.id().toString(), "ID"))
                .isEqualTo(deInexistente.getMessage().replace(inexistente.toString(), "ID"));
    }

    // ---- editar (RF-D4, RN5, RN11) ----

    @Test
    void editarCambiaApodoYEspecieDeLaPlantaDelDueno() {
        Planta planta = servicio.registrar(dueno, "Pepa", "helecho");

        Planta editada = servicio.editar(dueno, planta.id(), "Pepita", "potos");

        assertThat(editada.apodo()).isEqualTo("Pepita");
        assertThat(editada.especie().nombre()).isEqualTo("potos");
        assertThat(editada.id()).isEqualTo(planta.id());
        assertThat(editada.registradaEn()).isEqualTo(planta.registradaEn());
        assertThat(mundo.plantas.buscar(planta.id())).contains(editada);
    }

    @Test
    void editarAUnaEspecieDescontinuadaLanzaYNoCambiaNada() {
        Planta planta = servicio.registrar(dueno, "Pepa", "helecho");

        assertThatThrownBy(() -> servicio.editar(dueno, planta.id(), "Pepa", "lavanda"))
                .isInstanceOf(EspecieInactivaException.class);
        assertThat(mundo.plantas.buscar(planta.id())).contains(planta);
    }

    @Test
    void unaPlantaCuyaEspecieSeDescontinuoDespuesSigueEditandoseSiNoCambiaDeEspecie() {
        // RN11: "las plantas que ya la usan siguen funcionando".
        Planta existente = Planta.nueva(dueno, "Pepa", Muestras.especie("potos"));
        mundo.plantas.guardar(existente);
        especies.guardar(Muestras.especie("potos").desactivada());

        Planta editada = servicio.editar(dueno, existente.id(), "Pepita", "potos");

        assertThat(editada.apodo()).isEqualTo("Pepita");
        assertThat(editada.especie().activa()).isFalse();
    }

    @Test
    void elInvitadoNoEditaYRecibeOperacionNoPermitida() {
        Planta planta = plantaConInvitado();

        assertThatThrownBy(() -> servicio.editar(invitado, planta.id(), "Mio", "helecho"))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThat(mundo.plantas.buscar(planta.id())).contains(planta);
    }

    @Test
    void unAjenoNoEditaYNoSabeSiExiste() {
        Planta planta = plantaConInvitado();

        assertThatThrownBy(() -> servicio.editar(ajeno, planta.id(), "Mio", "helecho"))
                .isInstanceOf(PlantaNoEncontradaException.class);
    }

    // ---- eliminar (RF-D5, RN12, D11) ----

    @Test
    void eliminarBorraLaPlantaSuHistorialYSusAccesosYAvisaALosInvitados() {
        Planta planta = plantaConInvitado();
        mundo.diagnosticos.guardar(DiagnosticoDePlanta.deUnaEvaluacion(
                planta.id(), OrigenDeMedicion.MANUAL, Muestras.diagnosticoCon(EstadoGlobal.SALUDABLE)));
        // Un historial ajeno a esta planta no se toca.
        mundo.diagnosticos.guardar(DiagnosticoDePlanta.deUnaEvaluacion(
                UUID.randomUUID(), OrigenDeMedicion.MANUAL, Muestras.diagnosticoCon(EstadoGlobal.SALUDABLE)));

        servicio.eliminar(dueno, planta.id());

        assertThat(mundo.plantas.buscar(planta.id())).isEmpty();
        assertThat(mundo.accesos.invitadosDe(planta.id())).isEmpty();
        assertThat(mundo.diagnosticos.ultimoDe(planta.id())).isEmpty();
        assertThat(mundo.diagnosticos.cuantos()).isEqualTo(1);
        assertThat(mundo.notificador.avisos()).singleElement().satisfies(aviso -> {
            assertThat(aviso.tipo()).isEqualTo(TipoDeAviso.ACCESO_PERDIDO);
            assertThat(aviso.destinatarios()).containsExactly(invitado);   // el dueño no se avisa a sí mismo
            assertThat(aviso.mensaje()).contains("Pepa");
        });
    }

    @Test
    void eliminarUnaPlantaSinInvitadosNoAvisaANadie() {
        Planta planta = servicio.registrar(dueno, "Pepa", "helecho");

        servicio.eliminar(dueno, planta.id());

        assertThat(mundo.plantas.buscar(planta.id())).isEmpty();
        assertThat(mundo.notificador.avisos()).isEmpty();
    }

    @Test
    void elInvitadoNoEliminaNiElAjenoSabeQueExiste() {
        Planta planta = plantaConInvitado();

        assertThatThrownBy(() -> servicio.eliminar(invitado, planta.id()))
                .isInstanceOf(OperacionNoPermitidaException.class);
        assertThatThrownBy(() -> servicio.eliminar(ajeno, planta.id()))
                .isInstanceOf(PlantaNoEncontradaException.class);

        assertThat(mundo.plantas.buscar(planta.id())).isPresent();
        assertThat(mundo.accesos.invitadosDe(planta.id())).containsExactly(invitado);
        assertThat(mundo.notificador.avisos()).isEmpty();
    }
}
