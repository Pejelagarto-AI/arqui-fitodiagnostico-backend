package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioDiagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.web.dto.RespuestaDiagnostico;
import com.vivero.fitodiagnostico.web.dto.SolicitudDiagnostico;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/diagnosticos")
public class DiagnosticoController {

    private final ServicioDiagnostico servicio;

    public DiagnosticoController(ServicioDiagnostico servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ResponseEntity<RespuestaDiagnostico> diagnosticar(
            @Valid @ModelAttribute SolicitudDiagnostico solicitud) {

        Medicion medicion = Medicion.de(
                new Lectura(Magnitud.TEMPERATURA, solicitud.temperaturaC()),
                new Lectura(Magnitud.HUMEDAD, solicitud.humedad()),
                new Lectura(Magnitud.LUZ, solicitud.luzLux()));

        Diagnostico diagnostico = servicio.diagnosticar(solicitud.especie(), medicion);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic())
                .body(RespuestaDiagnostico.desde(diagnostico));
    }
}
