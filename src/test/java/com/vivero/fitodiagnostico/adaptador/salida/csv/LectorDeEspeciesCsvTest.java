package com.vivero.fitodiagnostico.adaptador.salida.csv;

import com.vivero.fitodiagnostico.dobles.EspeciesEnMemoria;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** JUnit puro: el lector del CSV semilla y el sembrador, sin Spring ni base. */
class LectorDeEspeciesCsvTest {

    @Test
    void leeLasEspeciesDelCsvTodasActivas() {
        assertThat(new LectorDeEspeciesCsv().leer())
                .extracting(Especie::nombre)
                .containsExactlyInAnyOrder("girasol", "cactus", "suculenta", "helecho", "atrapamoscas");
        assertThat(new LectorDeEspeciesCsv().leer()).allMatch(Especie::activa);
    }

    @Test
    void elSembradorCargaElCatalogoVacio() {
        EspeciesEnMemoria catalogo = new EspeciesEnMemoria();

        int cargadas = new SembradorDeEspecies(catalogo, catalogo, new LectorDeEspeciesCsv()).sembrarSiVacio();

        assertThat(cargadas).isEqualTo(5);
        assertThat(catalogo.buscar("suculenta").orElseThrow().rango(Magnitud.HUMEDAD).maximo()).isEqualTo(30.0);
    }

    @Test
    void elSembradorNoHaceNadaSiElCatalogoYaTieneDatos() {
        EspeciesEnMemoria catalogo = new EspeciesEnMemoria();
        new SembradorDeEspecies(catalogo, catalogo, new LectorDeEspeciesCsv()).sembrarSiVacio();
        catalogo.cambiarActivacion("helecho", false);

        int cargadas = new SembradorDeEspecies(catalogo, catalogo, new LectorDeEspeciesCsv()).sembrarSiVacio();

        assertThat(cargadas).isZero();
        assertThat(catalogo.buscar("helecho").orElseThrow().activa()).isFalse();
    }
}
