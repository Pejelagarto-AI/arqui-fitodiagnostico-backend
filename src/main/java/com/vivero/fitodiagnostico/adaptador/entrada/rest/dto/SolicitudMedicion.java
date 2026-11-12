package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import jakarta.validation.constraints.NotNull;

/**
 * Cuerpo de registrar una medición manual (RF-F1). Igual que el del
 * diagnóstico suelto pero SIN especie: la especie es la de la planta. Bean
 * Validation cubre ausencia; lo físicamente imposible lo valida {@link Lectura} (RA6).
 */
public record SolicitudMedicion(

        @NotNull
        Double humedad,

        @NotNull
        Double luz,

        @NotNull
        Double temperatura) {

    /** Conversión a dominio en el borde (RA6), en el orden humedad, luz, temperatura del contrato. */
    public Medicion aMedicion() {
        return Medicion.de(
                new Lectura(Magnitud.HUMEDAD, humedad),
                new Lectura(Magnitud.LUZ, luz),
                new Lectura(Magnitud.TEMPERATURA, temperatura));
    }
}
