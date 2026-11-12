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
    void listaLasCincoEspeciesDelAnexoBOrdenadasPorNombre() {
        assertThat(adaptador.listar()).extracting(Especie::nombre)
                .containsExactly("helecho", "lavanda", "potos", "sansevieria", "suculenta");
    }

    @Test
    void buscaSinDistinguirMayusculasYDevuelveLosRangosDelCsv() {
        Especie sansevieria = adaptador.buscar("SansEvieria").orElseThrow();

        assertThat(sansevieria.rango(Magnitud.HUMEDAD).minimo()).isEqualTo(20.0);
        assertThat(sansevieria.rango(Magnitud.HUMEDAD).maximo()).isEqualTo(45.0);
    }

    @Test
    void unaEspecieQueNoEstaOUnNombreNuloDevuelvenVacio() {
        assertThat(adaptador.buscar("no-existe")).isEmpty();
        assertThat(adaptador.buscar(null)).isEmpty();
    }

    @Test
    void laEscrituraSobreElCsvSiempreLanzaCatalogoSoloLectura() {
        CatalogoCsvSoloLectura soloLectura = new CatalogoCsvSoloLectura();
        Especie potos = adaptador.buscar("potos").orElseThrow();

        assertThatThrownBy(() -> soloLectura.guardar(potos)).isInstanceOf(CatalogoSoloLecturaException.class);
        assertThatThrownBy(() -> soloLectura.cambiarActivacion("potos", false))
                .isInstanceOf(CatalogoSoloLecturaException.class);
    }

    @Test
    void elEspejoCopiaElCsvAlDestinoEnCadaArranqueYPisaLoQueHubiera() {
        EspeciesEnMemoria espejo = new EspeciesEnMemoria();
        EspejoDeEspecies sincronizador = new EspejoDeEspecies(adaptador, espejo);

        assertThat(sincronizador.sincronizar()).isEqualTo(5);
        espejo.cambiarActivacion("potos", false);
        sincronizador.sincronizar();

        assertThat(espejo.buscar("potos").orElseThrow().activa()).isTrue();
        assertThat(espejo.listar()).hasSize(5);
    }
}
