package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoInvitacion;
import com.vivero.fitodiagnostico.dominio.modelo.Invitacion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El adaptador de invitaciones (V8) contra H2 con Flyway real. */
class AdaptadorInvitacionJpaTest extends BaseDePersistenciaDePlantas {

    private Invitacion nueva(Planta planta, String huella, Instant creadaEn, String correo) {
        return Invitacion.nueva(planta.id(), huella + UUID.randomUUID(), creadaEn, correo);
    }

    @Test
    void guardarYLeerPorHuellaYPorIdDevuelveLoMismo() {
        Planta planta = planta(usuario("dueno"), especie());
        Invitacion invitacion = nueva(planta, "h-", ahora(), "ana@vivero.co");

        invitaciones.guardar(invitacion);
        sincronizar();

        assertThat(invitaciones.porHuella(invitacion.huellaDelToken())).contains(invitacion);
        assertThat(invitaciones.buscar(invitacion.id())).contains(invitacion);
        assertThat(invitaciones.porHuella("no-existe")).isEmpty();
        assertThat(invitaciones.buscar(UUID.randomUUID())).isEmpty();
    }

    @Test
    void guardarDeNuevoActualizaElEstadoSinDuplicar() {
        Planta planta = planta(usuario("dueno"), especie());
        Invitacion invitacion = nueva(planta, "h-", ahora(), null);
        invitaciones.guardar(invitacion);
        Instant aceptadaEn = ahora().plusSeconds(30);

        invitaciones.guardar(invitacion.aceptada(aceptadaEn));
        sincronizar();

        Invitacion leida = invitaciones.buscar(invitacion.id()).orElseThrow();
        assertThat(leida.estado()).isEqualTo(EstadoInvitacion.ACEPTADA);
        assertThat(leida.aceptadaEn()).isEqualTo(aceptadaEn);
        assertThat(jdbc.queryForObject("select count(*) from invitacion where planta_id = ?", Integer.class,
                planta.id())).isEqualTo(1);
    }

    @Test
    void enLaBaseSoloQuedaLaHuellaNuncaElToken() {
        Planta planta = planta(usuario("dueno"), especie());
        Invitacion invitacion = Invitacion.nueva(planta.id(), "a".repeat(64), ahora(), null);
        invitaciones.guardar(invitacion);
        sincronizar();

        assertThat(jdbc.queryForObject("select huella_token from invitacion where id = ?", String.class,
                invitacion.id())).isEqualTo("a".repeat(64));
        assertThat(jdbc.queryForList("select column_name from information_schema.columns "
                + "where lower(table_name) = 'invitacion'", String.class))
                .extracting(String::toLowerCase).doesNotContain("token");
    }

    @Test
    void pendientesDevuelveSoloLasPendientesDeEsaPlantaLaMasRecientePrimero() {
        Usuario dueno = usuario("dueno");
        Planta planta = planta(dueno, especie());
        Planta otra = planta(dueno, especie());
        Instant t = ahora();
        Invitacion vieja = nueva(planta, "h-", t.minusSeconds(60), null);
        Invitacion reciente = nueva(planta, "h-", t, null);
        Invitacion revocada = nueva(planta, "h-", t.minusSeconds(30), null).revocada(t);
        Invitacion deOtra = nueva(otra, "h-", t, null);
        for (Invitacion i : new Invitacion[]{vieja, reciente, revocada, deOtra}) {
            invitaciones.guardar(i);
        }
        sincronizar();

        assertThat(invitaciones.pendientesDe(planta.id())).extracting(Invitacion::id)
                .containsExactly(reciente.id(), vieja.id());
    }

    @Test
    void eliminarDeLaPlantaBorraSoloLasDeEsaPlanta() {
        Usuario dueno = usuario("dueno");
        Planta planta = planta(dueno, especie());
        Planta otra = planta(dueno, especie());
        Invitacion deLaPlanta = nueva(planta, "h-", ahora(), null);
        Invitacion deOtra = nueva(otra, "h-", ahora(), null);
        invitaciones.guardar(deLaPlanta);
        invitaciones.guardar(deOtra);

        invitaciones.eliminarDe(planta.id());
        sincronizar();

        assertThat(invitaciones.buscar(deLaPlanta.id())).isEmpty();
        assertThat(invitaciones.buscar(deOtra.id())).isPresent();
    }

    @Test
    void laHuellaEsUnica() {
        Planta planta = planta(usuario("dueno"), especie());
        invitaciones.guardar(Invitacion.nueva(planta.id(), "repetida", ahora(), null));

        assertThatThrownBy(() -> invitaciones.guardar(Invitacion.nueva(planta.id(), "repetida", ahora(), null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laBaseNoDejaEliminarUnaPlantaConInvitacionesSinPasarPorLaCascada() {
        // Red de seguridad de D37 también para V8.
        Planta planta = planta(usuario("dueno"), especie());
        invitaciones.guardar(nueva(planta, "h-", ahora(), null));

        assertThatThrownBy(() -> plantas.eliminar(planta.id())).isInstanceOf(DataIntegrityViolationException.class);
    }
}
