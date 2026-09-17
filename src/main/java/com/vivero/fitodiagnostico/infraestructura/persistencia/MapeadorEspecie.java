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
 */
@Component
public class MapeadorEspecie {

    public Especie aDominio(EspecieEntity entidad) {
        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        rangos.put(Magnitud.TEMPERATURA, new Rango(entidad.getTempMin(), entidad.getTempMax()));
        rangos.put(Magnitud.HUMEDAD, new Rango(entidad.getHumMin(), entidad.getHumMax()));
        rangos.put(Magnitud.LUZ, new Rango(entidad.getLuzMin(), entidad.getLuzMax()));
        return new Especie(
                entidad.getNombreCientifico(),
                entidad.getNombreComun(),
                rangos);
    }
}
