package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioConsultaDeDiagnosticos;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.web.dto.RespuestaDiagnosticoDePlanta;
import com.vivero.fitodiagnostico.web.dto.RespuestaDiagnosticoVigente;
import com.vivero.fitodiagnostico.web.dto.RespuestaPagina;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Las dos consultas del diagnóstico de una planta (RF-F4, RF-F5). Solo
 * {@code GET}: consultar nunca escribe (D3). Dueño e invitados pueden usarlas.
 * Esta ruta NO es la del diagnóstico suelto ({@code POST /api/v1/diagnosticos},
 * público y sin cambios, D6): aquí se exige sesión y permiso sobre la planta.
 */
@RestController
@RequestMapping("/api/v1/plantas/{plantaId}")
public class DiagnosticosDePlantaController {

    private final ServicioConsultaDeDiagnosticos servicio;

    public DiagnosticosDePlantaController(ServicioConsultaDeDiagnosticos servicio) {
        this.servicio = servicio;
    }

    /** RF-F4: el vigente. Sin diagnósticos responde 200 con {@code hayDiagnostico=false} (RN9). */
    @GetMapping("/diagnostico")
    public RespuestaDiagnosticoVigente vigente(Authentication autenticacion, @PathVariable UUID plantaId) {
        return RespuestaDiagnosticoVigente.desde(
                servicio.vigente(IdentidadDelSolicitante.id(autenticacion), plantaId));
    }

    /**
     * RF-F5: historial del más reciente al más antiguo. {@code ?pagina=} desde 0
     * y {@code ?tamano=} de 1 a 100 (por defecto 20); fuera de eso, 400.
     */
    @GetMapping("/diagnosticos")
    public RespuestaPagina<RespuestaDiagnosticoDePlanta> historial(
            Authentication autenticacion, @PathVariable UUID plantaId,
            @RequestParam(name = "pagina", defaultValue = "0") int pagina,
            @RequestParam(name = "tamano", defaultValue = "" + Paginacion.TAMANO_POR_DEFECTO) int tamano) {
        return RespuestaPagina.desde(
                servicio.historial(IdentidadDelSolicitante.id(autenticacion), plantaId, new Paginacion(pagina, tamano)),
                RespuestaDiagnosticoDePlanta::desde);
    }
}
