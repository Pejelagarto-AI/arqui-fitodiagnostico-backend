package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioDiagnosticos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble en memoria de {@link RepositorioDiagnosticos}. Cuenta las escrituras
 * para poder afirmar que una consulta no escribió nada (D3).
 */
public class DiagnosticosEnMemoria implements RepositorioDiagnosticos {

    private final List<DiagnosticoDePlanta> guardados = new ArrayList<>();
    private int escrituras;

    @Override
    public Optional<DiagnosticoDePlanta> ultimoDe(UUID plantaId) {
        return deLaPlanta(plantaId).stream().findFirst();
    }

    @Override
    public Pagina<DiagnosticoDePlanta> historialDe(UUID plantaId, Paginacion paginacion) {
        List<DiagnosticoDePlanta> todos = deLaPlanta(plantaId);
        int desde = (int) Math.min(paginacion.desplazamiento(), todos.size());
        int hasta = Math.min(desde + paginacion.tamano(), todos.size());
        return new Pagina<>(todos.subList(desde, hasta), paginacion.pagina(), paginacion.tamano(), todos.size());
    }

    @Override
    public void guardar(DiagnosticoDePlanta diagnostico) {
        escrituras++;
        guardados.add(diagnostico);
    }

    @Override
    public void eliminarDe(UUID plantaId) {
        guardados.removeIf(d -> d.plantaId().equals(plantaId));
    }

    public int escrituras() {
        return escrituras;
    }

    public int cuantos() {
        return guardados.size();
    }

    private List<DiagnosticoDePlanta> deLaPlanta(UUID plantaId) {
        // Más reciente primero; a igual instante, el guardado después (como el índice del adaptador real).
        List<DiagnosticoDePlanta> deLaPlanta = new ArrayList<>(
                guardados.stream().filter(d -> d.plantaId().equals(plantaId)).toList());
        deLaPlanta.sort(Comparator.comparing(DiagnosticoDePlanta::registradoEn).reversed());
        return deLaPlanta;
    }
}
