package com.vivero.fitodiagnostico.infraestructura;

import com.vivero.fitodiagnostico.dobles.EspeciesEnMemoria;
import com.vivero.fitodiagnostico.dominio.puerto.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.CatalogoEditable;
import com.vivero.fitodiagnostico.dominio.puerto.RangosPorEspecie;

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
