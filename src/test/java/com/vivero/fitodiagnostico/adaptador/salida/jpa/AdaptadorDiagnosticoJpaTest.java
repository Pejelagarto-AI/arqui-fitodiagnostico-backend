package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El adaptador del historial contra H2 con Flyway real (V7): el esquema de tablas hijas (D39). */
class AdaptadorDiagnosticoJpaTest extends BaseDePersistenciaDePlantas {

    private Especie especie;
    private Planta planta;

    @BeforeEach
    void preparar() {
        Usuario dueno = usuario("dueno");
        especie = especie();
        planta = planta(dueno, especie);
    }

    private DiagnosticoDePlanta guardar(Planta de, Diagnostico diagnostico, OrigenDeMedicion origen) {
        DiagnosticoDePlanta registro = DiagnosticoDePlanta.deUnaEvaluacion(de.id(), origen, diagnostico);
        diagnosticos.guardar(registro);
        return registro;
    }

    private Diagnostico evaluarEn(Instant momento, com.vivero.fitodiagnostico.dominio.modelo.Medicion medicion) {
        Diagnostico d = evaluar(especie, medicion);
        return new Diagnostico(d.especie(), d.medicion(), d.estado(), d.parametros(), d.recomendaciones(), momento);
    }

    @Test
    void guardarYLeerConservaTodoElDiagnosticoIncluidosParametrosYRecomendaciones() {
        Diagnostico diagnostico = evaluar(especie, Muestras.medicionCritica());
        assertThat(diagnostico.recomendaciones()).isNotEmpty();   // la prueba no vale si no hay qué comparar
        DiagnosticoDePlanta registro = guardar(planta, diagnostico, OrigenDeMedicion.MANUAL);
        sincronizar();

        DiagnosticoDePlanta leido = diagnosticos.ultimoDe(planta.id()).orElseThrow();

        assertThat(leido).isEqualTo(registro);
        assertThat(leido.diagnostico().parametros()).extracting(p -> p.magnitud())
                .containsExactly(Magnitud.HUMEDAD, Magnitud.LUZ, Magnitud.TEMPERATURA);   // orden del enum
        assertThat(leido.diagnostico().estado()).isEqualTo(EstadoGlobal.CRITICO);
    }

    @Test
    void elOrigenSePersisteYSeDistingueEnElHistorial() {
        Instant base = ahora();
        guardar(planta, evaluarEn(base, Muestras.medicionSaludable()), OrigenDeMedicion.MANUAL);
        guardar(planta, evaluarEn(base.plusSeconds(1), Muestras.medicionSaludable()), OrigenDeMedicion.DISPOSITIVO);
        sincronizar();

        assertThat(diagnosticos.historialDe(planta.id(), Paginacion.primera()).elementos())
                .extracting(DiagnosticoDePlanta::origen)
                .containsExactly(OrigenDeMedicion.DISPOSITIVO, OrigenDeMedicion.MANUAL);
    }

    @Test
    void ultimoDeEsElMasRecienteYVacioSiLaPlantaNuncaSeMidio() {
        Instant base = ahora();
        guardar(planta, evaluarEn(base.minusSeconds(30), Muestras.medicionSaludable()), OrigenDeMedicion.MANUAL);
        DiagnosticoDePlanta ultimo = guardar(planta, evaluarEn(base, Muestras.medicionEnRiesgo()), OrigenDeMedicion.MANUAL);
        guardar(planta, evaluarEn(base.minusSeconds(10), Muestras.medicionCritica()), OrigenDeMedicion.MANUAL);
        sincronizar();

        assertThat(diagnosticos.ultimoDe(planta.id()).orElseThrow().id()).isEqualTo(ultimo.id());
        assertThat(diagnosticos.ultimoDe(UUID.randomUUID())).isEmpty();
    }

    @Test
    void elHistorialSePaginaDelMasRecienteAlMasAntiguoYCuentaElTotal() {
        Instant base = ahora();
        List<DiagnosticoDePlanta> masRecientePrimero = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            masRecientePrimero.add(0, guardar(planta, evaluarEn(base.plusSeconds(i), Muestras.medicionSaludable()),
                    OrigenDeMedicion.MANUAL));
        }
        sincronizar();

        Pagina<DiagnosticoDePlanta> primera = diagnosticos.historialDe(planta.id(), new Paginacion(0, 2));
        Pagina<DiagnosticoDePlanta> ultima = diagnosticos.historialDe(planta.id(), new Paginacion(2, 2));
        Pagina<DiagnosticoDePlanta> fuera = diagnosticos.historialDe(planta.id(), new Paginacion(7, 2));

        assertThat(primera.elementos()).extracting(DiagnosticoDePlanta::id)
                .containsExactly(masRecientePrimero.get(0).id(), masRecientePrimero.get(1).id());
        assertThat(ultima.elementos()).extracting(DiagnosticoDePlanta::id).containsExactly(masRecientePrimero.get(4).id());
        assertThat(primera.total()).isEqualTo(5);
        assertThat(primera.totalDePaginas()).isEqualTo(3);
        assertThat(fuera.elementos()).isEmpty();
        assertThat(fuera.total()).isEqualTo(5);
    }

    @Test
    void elHistorialEsDeUnaSolaPlanta() {
        Planta otra = planta(usuario("otro"), especie);
        guardar(planta, evaluarEn(ahora(), Muestras.medicionSaludable()), OrigenDeMedicion.MANUAL);
        guardar(otra, evaluarEn(ahora(), Muestras.medicionCritica()), OrigenDeMedicion.MANUAL);
        sincronizar();

        Pagina<DiagnosticoDePlanta> pagina = diagnosticos.historialDe(planta.id(), Paginacion.primera());

        assertThat(pagina.total()).isEqualTo(1);
        assertThat(pagina.elementos().get(0).estado()).isEqualTo(EstadoGlobal.SALUDABLE);
    }

    @Test
    void unDiagnosticoViejoNoCambiaAunqueSeEditeLaEspecieOLaPlantaCambieDeEspecie() {
        // El diagnóstico se reconstruye desde su propia fila (parámetros con el rango usado), no del catálogo.
        DiagnosticoDePlanta registro = guardar(planta, evaluar(especie, Muestras.medicionEnRiesgo()), OrigenDeMedicion.MANUAL);
        Map<Magnitud, Rango> otros = Muestras.rangos();
        otros.put(Magnitud.HUMEDAD, new Rango(1.0, 2.0));
        especies.guardar(especie.conRangos(otros));
        Especie otra = especie();
        plantas.guardar(planta.conEspecie(otra));
        sincronizar();

        DiagnosticoDePlanta leido = diagnosticos.ultimoDe(planta.id()).orElseThrow();

        assertThat(leido).isEqualTo(registro);
        assertThat(leido.diagnostico().especie().nombre()).isEqualTo(especie.nombre());
        assertThat(leido.diagnostico().parametros().get(0).rangoOptimo()).isEqualTo(new Rango(30.0, 60.0));
    }

    @Test
    void eliminarDeBorraElHistorialYSusTablasHijasSinTocarElDeOtraPlanta() {
        Planta otra = planta(usuario("otro"), especie);
        DiagnosticoDePlanta aBorrar = guardar(planta, evaluar(especie, Muestras.medicionCritica()), OrigenDeMedicion.MANUAL);
        guardar(planta, evaluarEn(ahora().plusSeconds(1), Muestras.medicionSaludable()), OrigenDeMedicion.MANUAL);
        DiagnosticoDePlanta queSeQueda = guardar(otra, evaluar(especie, Muestras.medicionCritica()), OrigenDeMedicion.MANUAL);
        sincronizar();

        diagnosticos.eliminarDe(planta.id());
        sincronizar();

        assertThat(diagnosticos.ultimoDe(planta.id())).isEmpty();
        assertThat(diagnosticos.ultimoDe(otra.id()).orElseThrow().id()).isEqualTo(queSeQueda.id());
        assertThat(jdbc.queryForObject("select count(*) from diagnostico_parametro where diagnostico_id = ?",
                Integer.class, aBorrar.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from diagnostico_recomendacion where diagnostico_id = ?",
                Integer.class, aBorrar.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from diagnostico_parametro where diagnostico_id = ?",
                Integer.class, queSeQueda.id())).isEqualTo(3);
    }

    @Test
    void despuesDeEliminarElHistorialLaPlantaYaSePuedeEliminar() {
        guardar(planta, evaluar(especie, Muestras.medicionSaludable()), OrigenDeMedicion.MANUAL);
        sincronizar();

        diagnosticos.eliminarDe(planta.id());
        plantas.eliminar(planta.id());
        sincronizar();

        assertThat(plantas.buscar(planta.id())).isEmpty();
    }

    @Test
    void laBaseGuardaUnaFilaPorParametroConSuRangoYUnaPorRecomendacionEnOrden() {
        DiagnosticoDePlanta registro = guardar(planta, evaluar(especie, Muestras.medicionCritica()), OrigenDeMedicion.MANUAL);
        sincronizar();

        assertThat(jdbc.queryForObject("select estado from diagnostico_planta where id = ?", String.class, registro.id()))
                .isEqualTo("CRITICO");
        assertThat(jdbc.queryForList("select magnitud from diagnostico_parametro where diagnostico_id = ? order by magnitud",
                String.class, registro.id())).containsExactly("HUMEDAD", "LUZ", "TEMPERATURA");
        assertThat(jdbc.queryForObject("select rango_minimo from diagnostico_parametro where diagnostico_id = ? and magnitud = 'HUMEDAD'",
                Double.class, registro.id())).isEqualTo(30.0);
        assertThat(jdbc.queryForList("select texto from diagnostico_recomendacion where diagnostico_id = ? order by orden",
                String.class, registro.id())).isEqualTo(registro.diagnostico().recomendaciones());
    }
}
