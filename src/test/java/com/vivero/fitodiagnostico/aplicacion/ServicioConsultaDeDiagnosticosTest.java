package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dobles.MundoEnMemoria;
import com.vivero.fitodiagnostico.dominio.excepcion.PlantaNoEncontradaException;
import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServicioConsultaDeDiagnosticosTest {

    private final UUID dueno = UUID.randomUUID();
    private final UUID invitado = UUID.randomUUID();
    private final UUID ajeno = UUID.randomUUID();

    private MundoEnMemoria mundo;
    private ServicioConsultaDeDiagnosticos servicio;
    private Planta planta;

    @BeforeEach
    void preparar() {
        mundo = new MundoEnMemoria();
        servicio = new ServicioConsultaDeDiagnosticos(mundo.plantas, mundo.accesos, mundo.diagnosticos,
                new PoliticaDeAcceso());
        planta = Planta.nueva(dueno, "Pepa", Muestras.especie("helecho"));
        mundo.plantas.guardar(planta);
        mundo.accesos.otorgar(AccesoCompartido.otorgadoAhora(planta.id(), invitado));
    }

    private DiagnosticoDePlanta guardarUnoEn(Instant momento, EstadoGlobal estado) {
        var diagnostico = new com.vivero.fitodiagnostico.dominio.modelo.Diagnostico(Muestras.especie("helecho"),
                Muestras.medicionSaludable(), estado, List.of(), List.of(), momento);
        DiagnosticoDePlanta registro = DiagnosticoDePlanta.deUnaEvaluacion(planta.id(), OrigenDeMedicion.MANUAL, diagnostico);
        mundo.diagnosticos.guardar(registro);
        return registro;
    }

    @Test
    void unaPlantaSinDiagnosticosNoEsUnErrorDevuelveVacio() {
        // RN9.
        assertThat(servicio.vigente(dueno, planta.id())).isEmpty();
        assertThat(servicio.historial(dueno, planta.id(), Paginacion.primera()).elementos()).isEmpty();
    }

    @Test
    void elVigenteEsElMasRecienteSinImportarElOrdenDeInsercion() {
        Instant ahora = Instant.now();
        guardarUnoEn(ahora.minusSeconds(10), EstadoGlobal.SALUDABLE);
        DiagnosticoDePlanta ultimo = guardarUnoEn(ahora, EstadoGlobal.CRITICO);
        guardarUnoEn(ahora.minusSeconds(5), EstadoGlobal.EN_RIESGO);

        assertThat(servicio.vigente(dueno, planta.id())).contains(ultimo);
        assertThat(servicio.vigente(invitado, planta.id())).contains(ultimo);   // el invitado también lee
    }

    @Test
    void consultarNuncaEscribe() {
        // D3.
        guardarUnoEn(Instant.now(), EstadoGlobal.SALUDABLE);
        int escriturasAntes = mundo.diagnosticos.escrituras();

        servicio.vigente(dueno, planta.id());
        servicio.historial(dueno, planta.id(), Paginacion.primera());
        servicio.vigente(invitado, planta.id());

        assertThat(mundo.diagnosticos.escrituras()).isEqualTo(escriturasAntes);
        assertThat(mundo.diagnosticos.cuantos()).isEqualTo(1);
        assertThat(mundo.notificador.avisos()).isEmpty();
    }

    @Test
    void elHistorialVieneDelMasRecienteAlMasAntiguoYPaginado() {
        Instant base = Instant.now();
        List<DiagnosticoDePlanta> ordenados = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ordenados.add(0, guardarUnoEn(base.plusSeconds(i), EstadoGlobal.SALUDABLE));   // el último guardado, primero
        }

        Pagina<DiagnosticoDePlanta> primera = servicio.historial(dueno, planta.id(), new Paginacion(0, 2));
        Pagina<DiagnosticoDePlanta> tercera = servicio.historial(dueno, planta.id(), new Paginacion(2, 2));
        Pagina<DiagnosticoDePlanta> fuera = servicio.historial(dueno, planta.id(), new Paginacion(9, 2));

        assertThat(primera.elementos()).containsExactlyElementsOf(ordenados.subList(0, 2));
        assertThat(primera.total()).isEqualTo(5);
        assertThat(primera.totalDePaginas()).isEqualTo(3);
        assertThat(tercera.elementos()).containsExactly(ordenados.get(4));
        assertThat(fuera.elementos()).isEmpty();
        assertThat(fuera.total()).isEqualTo(5);
    }

    @Test
    void unAjenoNoLeeNiElVigenteNiElHistorialYNoSabeSiLaPlantaExiste() {
        assertThatThrownBy(() -> servicio.vigente(ajeno, planta.id())).isInstanceOf(PlantaNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.historial(ajeno, planta.id(), Paginacion.primera()))
                .isInstanceOf(PlantaNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.vigente(ajeno, UUID.randomUUID())).isInstanceOf(PlantaNoEncontradaException.class);
    }

    @Test
    void elHistorialDeUnaPlantaNoIncluyeElDeOtra() {
        Planta otra = Planta.nueva(dueno, "Otra", Muestras.especie("helecho"));
        mundo.plantas.guardar(otra);
        guardarUnoEn(Instant.now(), EstadoGlobal.SALUDABLE);
        mundo.diagnosticos.guardar(DiagnosticoDePlanta.deUnaEvaluacion(otra.id(), OrigenDeMedicion.MANUAL,
                Muestras.diagnosticoCon(EstadoGlobal.CRITICO)));

        assertThat(servicio.historial(dueno, planta.id(), Paginacion.primera()).total()).isEqualTo(1);
        assertThat(servicio.vigente(dueno, planta.id()).orElseThrow().estado()).isEqualTo(EstadoGlobal.SALUDABLE);
    }
}
