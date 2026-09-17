package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioCatalogo;
import com.vivero.fitodiagnostico.web.dto.RespuestaEspecie;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/especies")
public class EspeciesController {

    private final ServicioCatalogo servicio;

    public EspeciesController(ServicioCatalogo servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<List<RespuestaEspecie>> listar() {
        List<RespuestaEspecie> respuesta = servicio.listar().stream()
                .map(RespuestaEspecie::desde)
                .toList();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic())
                .body(respuesta);
    }
}
