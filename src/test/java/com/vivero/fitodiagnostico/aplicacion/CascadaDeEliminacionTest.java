package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dobles.MundoEnMemoria;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.Invitacion;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.SolicitudEspecie;
import com.vivero.fitodiagnostico.dominio.modelo.SolicitudRestablecimiento;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** La cascada sola (RN12, D11); los servicios que la usan prueban además su propio camino. */
class CascadaDeEliminacionTest {

    private final MundoEnMemoria mundo = new MundoEnMemoria();
    private final UUID dueno = UUID.randomUUID();
    private final UUID invitadoA = UUID.randomUUID();
    private final UUID invitadoB = UUID.randomUUID();

    private Planta plantaConHistorial(String apodo, UUID duenoId) {
        Planta planta = Planta.nueva(duenoId, apodo, Muestras.especie("helecho"));
        mundo.plantas.guardar(planta);
        mundo.diagnosticos.guardar(DiagnosticoDePlanta.deUnaEvaluacion(planta.id(), OrigenDeMedicion.MANUAL,
                Muestras.diagnosticoCon(EstadoGlobal.SALUDABLE)));
        return planta;
    }

    @Test
    void eliminarUnaPlantaAvisaUnaSolaVezATodosLosInvitadosConElTipoCorrecto() {
        Planta planta = plantaConHistorial("Pepa", dueno);
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), invitadoA));
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), invitadoB));

        mundo.cascada.eliminarPlanta(planta);

        assertThat(mundo.notificador.avisos()).singleElement().satisfies(aviso -> {
            assertThat(aviso.tipo()).isEqualTo(TipoDeAviso.ACCESO_PERDIDO);
            assertThat(aviso.destinatarios()).containsExactlyInAnyOrder(invitadoA, invitadoB);
        });
        assertThat(mundo.accesos.cuantos()).isZero();
        assertThat(mundo.diagnosticos.cuantos()).isZero();
        assertThat(mundo.plantas.cuantas()).isZero();
    }

    @Test
    void eliminarLoDeUnUsuarioBorraTodasSusPlantasYSusAccesosComoInvitadoPeroNoLasAjenas() {
        Planta uno = plantaConHistorial("Uno", dueno);
        Planta dos = plantaConHistorial("Dos", dueno);
        Planta ajena = plantaConHistorial("Ajena", invitadoA);
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(uno.id(), invitadoA));
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(ajena.id(), dueno));   // el usuario era invitado de otro
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(ajena.id(), invitadoB));

        mundo.cascada.eliminarLoDe(dueno);

        assertThat(mundo.plantas.buscar(uno.id())).isEmpty();
        assertThat(mundo.plantas.buscar(dos.id())).isEmpty();
        assertThat(mundo.plantas.buscar(ajena.id())).isPresent();
        assertThat(mundo.accesos.invitadosDe(ajena.id())).containsExactly(invitadoB);   // solo se fue él
        assertThat(mundo.diagnosticos.ultimoDe(ajena.id())).isPresent();
        // Solo la planta "Uno" tenía invitados: un aviso. Que el usuario salga de una ajena no avisa a nadie.
        assertThat(mundo.notificador.avisos()).singleElement()
                .satisfies(aviso -> assertThat(aviso.destinatarios()).containsExactly(invitadoA));
    }

    @Test
    void unUsuarioSinNadaNoHaceNadaNiFalla() {
        mundo.cascada.eliminarLoDe(UUID.randomUUID());

        assertThat(mundo.notificador.avisos()).isEmpty();
    }

    @Test
    void eliminarUnaPlantaBorraSusInvitacionesPendientesPeroNoLasDeOtraPlanta() {
        Planta planta = plantaConHistorial("Pepa", dueno);
        Planta otra = plantaConHistorial("Otra", dueno);
        mundo.invitaciones.guardar(Invitacion.nueva(planta.id(), "h1", Instant.now(), null));
        mundo.invitaciones.guardar(Invitacion.nueva(planta.id(), "h2", Instant.now(), null));
        mundo.invitaciones.guardar(Invitacion.nueva(otra.id(), "h3", Instant.now(), null));

        mundo.cascada.eliminarPlanta(planta);

        assertThat(mundo.invitaciones.todas()).singleElement().satisfies(i -> assertThat(i.plantaId()).isEqualTo(otra.id()));
    }

    @Test
    void eliminarLoDeUnUsuarioBorraSusSolicitudesDeEspecieYDeRestablecimientoPeroNoLasAjenas() {
        Instant ahora = Instant.now();
        mundo.solicitudes.guardar(SolicitudEspecie.nueva(dueno, "Monstera", null, ahora));
        mundo.solicitudes.guardar(SolicitudEspecie.nueva(dueno, "Ficus", null, ahora).aprobada("ok", ahora));
        mundo.solicitudes.guardar(SolicitudEspecie.nueva(invitadoA, "Calathea", null, ahora));
        mundo.restablecimientos.guardar(SolicitudRestablecimiento.nueva(dueno, "t1", "c1", ahora));
        mundo.restablecimientos.guardar(SolicitudRestablecimiento.nueva(invitadoA, "t2", "c2", ahora));

        mundo.cascada.eliminarLoDe(dueno);

        assertThat(mundo.solicitudes.deSolicitante(dueno)).isEmpty();
        assertThat(mundo.solicitudes.deSolicitante(invitadoA)).hasSize(1);
        assertThat(mundo.restablecimientos.cuantas()).isEqualTo(1);
        assertThat(mundo.restablecimientos.todas().get(0).usuarioId()).isEqualTo(invitadoA);
    }
}
