package com.vivero.fitodiagnostico;

import com.vivero.fitodiagnostico.adaptador.salida.jpa.AdaptadorEspecieJpa;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoEditable;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RangosPorEspecie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Sin tocar la configuración, la fuente es la base de datos: un solo adaptador JPA para los tres puertos. */
@SpringBootTest
@ActiveProfiles("test")
class FuenteBdPorDefectoIntegracionTest {

    @Autowired private RangosPorEspecie rangos;
    @Autowired private CatalogoDeEspecies catalogo;
    @Autowired private CatalogoEditable editable;

    @Test
    void porDefectoLosTresPuertosDelCatalogoLosImplementaElAdaptadorJpa() {
        assertThat(rangos).isInstanceOf(AdaptadorEspecieJpa.class);
        assertThat(catalogo).isInstanceOf(AdaptadorEspecieJpa.class);
        assertThat(editable).isInstanceOf(AdaptadorEspecieJpa.class);
    }
}
