package com.vivero.fitodiagnostico.web;

import com.vivero.fitodiagnostico.aplicacion.ServicioDiagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.web.dto.RespuestaDiagnostico;
import com.vivero.fitodiagnostico.web.dto.SolicitudDiagnostico;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Único endpoint del contrato del Anexo A (sección 2). POST porque el cuerpo
 * es una medición completa a evaluar, no una búsqueda idempotente por query
 * params. El DTO se valida y se convierte a objetos de dominio EN EL BORDE
 * (RA6): el dominio nunca ve el DTO ni un Map.
 *
 * <p>Las lecturas se construyen en orden humedad, luz, temperatura —el mismo
 * orden del contrato y del enum {@link Magnitud}— para que, si más de una
 * viene fuera del rango físico del sensor, se reporte siempre la primera de
 * forma determinista.
 */
@RestController
@RequestMapping("/api/v1/diagnosticos")
public class DiagnosticoController {

    private final ServicioDiagnostico servicio;

    public DiagnosticoController(ServicioDiagnostico servicio) {
        this.servicio = servicio;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<RespuestaDiagnostico> diagnosticar(@Valid @RequestBody SolicitudDiagnostico solicitud) {

        Medicion medicion = Medicion.de(
                new Lectura(Magnitud.HUMEDAD, solicitud.humedad()),
                new Lectura(Magnitud.LUZ, solicitud.luz()),
                new Lectura(Magnitud.TEMPERATURA, solicitud.temperatura()));

        Diagnostico diagnostico = servicio.diagnosticar(solicitud.especie(), medicion);

        return ResponseEntity.ok(RespuestaDiagnostico.desde(diagnostico));
    }
}
