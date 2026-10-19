package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.AdministrarEspeciesPort;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.ConsultarCatalogoCompletoPort;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaEspecieAdmin;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudEspecie;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudRangos;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * El catálogo de especies visto por un administrador (RF-C2 a RF-C6). No hay
 * {@code DELETE}: las especies se desactivan, nunca se eliminan (D10), y exponer
 * un verbo de borrado en la API habría prometido lo contrario. Desactivar y
 * reactivar son acciones ({@code POST .../desactivar}) y no un
 * {@code PUT activa=false} para que se lean como lo que son en el registro de
 * peticiones.
 */
@RestController
@RequestMapping("/api/v1/admin/especies")
public class AdministracionEspeciesController {

    private final ConsultarCatalogoCompletoPort catalogo;
    private final AdministrarEspeciesPort administracion;

    public AdministracionEspeciesController(ConsultarCatalogoCompletoPort catalogo, AdministrarEspeciesPort administracion) {
        this.catalogo = catalogo;
        this.administracion = administracion;
    }

    /** RF-C2: todas, incluidas las descontinuadas. */
    @GetMapping
    public List<RespuestaEspecieAdmin> listarTodas() {
        return catalogo.listarTodas().stream().map(RespuestaEspecieAdmin::desde).toList();
    }

    /** RF-C3. 409 si el nombre ya existe. */
    @PostMapping
    public ResponseEntity<RespuestaEspecieAdmin> crear(@Valid @RequestBody SolicitudEspecie solicitud) {
        var creada = administracion.crearEspecie(solicitud.nombre(), solicitud.rangosDeDominio());
        return ResponseEntity.status(HttpStatus.CREATED).body(RespuestaEspecieAdmin.desde(creada));
    }

    /** RF-C4: reemplaza los rangos; el nombre de la ruta es la clave y no se renombra. */
    @PutMapping("/{nombre}")
    public RespuestaEspecieAdmin editar(@PathVariable String nombre, @Valid @RequestBody SolicitudRangos solicitud) {
        return RespuestaEspecieAdmin.desde(administracion.editarEspecie(nombre, solicitud.rangosDeDominio()));
    }

    /** RF-C5. */
    @PostMapping("/{nombre}/desactivar")
    public RespuestaEspecieAdmin desactivar(@PathVariable String nombre) {
        return RespuestaEspecieAdmin.desde(administracion.desactivarEspecie(nombre));
    }

    /** RF-C6. */
    @PostMapping("/{nombre}/reactivar")
    public RespuestaEspecieAdmin reactivar(@PathVariable String nombre) {
        return RespuestaEspecieAdmin.desde(administracion.reactivarEspecie(nombre));
    }
}
