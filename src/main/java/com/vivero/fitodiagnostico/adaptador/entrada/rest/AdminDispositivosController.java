package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaDispositivoAdmin;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaDispositivoDeFabrica;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.SolicitudDispositivoDeFabrica;
import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;
import com.vivero.fitodiagnostico.dominio.puerto.entrada.RegistrarDispositivoDeFabricaPort;
import com.vivero.fitodiagnostico.salud.ConsultarSaludPort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * La "fábrica" como endpoint de administrador: da de alta un dispositivo y entrega su serial y su secreto
 * (el sticker) una sola vez. Todo {@code /api/v1/admin/**} exige el rol en {@link ConfiguracionSeguridad};
 * este controlador no lo repite.
 */
@RestController
@RequestMapping("/api/v1/admin/dispositivos")
public class AdminDispositivosController {

    private final RegistrarDispositivoDeFabricaPort fabrica;
    private final ConsultarSaludPort salud;

    public AdminDispositivosController(RegistrarDispositivoDeFabricaPort fabrica, ConsultarSaludPort salud) {
        this.fabrica = fabrica;
        this.salud = salud;
    }

    /** 201 con serial y secreto. Cuerpo opcional: {@code {"serial":"fito-AAAAAA"}}. 409 si el serial está tomado. */
    @PostMapping
    public ResponseEntity<RespuestaDispositivoDeFabrica> registrar(
            Authentication autenticacion, @Valid @RequestBody(required = false) SolicitudDispositivoDeFabrica solicitud) {
        Optional<String> serial = Optional.ofNullable(solicitud).map(SolicitudDispositivoDeFabrica::serial);
        return ResponseEntity.status(HttpStatus.CREATED).body(RespuestaDispositivoDeFabrica.desde(
                fabrica.registrar(IdentidadDelSolicitante.id(autenticacion), serial)));
    }

    @GetMapping
    public List<RespuestaDispositivoAdmin> listar(Authentication autenticacion) {
        List<Dispositivo> lista = fabrica.listar(IdentidadDelSolicitante.id(autenticacion));
        Map<String, ConsultarSaludPort.Vista> vistas = salud.deLosDispositivos(
                lista.stream().map(Dispositivo::serial).toList());
        return lista.stream().map(d -> RespuestaDispositivoAdmin.desde(d, vistas.get(d.serial()))).toList();
    }
}
