package com.vivero.fitodiagnostico.adaptador.entrada.rest;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.ConsultarEspeciesPort;
import com.vivero.fitodiagnostico.adaptador.entrada.rest.dto.RespuestaEspecie;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/especies")
public class EspeciesController {

    private final ConsultarEspeciesPort especies;

    public EspeciesController(ConsultarEspeciesPort especies) {
        this.especies = especies;
    }

    @GetMapping
    public ResponseEntity<List<RespuestaEspecie>> listar() {
        List<RespuestaEspecie> respuesta = especies.listar().stream()
                .map(RespuestaEspecie::desde)
                .toList();

        // Content-Type explícito (RA1): sin esto, un cliente con
        // "Accept: text/html" (p. ej. un navegador) recibe 406 en vez de la
        // lista en JSON, porque la negociación de contenido no encuentra un
        // conversor compatible con lo que pidió.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic())
                .contentType(MediaType.APPLICATION_JSON)
                .body(respuesta);
    }
}
