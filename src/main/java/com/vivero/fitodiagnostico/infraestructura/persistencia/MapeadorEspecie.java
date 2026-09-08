package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import org.springframework.stereotype.Component;

/**
 * Única clase autorizada a ver los dos modelos a la vez.
 * Convierte {@link EspecieEntity} en {@link Especie} armando los tres {@link Rango}.
 * Ninguna EspecieEntity debe salir del paquete infraestructura.persistencia.
 */
@Component
public class MapeadorEspecie {

    public Especie aDominio(EspecieEntity entidad) {
        return new Especie(
                entidad.getNombreCientifico(),
                entidad.getNombreComun(),
                new Rango(entidad.getTempMin(), entidad.getTempMax()),
                new Rango(entidad.getHumMin(), entidad.getHumMax()),
                new Rango(entidad.getLuzMin(), entidad.getLuzMax()));
    }
}
