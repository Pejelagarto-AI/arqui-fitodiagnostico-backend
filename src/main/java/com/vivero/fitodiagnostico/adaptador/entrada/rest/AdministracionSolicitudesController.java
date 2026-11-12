package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.ResolverSolicitudesDeEspeciePort;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaSolicitudDeEspecie;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudResolucion;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Del administrador: listar las pendientes (RF-H3) y resolverlas (RF-H4, RF-H5).
 * Está bajo {@code /api/v1/admin/**}, que {@link ConfiguracionSeguridad} protege con el rol.
 * Aprobar NO crea la especie (D55): el administrador la da de alta con
 * {@code POST /api/v1/admin/especies} (RF-C3) y sus rangos.
 */
@RestController
@RequestMapping("/api/v1/admin/solicitudes-especie")
public class AdministracionSolicitudesController {

    private final ResolverSolicitudesDeEspeciePort solicitudes;

    public AdministracionSolicitudesController(ResolverSolicitudesDeEspeciePort solicitudes) {
        this.solicitudes = solicitudes;
    }

    /** RF-H3: pendientes, la más antigua primero. */
    @GetMapping
    public List<RespuestaSolicitudDeEspecie> pendientes() {
        return solicitudes.pendientes().stream().map(RespuestaSolicitudDeEspecie::desde).toList();
    }

    /** RF-H4: aprobar; la respuesta es opcional. 409 si ya estaba resuelta. */
    @PostMapping("/{id}/aprobar")
    public RespuestaSolicitudDeEspecie aprobar(@PathVariable UUID id,
                                               @Valid @RequestBody(required = false) SolicitudResolucion cuerpo) {
        return RespuestaSolicitudDeEspecie.desde(solicitudes.aprobar(id, cuerpo == null ? null : cuerpo.respuesta()));
    }

    /** RF-H4: rechazar; la respuesta con el motivo es obligatoria (400 si falta). */
    @PostMapping("/{id}/rechazar")
    public RespuestaSolicitudDeEspecie rechazar(@PathVariable UUID id,
                                                @Valid @RequestBody(required = false) SolicitudResolucion cuerpo) {
        return RespuestaSolicitudDeEspecie.desde(solicitudes.rechazar(id, cuerpo == null ? null : cuerpo.respuesta()));
    }
}
