package com.vivero.fitodiagnostico.infraestructura;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.puerto.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.RangosPorEspecie;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato común (evidencia de LSP) que debe cumplir cualquier fuente de la
 * tabla de referencia del Anexo B, sea CSV o base de datos: las cinco
 * especies sembradas son las mismas y se comportan igual desde el punto de
 * vista del dominio, aunque las dos implementaciones ni se conocen ni
 * comparten infraestructura entre sí.
 *
 * <p>Dos subclases la extienden: una JUnit puro sobre el adaptador CSV y otra
 * {@code @DataJpaTest} sobre el adaptador JPA. Cada una provee su propia
 * instancia (con o sin Spring) a través de {@link #rangosPorEspecie()} y
 * {@link #catalogoDeEspecies()}.
 */
public abstract class ContratoFuenteDeEspeciesTest {

    protected abstract RangosPorEspecie rangosPorEspecie();

    protected abstract CatalogoDeEspecies catalogoDeEspecies();

    @Test
    public void listarDevuelveLasCincoEspeciesDelAnexoBEnOrden() {
        List<Especie> especies = catalogoDeEspecies().listar();

        assertThat(especies).extracting(Especie::nombre)
                .containsExactly("helecho", "lavanda", "potos", "sansevieria", "suculenta");
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
}
