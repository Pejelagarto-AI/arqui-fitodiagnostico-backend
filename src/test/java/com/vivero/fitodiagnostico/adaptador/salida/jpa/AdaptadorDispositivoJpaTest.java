package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El adaptador de dispositivos (V12) contra H2 con Flyway real. */
class AdaptadorDispositivoJpaTest extends BaseDePersistenciaDePlantas {

    private static String serial() {
        return "fito-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Dispositivo deFabrica(Instant creadoEn) {
        return Dispositivo.deFabrica(serial(), "secreto-" + UUID.randomUUID(), creadoEn);
    }

    @Test
    void guardarYLeerPorSerialDevuelveLoMismoIncluidoElRetoPendiente() {
        Planta planta = planta(usuario("dueno"), especie());
        Instant t = ahora();
        Dispositivo d = deFabrica(t);
        d.reclamar(planta.id(), t.plusSeconds(1));
        d.registrarCredencial(t.plusSeconds(2));
        d.asignarReto("reto-base64==", t.plusSeconds(62));

        dispositivos.guardar(d);
        sincronizar();

        Dispositivo leido = dispositivos.porSerial(d.serial()).orElseThrow();
        assertThat(leido.secreto()).isEqualTo(d.secreto());
        assertThat(leido.plantaId()).contains(planta.id());
        assertThat(leido.reclamadoEn()).contains(t.plusSeconds(1));
        assertThat(leido.ultimaCredencialEn()).contains(t.plusSeconds(2));
        assertThat(leido.reto()).isEqualTo(d.reto());
        assertThat(leido.creadoEn()).isEqualTo(t);
        assertThat(dispositivos.porSerial("no-existe")).isEmpty();
    }

    @Test
    void unDispositivoDeFabricaSeGuardaSinPlantaNiReto() {
        Dispositivo d = deFabrica(ahora());

        dispositivos.guardar(d);
        sincronizar();

        Dispositivo leido = dispositivos.porSerial(d.serial()).orElseThrow();
        assertThat(leido.estaReclamado()).isFalse();
        assertThat(leido.reto()).isEmpty();
        assertThat(leido.ultimaCredencialEn()).isEmpty();
    }

    @Test
    void guardarDeNuevoActualizaSinDuplicarYDesvincularQuedaPersistido() {
        Planta planta = planta(usuario("dueno"), especie());
        Dispositivo d = deFabrica(ahora());
        d.reclamar(planta.id(), ahora());
        dispositivos.guardar(d);

        d.desvincular();
        dispositivos.guardar(d);
        sincronizar();

        assertThat(dispositivos.porSerial(d.serial()).orElseThrow().estaReclamado()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from dispositivo where serial = ?", Integer.class,
                d.serial())).isEqualTo(1);
    }

    @Test
    void porPlantaDevuelveSoloLosReclamadosPorEsaPlantaYTodosLosDevuelveTodos() {
        Usuario dueno = usuario("dueno");
        Planta una = planta(dueno, especie());
        Planta otra = planta(dueno, especie());
        Instant t = ahora();
        Dispositivo a = deFabrica(t);
        Dispositivo b = deFabrica(t.plusSeconds(1));
        Dispositivo c = deFabrica(t.plusSeconds(2));
        a.reclamar(una.id(), t);
        b.reclamar(una.id(), t);
        c.reclamar(otra.id(), t);
        Dispositivo libre = deFabrica(t.plusSeconds(3));
        for (Dispositivo d : new Dispositivo[] {c, b, a, libre}) {
            dispositivos.guardar(d);
        }
        sincronizar();

        assertThat(dispositivos.porPlanta(una.id())).extracting(Dispositivo::serial)
                .containsExactly(a.serial(), b.serial());   // el mas antiguo primero
        assertThat(dispositivos.porPlanta(otra.id())).extracting(Dispositivo::serial).containsExactly(c.serial());
        assertThat(dispositivos.porPlanta(UUID.randomUUID())).isEmpty();
        assertThat(dispositivos.todos()).extracting(Dispositivo::serial)
                .containsSubsequence(a.serial(), b.serial(), c.serial(), libre.serial());
    }

    @Test
    void laClaveForaneaImpideReclamarParaUnaPlantaQueNoExiste() {
        Dispositivo d = deFabrica(ahora());
        d.reclamar(UUID.randomUUID(), ahora());

        assertThatThrownBy(() -> dispositivos.guardar(d)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
