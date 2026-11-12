package com.vivero.fitodiagnostico.adaptador.salida;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoEditable;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RangosPorEspecie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato común (evidencia de LSP) que debe cumplir cualquier implementación
 * de los puertos del catálogo: lectura (D8) y escritura. Tras D14 hay un solo
 * adaptador de producción (JPA), pero el puerto sigue siendo la frontera: el
 * doble en memoria {@code EspeciesEnMemoria} pasa exactamente las mismas
 * aserciones, y eso es lo que demuestra que el dominio y la aplicación pueden
 * correr sin base ni archivo.
 *
 * <p>Cada subclase entrega una instancia vacía a través de los tres métodos
 * abstractos; este contrato la siembra con las cinco especies del Anexo B
 * leídas del CSV, usando el mismo {@code SembradorDeEspecies} de producción.
 *
 * <p>{@code @Transactional} va aquí y no solo en la subclase: Spring busca la
 * anotación en la clase que DECLARA el método de prueba, y estos métodos se
 * declaran en esta superclase. Sin ella, el {@code @DataJpaTest} de la
 * subclase no abre transacción y las escrituras de un test quedarían
 * confirmadas para el siguiente. Las subclases sin Spring la ignoran.
 */
@Transactional
public abstract class ContratoFuenteDeEspeciesTest {

    protected abstract RangosPorEspecie rangosPorEspecie();

    protected abstract CatalogoDeEspecies catalogoDeEspecies();

    protected abstract CatalogoEditable catalogoEditable();

    @BeforeEach
    void sembrarElAnexoB() {
        new com.vivero.fitodiagnostico.adaptador.salida.csv.SembradorDeEspecies(
                catalogoDeEspecies(), catalogoEditable(),
                new com.vivero.fitodiagnostico.adaptador.salida.csv.LectorDeEspeciesCsv())
                .sembrarSiVacio();
    }

    private static Especie especieNueva(String nombre) {
        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        rangos.put(Magnitud.HUMEDAD, new Rango(30.0, 60.0));
        rangos.put(Magnitud.LUZ, new Rango(500.0, 1000.0));
        rangos.put(Magnitud.TEMPERATURA, new Rango(18.0, 26.0));
        return new Especie(nombre, rangos);
    }

    @Test
    public void listarDevuelveLasCincoEspeciesDelAnexoBEnOrden() {
        List<Especie> especies = catalogoDeEspecies().listar();

        assertThat(especies).extracting(Especie::nombre)
                .containsExactly("helecho", "lavanda", "potos", "sansevieria", "suculenta");
        assertThat(especies).allMatch(Especie::activa);
    }

    @Test
    public void laBusquedaEsInsensibleAMayusculas() {
        Optional<Especie> porMinusculas = rangosPorEspecie().buscar("sansevieria");
        Optional<Especie> porMayusculas = rangosPorEspecie().buscar("SANSEVIERIA");

        assertThat(porMinusculas).isPresent();
        assertThat(porMayusculas).isPresent();
        assertThat(porMinusculas.get().nombre()).isEqualTo("sansevieria");
        assertThat(porMayusculas.get().nombre()).isEqualTo("sansevieria");
    }

    @Test
    public void unaEspecieInexistenteDevuelveOptionalVacio() {
        assertThat(rangosPorEspecie().buscar("especie inexistens")).isEmpty();
    }

    @Test
    public void losRangosDeSansevieriaSonLosDelAnexoB() {
        Especie sansevieria = rangosPorEspecie().buscar("sansevieria")
                .orElseThrow(() -> new AssertionError("sansevieria debería existir en la referencia"));

        assertThat(sansevieria.rango(Magnitud.HUMEDAD).minimo()).isEqualTo(20.0);
        assertThat(sansevieria.rango(Magnitud.HUMEDAD).maximo()).isEqualTo(45.0);
        assertThat(sansevieria.rango(Magnitud.LUZ).minimo()).isEqualTo(200.0);
        assertThat(sansevieria.rango(Magnitud.LUZ).maximo()).isEqualTo(1500.0);
        assertThat(sansevieria.rango(Magnitud.TEMPERATURA).minimo()).isEqualTo(15.0);
        assertThat(sansevieria.rango(Magnitud.TEMPERATURA).maximo()).isEqualTo(29.0);
    }

    @Test
    public void guardarUnaEspecieNuevaLaAgregaAlCatalogo() {
        catalogoEditable().guardar(especieNueva("orquidea"));

        assertThat(rangosPorEspecie().buscar("orquidea")).isPresent();
        assertThat(catalogoDeEspecies().listar()).extracting(Especie::nombre)
                .contains("orquidea").hasSize(6);
    }

    @Test
    public void guardarUnaEspecieExistenteReemplazaSusRangosSinDuplicarla() {
        Especie editada = rangosPorEspecie().buscar("potos").orElseThrow()
                .conRangos(especieNueva("potos").rangos());

        catalogoEditable().guardar(editada);

        assertThat(catalogoDeEspecies().listar()).hasSize(5);
        assertThat(rangosPorEspecie().buscar("potos").orElseThrow().rango(Magnitud.HUMEDAD))
                .isEqualTo(new Rango(30.0, 60.0));
    }

    @Test
    public void unaEspecieDesactivadaSigueApareciendoEnElListadoYEnLaBusqueda() {
        catalogoEditable().cambiarActivacion("helecho", false);

        Especie helecho = rangosPorEspecie().buscar("helecho").orElseThrow();
        assertThat(helecho.activa()).isFalse();
        assertThat(catalogoDeEspecies().listar()).hasSize(5);
        assertThat(catalogoDeEspecies().listar().stream().filter(Especie::activa)).hasSize(4);
    }

    @Test
    public void reactivarDejaLaEspecieActivaOtraVez() {
        catalogoEditable().cambiarActivacion("helecho", false);
        catalogoEditable().cambiarActivacion("HELECHO", true);

        assertThat(rangosPorEspecie().buscar("helecho").orElseThrow().activa()).isTrue();
    }

    @Test
    public void cambiarLaActivacionDeUnaEspecieInexistenteNoHaceNada() {
        catalogoEditable().cambiarActivacion("fantasma", false);

        assertThat(catalogoDeEspecies().listar()).hasSize(5);
    }

    @Test
    public void sembrarDeNuevoNoPisaLosCambiosDelAdministrador() {
        catalogoEditable().cambiarActivacion("helecho", false);

        sembrarElAnexoB();

        assertThat(rangosPorEspecie().buscar("helecho").orElseThrow().activa()).isFalse();
    }
}
