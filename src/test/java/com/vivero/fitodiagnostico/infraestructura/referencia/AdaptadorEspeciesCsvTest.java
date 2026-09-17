package com.vivero.fitodiagnostico.infraestructura.referencia;

import com.vivero.fitodiagnostico.dominio.puerto.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.RangosPorEspecie;
import com.vivero.fitodiagnostico.infraestructura.ContratoFuenteDeEspeciesTest;

/**
 * JUnit puro, sin Spring: {@link AdaptadorEspeciesCsv} no necesita un
 * ApplicationContext para leer el CSV del classpath, así que la prueba
 * tampoco lo necesita.
 */
class AdaptadorEspeciesCsvTest extends ContratoFuenteDeEspeciesTest {

    private final AdaptadorEspeciesCsv adaptador = new AdaptadorEspeciesCsv();

    @Override
    protected RangosPorEspecie rangosPorEspecie() {
        return adaptador;
    }

    @Override
    protected CatalogoDeEspecies catalogoDeEspecies() {
        return adaptador;
    }
}
