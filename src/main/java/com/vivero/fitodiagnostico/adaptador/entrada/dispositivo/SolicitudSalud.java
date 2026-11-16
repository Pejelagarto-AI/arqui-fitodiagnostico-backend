package com.vivero.fitodiagnostico.adaptador.entrada.dispositivo;

import com.vivero.fitodiagnostico.salud.EstadoDeSensor;
import com.vivero.fitodiagnostico.salud.SensorDeMatera;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.EnumMap;
import java.util.Map;

/**
 * Cuerpo de {@code POST /dispositivos/{id}/salud}: el estado de cada sensor ({@code "OK"} o {@code "FALLA"}, las tres
 * claves obligatorias) y un detalle opcional de a lo sumo 200 caracteres. Lo que no es una de esas palabras o esas
 * claves sale como 400 por el manejador global, igual que una lectura imposible.
 */
public record SolicitudSalud(

        @NotNull
        Map<String, String> sensores,

        @Size(max = 200)
        String detalle) {

    public Map<SensorDeMatera, EstadoDeSensor> aSensores() {
        EnumMap<SensorDeMatera, EstadoDeSensor> estados = new EnumMap<>(SensorDeMatera.class);
        for (Map.Entry<String, String> e : sensores.entrySet()) {
            SensorDeMatera sensor = SensorDeMatera.deClave(e.getKey()).orElseThrow(() ->
                    new IllegalArgumentException("sensor desconocido: '" + e.getKey()
                            + "' (use humedad, luz o temperatura)"));
            try {
                estados.put(sensor, EstadoDeSensor.valueOf(String.valueOf(e.getValue())));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("el estado de '" + e.getKey() + "' debe ser OK o FALLA");
            }
        }
        for (SensorDeMatera s : SensorDeMatera.values()) {
            if (!estados.containsKey(s)) {
                throw new IllegalArgumentException("falta el sensor '" + s.clave() + "'");
            }
        }
        return estados;
    }
}
