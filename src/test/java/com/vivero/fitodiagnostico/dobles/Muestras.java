package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.Lectura;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Datos de prueba comunes a las pruebas de plantas: una especie con rangos
 * conocidos y mediciones que, con la regla por desviación (umbral 25 %), dan
 * cada uno de los tres estados.
 */
public final class Muestras {

    private Muestras() { }

    public static Map<Magnitud, Rango> rangos() {
        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        rangos.put(Magnitud.HUMEDAD, new Rango(30.0, 60.0));
        rangos.put(Magnitud.LUZ, new Rango(500.0, 1000.0));
        rangos.put(Magnitud.TEMPERATURA, new Rango(18.0, 26.0));
        return rangos;
    }

    public static Especie especie(String nombre) {
        return new Especie(nombre, rangos());
    }

    public static Especie especieInactiva(String nombre) {
        return especie(nombre).desactivada();
    }

    /** Todo dentro de rango: SALUDABLE. */
    public static Medicion medicionSaludable() {
        return medicion(45, 700, 22);
    }

    /** Humedad 2 puntos bajo el mínimo (7 % del ancho): EN_RIESGO. */
    public static Medicion medicionEnRiesgo() {
        return medicion(28, 700, 22);
    }

    /** Humedad 25 puntos bajo el mínimo (83 % del ancho): CRITICO. */
    public static Medicion medicionCritica() {
        return medicion(5, 700, 22);
    }

    public static Medicion medicion(double humedad, double luz, double temperatura) {
        return Medicion.de(new Lectura(Magnitud.HUMEDAD, humedad), new Lectura(Magnitud.LUZ, luz),
                new Lectura(Magnitud.TEMPERATURA, temperatura));
    }

    /** Un diagnóstico armado a mano con el estado dado, sin pasar por el evaluador. */
    public static Diagnostico diagnosticoCon(EstadoGlobal estado) {
        return new Diagnostico(especie("helecho"), medicionSaludable(), estado, List.of(), List.of(), Instant.now());
    }
}
