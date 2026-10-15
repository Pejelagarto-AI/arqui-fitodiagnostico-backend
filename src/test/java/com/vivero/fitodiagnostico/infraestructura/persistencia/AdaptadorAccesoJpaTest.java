package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El adaptador de accesos compartidos (D19) contra H2 con Flyway real (V6). */
class AdaptadorAccesoJpaTest extends BaseDePersistenciaDePlantas {

    @Test
    void otorgarYListarLosInvitadosDeUnaPlanta() {
        Usuario dueno = usuario("dueno");
        Usuario beto = usuario("beto");
        Usuario carla = usuario("carla");
        Planta planta = planta(dueno, especie());

        accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), beto.id()));
        accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), carla.id()));
        sincronizar();

        assertThat(accesos.invitadosDe(planta.id())).containsExactlyInAnyOrder(beto.id(), carla.id());
        assertThat(accesos.invitadosDe(UUID.randomUUID())).isEmpty();
    }

    @Test
    void otorgarDosVecesElMismoAccesoDejaUnaSolaFila() {
        Usuario dueno = usuario("dueno");
        Usuario beto = usuario("beto");
        Planta planta = planta(dueno, especie());

        accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), beto.id()));
        accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), beto.id()));
        sincronizar();

        assertThat(jdbc.queryForObject("select count(*) from acceso_compartido where planta_id = ?", Integer.class,
                planta.id())).isEqualTo(1);
    }

    @Test
    void revocarQuitaSoloAEseInvitadoYEsIdempotente() {
        Usuario dueno = usuario("dueno");
        Usuario beto = usuario("beto");
        Usuario carla = usuario("carla");
        Planta planta = planta(dueno, especie());
        accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), beto.id()));
        accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), carla.id()));

        accesos.revocar(planta.id(), beto.id());
        accesos.revocar(planta.id(), beto.id());
        accesos.revocar(planta.id(), UUID.randomUUID());
        sincronizar();

        assertThat(accesos.invitadosDe(planta.id())).containsExactly(carla.id());
    }

    @Test
    void revocarTodosDeUnaPlantaNoTocaLasDeOtra() {
        Usuario dueno = usuario("dueno");
        Usuario beto = usuario("beto");
        Especie especie = especie();
        Planta una = planta(dueno, especie);
        Planta otra = planta(dueno, especie);
        accesos.otorgar(AccesoCompartido.otorgadoAhora(una.id(), beto.id()));
        accesos.otorgar(AccesoCompartido.otorgadoAhora(otra.id(), beto.id()));

        accesos.revocarTodosDe(una.id());
        sincronizar();

        assertThat(accesos.invitadosDe(una.id())).isEmpty();
        assertThat(accesos.invitadosDe(otra.id())).containsExactly(beto.id());
    }

    @Test
    void revocarTodosDelInvitadoLoSacaDeTodasLasPlantasYNoDeLosDemas() {
        Usuario dueno = usuario("dueno");
        Usuario beto = usuario("beto");
        Usuario carla = usuario("carla");
        Especie especie = especie();
        Planta una = planta(dueno, especie);
        Planta otra = planta(dueno, especie);
        accesos.otorgar(AccesoCompartido.otorgadoAhora(una.id(), beto.id()));
        accesos.otorgar(AccesoCompartido.otorgadoAhora(otra.id(), beto.id()));
        accesos.otorgar(AccesoCompartido.otorgadoAhora(otra.id(), carla.id()));

        accesos.revocarTodosDelInvitado(beto.id());
        sincronizar();

        assertThat(accesos.invitadosDe(una.id())).isEmpty();
        assertThat(accesos.invitadosDe(otra.id())).containsExactly(carla.id());
    }
}
