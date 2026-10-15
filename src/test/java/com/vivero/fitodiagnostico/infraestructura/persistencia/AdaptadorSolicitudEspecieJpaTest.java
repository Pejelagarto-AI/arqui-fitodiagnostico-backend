package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoSolicitud;
import com.vivero.fitodiagnostico.dominio.modelo.SolicitudEspecie;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El adaptador de solicitudes de especie (V10) contra H2 con Flyway real. */
class AdaptadorSolicitudEspecieJpaTest extends BaseDePersistenciaDePlantas {

    private SolicitudEspecie nueva(Usuario solicitante, String nombre, Instant creadaEn) {
        return SolicitudEspecie.nueva(solicitante.id(), nombre, "justificación de " + nombre, creadaEn);
    }

    @Test
    void guardarYBuscarDevuelveLoMismoIncluidosLosCamposOpcionales() {
        Usuario ana = usuario("ana");
        SolicitudEspecie sinResolver = nueva(ana, "Monstera", ahora());
        SolicitudEspecie sinJustificacion = SolicitudEspecie.nueva(ana.id(), "Ficus", null, ahora());

        solicitudes.guardar(sinResolver);
        solicitudes.guardar(sinJustificacion);
        sincronizar();

        assertThat(solicitudes.buscar(sinResolver.id())).contains(sinResolver);
        assertThat(solicitudes.buscar(sinJustificacion.id())).contains(sinJustificacion);
        assertThat(solicitudes.buscar(UUID.randomUUID())).isEmpty();
    }

    @Test
    void resolverLaActualizaConRespuestaYFecha() {
        Usuario ana = usuario("ana");
        SolicitudEspecie solicitud = nueva(ana, "Monstera", ahora());
        solicitudes.guardar(solicitud);
        Instant resueltaEn = ahora().plusSeconds(10);

        solicitudes.guardar(solicitud.rechazada("ya existe", resueltaEn));
        sincronizar();

        SolicitudEspecie leida = solicitudes.buscar(solicitud.id()).orElseThrow();
        assertThat(leida.estado()).isEqualTo(EstadoSolicitud.RECHAZADA);
        assertThat(leida.respuesta()).isEqualTo("ya existe");
        assertThat(leida.resueltaEn()).isEqualTo(resueltaEn);
    }

    @Test
    void pendientesSoloListaLasPendientesLaMasAntiguaPrimero() {
        Usuario ana = usuario("ana");
        Usuario beto = usuario("beto");
        Instant t = ahora();
        SolicitudEspecie antigua = nueva(ana, "Antigua-" + UUID.randomUUID(), t.minusSeconds(120));
        SolicitudEspecie reciente = nueva(beto, "Reciente-" + UUID.randomUUID(), t);
        SolicitudEspecie resuelta = nueva(ana, "Resuelta-" + UUID.randomUUID(), t.minusSeconds(60)).aprobada("ok", t);
        solicitudes.guardar(reciente);
        solicitudes.guardar(resuelta);
        solicitudes.guardar(antigua);
        sincronizar();

        var pendientes = solicitudes.pendientes().stream()
                .filter(s -> s.id().equals(antigua.id()) || s.id().equals(reciente.id()) || s.id().equals(resuelta.id()))
                .toList();

        assertThat(pendientes).extracting(SolicitudEspecie::id).containsExactly(antigua.id(), reciente.id());
    }

    @Test
    void deSolicitanteListaSoloLasDeEsaPersonaLaMasRecientePrimero() {
        Usuario ana = usuario("ana");
        Usuario beto = usuario("beto");
        Instant t = ahora();
        SolicitudEspecie vieja = nueva(ana, "Vieja", t.minusSeconds(60));
        SolicitudEspecie nueva = nueva(ana, "Nueva", t);
        SolicitudEspecie deBeto = nueva(beto, "DeBeto", t);
        solicitudes.guardar(vieja);
        solicitudes.guardar(nueva);
        solicitudes.guardar(deBeto);
        sincronizar();

        assertThat(solicitudes.deSolicitante(ana.id())).extracting(SolicitudEspecie::id)
                .containsExactly(nueva.id(), vieja.id());
        assertThat(solicitudes.deSolicitante(UUID.randomUUID())).isEmpty();
    }

    @Test
    void eliminarDeUnSolicitanteBorraTodasLasSuyasResueltasONo() {
        Usuario ana = usuario("ana");
        Usuario beto = usuario("beto");
        SolicitudEspecie pendiente = nueva(ana, "Pendiente", ahora());
        SolicitudEspecie resuelta = nueva(ana, "Resuelta", ahora()).aprobada("ok", ahora());
        SolicitudEspecie deBeto = nueva(beto, "DeBeto", ahora());
        solicitudes.guardar(pendiente);
        solicitudes.guardar(resuelta);
        solicitudes.guardar(deBeto);

        solicitudes.eliminarDe(ana.id());
        sincronizar();

        assertThat(solicitudes.deSolicitante(ana.id())).isEmpty();
        assertThat(solicitudes.buscar(deBeto.id())).isPresent();
    }

    @Test
    void laBaseNoDejaEliminarUnUsuarioConSolicitudesSinPasarPorLaCascada() {
        Usuario ana = usuario("ana");
        solicitudes.guardar(nueva(ana, "Monstera", ahora()));

        assertThatThrownBy(() -> {
            usuarios.eliminar(ana.id());
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }
}
