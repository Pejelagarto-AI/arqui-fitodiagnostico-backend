package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.SolicitudRestablecimiento;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El adaptador de solicitudes de restablecimiento (V9) contra H2 con Flyway real. */
class AdaptadorRestablecimientoJpaTest extends BaseDePersistenciaDePlantas {

    private SolicitudRestablecimiento nueva(Usuario usuario) {
        return SolicitudRestablecimiento.nueva(usuario.id(), "token-" + UUID.randomUUID(), "cred-" + UUID.randomUUID(), ahora());
    }

    @Test
    void guardarYBuscarPorHuellaDevuelveLoMismo() {
        Usuario ana = usuario("ana");
        SolicitudRestablecimiento solicitud = nueva(ana);

        restablecimientos.guardar(solicitud);
        sincronizar();

        assertThat(restablecimientos.porHuella(solicitud.huellaDelToken())).contains(solicitud);
        assertThat(restablecimientos.porHuella("no-existe")).isEmpty();
    }

    @Test
    void consumirLaActualizaSinDuplicar() {
        Usuario ana = usuario("ana");
        SolicitudRestablecimiento solicitud = nueva(ana);
        restablecimientos.guardar(solicitud);

        restablecimientos.guardar(solicitud.consumida(ahora(), solicitud.huellaDeCredencial()));
        sincronizar();

        assertThat(restablecimientos.porHuella(solicitud.huellaDelToken()).orElseThrow().usado()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from restablecimiento where usuario_id = ?", Integer.class,
                ana.id())).isEqualTo(1);
    }

    @Test
    void eliminarDeUnUsuarioBorraSoloLasSuyas() {
        Usuario ana = usuario("ana");
        Usuario beto = usuario("beto");
        SolicitudRestablecimiento deAna = nueva(ana);
        SolicitudRestablecimiento otraDeAna = nueva(ana);
        SolicitudRestablecimiento deBeto = nueva(beto);
        restablecimientos.guardar(deAna);
        restablecimientos.guardar(otraDeAna);
        restablecimientos.guardar(deBeto);

        restablecimientos.eliminarDe(ana.id());
        sincronizar();

        assertThat(restablecimientos.porHuella(deAna.huellaDelToken())).isEmpty();
        assertThat(restablecimientos.porHuella(otraDeAna.huellaDelToken())).isEmpty();
        assertThat(restablecimientos.porHuella(deBeto.huellaDelToken())).isPresent();
    }

    @Test
    void laBaseNoDejaEliminarUnUsuarioConSolicitudesSinPasarPorLaCascada() {
        Usuario ana = usuario("ana");
        restablecimientos.guardar(nueva(ana));

        assertThatThrownBy(() -> {
            usuarios.eliminar(ana.id());
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }
}
