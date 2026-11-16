package com.vivero.fitodiagnostico.dominio.puerto.salida;

import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Muestra;
import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.dominio.modelo.SerieTemporal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia del historial de diagnósticos. Es de solo agregar:
 * no hay "actualizar" un diagnóstico, el historial refleja la vida de la
 * planta (D3). La única forma de quitar registros es eliminar la planta entera.
 */
public interface RepositorioDiagnosticos {

    /** El más reciente de la planta, o vacío si nunca se midió (RN9, RF-F4). */
    Optional<DiagnosticoDePlanta> ultimoDe(UUID plantaId);

    /** El historial, del más reciente al más antiguo (RF-F5). */
    Pagina<DiagnosticoDePlanta> historialDe(UUID plantaId, Paginacion paginacion);

    /**
     * Los valores de una magnitud tomados en {@code desde <= tomadaEn < hasta} (fase 8, D127).
     * Es una lectura más del mismo historial, no un dato nuevo: esta implementación por defecto
     * la arma recorriendo {@link #historialDe} página por página, así que todo adaptador la tiene
     * sin escribir nada. El adaptador JPA la reemplaza por una sola consulta, porque una matera
     * reporta cada 5 s (unas 17 000 lecturas al día) y cargar diagnósticos completos sería caro.
     */
    default SerieTemporal serieDe(UUID plantaId, Magnitud magnitud, Instant desde, Instant hasta) {
        List<Muestra> muestras = new ArrayList<>();
        for (int numero = 0; ; numero++) {
            Pagina<DiagnosticoDePlanta> pagina = historialDe(plantaId, new Paginacion(numero, Paginacion.TAMANO_MAXIMO));
            for (DiagnosticoDePlanta registro : pagina.elementos()) {
                Instant tomadaEn = registro.tomadaEn();
                if (!tomadaEn.isBefore(desde) && tomadaEn.isBefore(hasta)
                        && registro.diagnostico().medicion().contiene(magnitud)) {
                    muestras.add(new Muestra(tomadaEn, registro.diagnostico().medicion().valor(magnitud)));
                }
            }
            List<DiagnosticoDePlanta> elementos = pagina.elementos();
            // El historial viene del más reciente al más antiguo por registradoEn; una lectura se
            // registra después de tomarse, así que pasado "desde" no queda nada que sirva.
            if (elementos.isEmpty() || numero + 1 >= pagina.totalDePaginas()
                    || elementos.get(elementos.size() - 1).registradoEn().isBefore(desde)) {
                return SerieTemporal.de(muestras);
            }
        }
    }

    void guardar(DiagnosticoDePlanta diagnostico);

    /** Elimina todo el historial de la planta (parte de eliminarla, RN12). */
    void eliminarDe(UUID plantaId);
}
