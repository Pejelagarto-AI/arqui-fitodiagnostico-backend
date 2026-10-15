package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El adaptador de plantas contra H2 con Flyway real (V6). */
class AdaptadorPlantaJpaTest extends BaseDePersistenciaDePlantas {

    @Test
    void guardarYBuscarDevuelveLaMismaPlantaConSuEspecie() {
        Usuario ana = usuario("ana");
        Especie especie = especie();
        Planta planta = planta(ana, especie, "Pepa", ahora());
        sincronizar();

        Planta leida = plantas.buscar(planta.id()).orElseThrow();

        assertThat(leida).isEqualTo(planta);
        assertThat(leida.especie().rangos()).isEqualTo(especie.rangos());
        assertThat(plantas.buscar(UUID.randomUUID())).isEmpty();
    }

    @Test
    void guardarDeNuevoActualizaApodoYEspecieSinDuplicar() {
        Usuario ana = usuario("ana");
        Especie primera = especie();
        Especie segunda = especie();
        Planta planta = planta(ana, primera);

        plantas.guardar(planta.conApodo("Pepita").conEspecie(segunda));
        sincronizar();

        Planta leida = plantas.buscar(planta.id()).orElseThrow();
        assertThat(leida.apodo()).isEqualTo("Pepita");
        assertThat(leida.especie().nombre()).isEqualTo(segunda.nombre());
        assertThat(jdbc.queryForObject("select count(*) from planta where id = ?", Integer.class, planta.id())).isEqualTo(1);
    }

    @Test
    void deDuenoDevuelveSoloLasSuyasLaMasRecientePrimero() {
        Usuario ana = usuario("ana");
        Usuario beto = usuario("beto");
        Especie especie = especie();
        Instant base = ahora();
        Planta vieja = planta(ana, especie, "Vieja", base.minusSeconds(60));
        Planta nueva = planta(ana, especie, "Nueva", base);
        planta(beto, especie, "De Beto", base);
        sincronizar();

        assertThat(plantas.deDueno(ana.id())).containsExactly(nueva, vieja);
        assertThat(plantas.deDueno(UUID.randomUUID())).isEmpty();   // RN7: 0..N
    }

    @Test
    void compartidasConDevuelveLasPlantasAjenasAlasQueFueInvitado() {
        Usuario ana = usuario("ana");
        Usuario beto = usuario("beto");
        Especie especie = especie();
        Planta deAna = planta(ana, especie, "De Ana", ahora());
        Planta otraDeAna = planta(ana, especie, "No compartida", ahora());
        accesos.otorgar(AccesoCompartido.otorgadoAhora(deAna.id(), beto.id()));
        sincronizar();

        assertThat(plantas.compartidasCon(beto.id())).containsExactly(deAna);
        assertThat(plantas.compartidasCon(ana.id())).isEmpty();   // ser dueño no es ser invitado
        assertThat(plantas.deDueno(beto.id())).isEmpty();
        assertThat(otraDeAna).isNotNull();
    }

    @Test
    void laPlantaLlevaLosRangosVigentesDeSuEspecieAunqueSeEditeDespues() {
        // RF-C4: el administrador edita la especie; las plantas que la usan ven el cambio.
        Usuario ana = usuario("ana");
        Especie especie = especie();
        Planta planta = planta(ana, especie);
        Map<Magnitud, Rango> nuevos = Muestras.rangos();
        nuevos.put(Magnitud.HUMEDAD, new Rango(10.0, 20.0));
        especies.guardar(especie.conRangos(nuevos));
        sincronizar();

        assertThat(plantas.buscar(planta.id()).orElseThrow().especie().rango(Magnitud.HUMEDAD))
                .isEqualTo(new Rango(10.0, 20.0));
    }

    @Test
    void unaPlantaConEspecieDescontinuadaSeSigueLeyendoYGuardando() {
        // RN11 en la base: la clave foránea no impide desactivar y la planta sigue funcionando.
        Usuario ana = usuario("ana");
        Especie especie = especie();
        Planta planta = planta(ana, especie);
        especies.cambiarActivacion(especie.nombre(), false);
        sincronizar();

        Planta leida = plantas.buscar(planta.id()).orElseThrow();
        assertThat(leida.especie().activa()).isFalse();

        plantas.guardar(leida.conApodo("Sigue viva"));
        sincronizar();
        assertThat(plantas.buscar(planta.id()).orElseThrow().apodo()).isEqualTo("Sigue viva");
    }

    @Test
    void guardarConUnaEspecieQueNoExisteEsUnErrorDelLlamadorNoUnaFilaHuerfana() {
        Usuario ana = usuario("ana");
        Planta planta = new Planta(UUID.randomUUID(), ana.id(), "Pepa", Muestras.especie("jpa-no-existe"), ahora());

        assertThatThrownBy(() -> plantas.guardar(planta)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void eliminarBorraLaPlanta() {
        Usuario ana = usuario("ana");
        Planta planta = planta(ana, especie());

        plantas.eliminar(planta.id());
        sincronizar();

        assertThat(plantas.buscar(planta.id())).isEmpty();
        plantas.eliminar(UUID.randomUUID());   // inexistente: no falla
    }

    @Test
    void laBaseNoDejaEliminarUnaPlantaConAccesosSinPasarPorLaCascada() {
        // Las claves foráneas sin ON DELETE CASCADE son la red de seguridad de CascadaDeEliminacion (V6).
        Usuario ana = usuario("ana");
        Usuario beto = usuario("beto");
        Planta conAcceso = planta(ana, especie());
        accesos.otorgar(AccesoCompartido.otorgadoAhora(conAcceso.id(), beto.id()));

        assertThatThrownBy(() -> plantas.eliminar(conAcceso.id())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laBaseNoDejaEliminarUnaPlantaConHistorialSinPasarPorLaCascada() {
        // Ídem para el historial (V7).
        Usuario ana = usuario("ana");
        Especie especie = especie();
        Planta conHistorial = planta(ana, especie);
        diagnosticos.guardar(DiagnosticoDePlanta.deUnaEvaluacion(conHistorial.id(), OrigenDeMedicion.MANUAL,
                evaluar(especie, Muestras.medicionSaludable())));

        assertThatThrownBy(() -> plantas.eliminar(conHistorial.id())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void laBaseNoDejaEliminarUnUsuarioQueTodaviaTienePlantas() {
        Usuario ana = usuario("ana");
        planta(ana, especie());

        assertThatThrownBy(() -> {
            usuarios.eliminar(ana.id());
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);   // flush directo: sin la traducción de Spring Data
    }
}
