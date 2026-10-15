package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.puerto.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.CatalogoEditable;
import com.vivero.fitodiagnostico.dominio.puerto.RangosPorEspecie;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Doble en memoria de los tres puertos del catálogo. Sin base, sin archivo y
 * sin Spring: es lo que demuestra que los puertos son sustituibles (RA-05,
 * D14) y lo que usan las pruebas de aplicación. Pasa el mismo contrato que el
 * adaptador JPA ({@code ContratoFuenteDeEspeciesTest}), así que no es un
 * simulacro que se desvíe del comportamiento real.
 */
public class EspeciesEnMemoria implements RangosPorEspecie, CatalogoDeEspecies, CatalogoEditable {

    private final Map<String, Especie> porNombre = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public EspeciesEnMemoria(Especie... iniciales) {
        for (Especie especie : iniciales) {
            guardar(especie);
        }
    }

    @Override
    public Optional<Especie> buscar(String nombre) {
        return nombre == null ? Optional.empty() : Optional.ofNullable(porNombre.get(nombre));
    }

    @Override
    public List<Especie> listar() {
        return List.copyOf(porNombre.values());
    }

    @Override
    public void guardar(Especie especie) {
        porNombre.put(especie.nombre(), especie);
    }

    @Override
    public void cambiarActivacion(String nombre, boolean activa) {
        Especie actual = porNombre.get(nombre);
        if (actual != null) {
            porNombre.put(actual.nombre(), activa ? actual.reactivada() : actual.desactivada());
        }
    }
}
