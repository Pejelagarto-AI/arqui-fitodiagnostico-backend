package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.ConsultarEstadoPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.ConsultarHistorialPort;
import com.vivero.fitodiagnostico.dominio.modelo.DiagnosticoDePlanta;
import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioDiagnosticos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioPlantas;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Las consultas del diagnóstico de una planta (RF-F4, RF-F5). Está separado de
 * {@link ServicioMediciones} para que la D3 ("consultar nunca escribe") sea
 * visible en el código y no solo una promesa: este servicio no recibe el
 * evaluador, ni el detector, ni el notificador, así que no tiene con qué
 * producir un diagnóstico, y todos sus métodos son {@code readOnly}. Lo pueden
 * usar dueño e invitados (RN3).
 */
@Service
public class ServicioConsultaDeDiagnosticos implements ConsultarEstadoPort, ConsultarHistorialPort {

    private final RepositorioDiagnosticos diagnosticos;
    private final AutorizadorDePlantas autorizador;

    public ServicioConsultaDeDiagnosticos(RepositorioPlantas plantas, RepositorioAccesos accesos,
                                          RepositorioDiagnosticos diagnosticos, PoliticaDeAcceso politica) {
        this.diagnosticos = diagnosticos;
        this.autorizador = new AutorizadorDePlantas(plantas, accesos, politica);
    }

    /** RF-F4: el último guardado, o vacío si la planta nunca se midió (RN9: no es un error). */
    @Transactional(readOnly = true)
    @Override
    public Optional<DiagnosticoDePlanta> vigente(UUID solicitante, UUID plantaId) {
        autorizador.paraLeer(solicitante, plantaId);
        return diagnosticos.ultimoDe(plantaId);
    }

    /** RF-F5: paginado desde el principio (DA6). */
    @Transactional(readOnly = true)
    @Override
    public Pagina<DiagnosticoDePlanta> historial(UUID solicitante, UUID plantaId, Paginacion paginacion) {
        autorizador.paraLeer(solicitante, plantaId);
        return diagnosticos.historialDe(plantaId, paginacion);
    }
}
