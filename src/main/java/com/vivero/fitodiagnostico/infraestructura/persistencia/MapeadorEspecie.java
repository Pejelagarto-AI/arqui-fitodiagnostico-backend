package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Única clase autorizada a ver los dos modelos a la vez.
 * Convierte {@link EspecieEntity} en {@link Especie} armando el mapa de {@link Rango} por {@link Magnitud}.
 * Ninguna EspecieEntity debe salir del paquete infraestructura.persistencia.
 * También hace el camino inverso (escritura): vuelca una {@link Especie} sobre
 * una entidad nueva o ya cargada.
 */
@Component
public class MapeadorEspecie {

    public Especie aDominio(EspecieEntity entidad) {
        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        rangos.put(Magnitud.HUMEDAD, new Rango(entidad.getHumedadMin(), entidad.getHumedadMax()));
        rangos.put(Magnitud.LUZ, new Rango(entidad.getLuzMin(), entidad.getLuzMax()));
        rangos.put(Magnitud.TEMPERATURA, new Rango(entidad.getTempMin(), entidad.getTempMax()));
        return new Especie(entidad.getNombre(), rangos, entidad.isActiva());
    }

    /** Entidad vacía para una especie que todavía no está en la base. */
    EspecieEntity nuevaEntidad() {
        return new EspecieEntity();
    }

    /**
     * Copia el estado de la especie a la entidad. El nombre solo se escribe
     * si la entidad es nueva: renombrar una especie existente no es una
     * operación del catálogo (el nombre es su clave de búsqueda).
     */
    void volcarEn(EspecieEntity entidad, Especie especie) {
        if (entidad.getNombre() == null) {
            entidad.setNombre(especie.nombre());
        }
        entidad.setActiva(especie.activa());
        Rango humedad = especie.rango(Magnitud.HUMEDAD);
        Rango luz = especie.rango(Magnitud.LUZ);
        Rango temperatura = especie.rango(Magnitud.TEMPERATURA);
        entidad.setHumedad(humedad.minimo(), humedad.maximo());
        entidad.setLuz(luz.minimo(), luz.maximo());
        entidad.setTemperatura(temperatura.minimo(), temperatura.maximo());
    }
}
