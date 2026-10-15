package com.vivero.fitodiagnostico.web.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.web.dto.SolicitudRangos.RangoSolicitud;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/** Cuerpo de registrar una especie (RF-C3): nombre más los rangos de {@link SolicitudRangos}. */
public record SolicitudEspecie(

        @NotBlank @Size(max = 120)
        String nombre,

        @NotNull
        Map<String, @NotNull @Valid RangoSolicitud> rangos) {

    public Map<Magnitud, Rango> rangosDeDominio() {
        return new SolicitudRangos(rangos).rangosDeDominio();
    }
}
