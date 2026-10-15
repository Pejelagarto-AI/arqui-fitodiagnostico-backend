package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioAdministracion;
import com.vivero.fitodiagnostico.web.dto.RespuestaDetalleDeUsuario;
import com.vivero.fitodiagnostico.web.dto.RespuestaUsuario;
import com.vivero.fitodiagnostico.web.dto.SolicitudActualizarDatos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Usuarios y roles vistos por un administrador (RF-B1 a RF-B5). Todo
 * {@code /api/v1/admin/**} exige el rol en {@link ConfiguracionSeguridad}; este
 * controlador no repite la comprobación. El rol de administrador es un
 * subrecurso ({@code .../rol-administrador}) y no un campo editable del usuario:
 * otorgarlo y revocarlo tiene una regla propia (RN4) y no debe poder colarse
 * dentro de una edición de nombre y correo.
 */
@RestController
@RequestMapping("/api/v1/admin/usuarios")
public class AdministracionUsuariosController {

    private final ServicioAdministracion servicio;

    public AdministracionUsuariosController(ServicioAdministracion servicio) {
        this.servicio = servicio;
    }

    /** RF-B1: {@code ?q=texto} filtra por nombre o correo. */
    @GetMapping
    public List<RespuestaUsuario> listar(@RequestParam(name = "q", required = false) String texto) {
        return servicio.listarUsuarios(texto).stream().map(RespuestaUsuario::desde).toList();
    }

    /** RF-B2: el usuario y un resumen de sus plantas. */
    @GetMapping("/{id}")
    public RespuestaDetalleDeUsuario detalle(@PathVariable UUID id) {
        return RespuestaDetalleDeUsuario.desde(servicio.detalleDeUsuario(id));
    }

    /** RF-B3. */
    @PutMapping("/{id}")
    public RespuestaUsuario editar(@PathVariable UUID id, @Valid @RequestBody SolicitudActualizarDatos solicitud) {
        return RespuestaUsuario.desde(servicio.editarUsuario(id, solicitud.nombre(), solicitud.correo()));
    }

    /** RF-B4. 409 si es el último administrador (RN4). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@PathVariable UUID id) {
        servicio.eliminarUsuario(id);
    }

    /** RF-B5 (otorgar). Idempotente. */
    @PutMapping("/{id}/rol-administrador")
    public RespuestaUsuario otorgarAdministrador(@PathVariable UUID id) {
        return RespuestaUsuario.desde(servicio.otorgarAdministrador(id));
    }

    /** RF-B5 (revocar). Idempotente; 409 si es el último administrador (RN4). */
    @DeleteMapping("/{id}/rol-administrador")
    public RespuestaUsuario revocarAdministrador(@PathVariable UUID id) {
        return RespuestaUsuario.desde(servicio.revocarAdministrador(id));
    }
}
