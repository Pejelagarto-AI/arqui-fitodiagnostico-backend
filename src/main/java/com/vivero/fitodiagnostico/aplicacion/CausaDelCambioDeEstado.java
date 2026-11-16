package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.ResultadoParametro;

import java.util.EnumMap;
import java.util.Map;

/**
 * Por qué cambió el estado global (fase 12, D171). Solo distingue un caso: el cambio se debe únicamente a que entró o
 * salió la noche. Es lectura de dos diagnósticos que ya existen, no una regla de agronomía: no vive en el dominio.
 *
 * <p>Es por la noche si el periodo cambió y ningún parámetro que no sea la luz cambió de clasificación: lo único que
 * pudo mover el estado es que la luz dejó de contar (o volvió a contar).
 */
final class CausaDelCambioDeEstado {

    private CausaDelCambioDeEstado() { }

    static boolean soloPorElPeriodo(Diagnostico anterior, Diagnostico nuevo) {
        return anterior.periodo() != nuevo.periodo()
                && sinLuz(anterior).equals(sinLuz(nuevo));
    }

    private static Map<Magnitud, Clasificacion> sinLuz(Diagnostico diagnostico) {
        Map<Magnitud, Clasificacion> clasificaciones = new EnumMap<>(Magnitud.class);
        for (ResultadoParametro p : diagnostico.parametros()) {
            if (p.magnitud() != Magnitud.LUZ) {
                clasificaciones.put(p.magnitud(), p.clasificacion());
            }
        }
        return clasificaciones;
    }
}
