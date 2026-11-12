package com.vivero.fitodiagnostico.adaptador.salida;

import com.vivero.fitodiagnostico.dobles.EspeciesEnMemoria;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoEditable;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RangosPorEspecie;

/**
 * El doble en memoria pasa el mismo contrato que el adaptador JPA: evidencia
 * ejecutable de que el puerto es sustituible (RA-05) sin base ni archivo.
 * JUnit puro, sin Spring.
 */
class EspeciesEnMemoriaTest extends ContratoFuenteDeEspeciesTest {

    private final EspeciesEnMemoria doble = new EspeciesEnMemoria();

    @Override
    protected RangosPorEspecie rangosPorEspecie() {
        return doble;
    }

    @Override
    protected CatalogoDeEspecies catalogoDeEspecies() {
        return doble;
    }

    @Override
    protected CatalogoEditable catalogoEditable() {
        return doble;
    }
}
