package com.vivero.fitodiagnostico.adaptador.salida.csv;

import com.vivero.fitodiagnostico.dobles.EspeciesEnMemoria;
import com.vivero.fitodiagnostico.dominio.excepcion.CatalogoSoloLecturaException;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JUnit puro: el adaptador de lectura sobre el CSV, su contraparte de solo lectura y el espejo. */
class AdaptadorEspecieCsvTest {

    private final AdaptadorEspecieCsv adaptador = new AdaptadorEspecieCsv(new LectorDeEspeciesCsv());

    @Test
    void listaLasEspeciesDelCsvOrdenadasPorNombre() {
        assertThat(adaptador.listar()).extracting(Especie::nombre)
                .containsExactly("atrapamoscas", "cactus", "girasol", "helecho", "suculenta");
    }

    @Test
    void buscaSinDistinguirMayusculasYDevuelveLosRangosDelCsv() {
        Especie suculenta = adaptador.buscar("SucuLenta").orElseThrow();

        assertThat(suculenta.rango(Magnitud.HUMEDAD).minimo()).isEqualTo(10.0);
        assertThat(suculenta.rango(Magnitud.HUMEDAD).maximo()).isEqualTo(30.0);
    }

    @Test
    void unaEspecieQueNoEstaOUnNombreNuloDevuelvenVacio() {
        assertThat(adaptador.buscar("no-existe")).isEmpty();
        assertThat(adaptador.buscar(null)).isEmpty();
    }

    @Test
    void laEscrituraSobreElCsvSiempreLanzaCatalogoSoloLectura() {
        CatalogoCsvSoloLectura soloLectura = new CatalogoCsvSoloLectura();
        Especie helecho = adaptador.buscar("helecho").orElseThrow();

        assertThatThrownBy(() -> soloLectura.guardar(helecho)).isInstanceOf(CatalogoSoloLecturaException.class);
        assertThatThrownBy(() -> soloLectura.cambiarActivacion("helecho", false))
                .isInstanceOf(CatalogoSoloLecturaException.class);
    }

    @Test
    void elEspejoCopiaElCsvAlDestinoEnCadaArranqueYPisaLoQueHubiera() {
        EspeciesEnMemoria espejo = new EspeciesEnMemoria();
        EspejoDeEspecies sincronizador = new EspejoDeEspecies(adaptador, espejo);

        assertThat(sincronizador.sincronizar()).isEqualTo(5);
        espejo.cambiarActivacion("helecho", false);
        sincronizador.sincronizar();

        assertThat(espejo.buscar("helecho").orElseThrow().activa()).isTrue();
        assertThat(espejo.listar()).hasSize(5);
    }
}
