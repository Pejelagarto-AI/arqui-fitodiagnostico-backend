package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración (sección 11): contra H2 (perfil por defecto de
 * application.yml, sin Testcontainers). Confirma que Flyway corrió las
 * migraciones y sembró las 4 especies, que la búsqueda es insensible a
 * mayúsculas (RF-03) y que el mapeo arma bien los tres {@link com.vivero.fitodiagnostico.dominio.modelo.Rango}.
 *
 * Se usa {@code Replace.NONE} para que el test corra contra el datasource H2
 * ya declarado en application.yml (con {@code spring.flyway.enabled=true} y
 * {@code locations=classpath:db/migration/{vendor}}), en vez de un embebido
 * anónimo que @DataJpaTest sustituiría por defecto.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AdaptadorEspecieJpa.class, MapeadorEspecie.class})
class AdaptadorEspecieJpaTest {

    @Autowired
    private EspecieJpaRepository jpaRepository;

    @Autowired
    private AdaptadorEspecieJpa adaptador;

    @Test
    void flywaySembroLasCuatroEspecies() {
        assertThat(jpaRepository.count()).isEqualTo(4);
    }

    @Test
    void laBusquedaEsInsensibleAMayusculas() {
        Optional<Especie> porMinusculas = adaptador.buscarPorNombreCientifico("monstera deliciosa");
        Optional<Especie> porMayusculas = adaptador.buscarPorNombreCientifico("MONSTERA DELICIOSA");

        assertThat(porMinusculas).isPresent();
        assertThat(porMayusculas).isPresent();
        assertThat(porMinusculas.get().nombreCientifico()).isEqualTo("Monstera deliciosa");
        assertThat(porMayusculas.get().nombreCientifico()).isEqualTo("Monstera deliciosa");
    }

    @Test
    void unaEspecieInexistenteDevuelveOptionalVacio() {
        assertThat(adaptador.buscarPorNombreCientifico("Especie inexistens")).isEmpty();
    }

    @Test
    void elMapeoArmaBienLosTresRangos() {
        Especie ficus = adaptador.buscarPorNombreCientifico("Ficus lyrata")
                .orElseThrow(() -> new AssertionError("Ficus lyrata debería existir en la semilla"));

        assertThat(ficus.nombreComun()).isEqualTo("ficus lira");
        assertThat(ficus.temperatura().minimo()).isEqualTo(18.0);
        assertThat(ficus.temperatura().maximo()).isEqualTo(27.0);
        assertThat(ficus.humedad().minimo()).isEqualTo(45.0);
        assertThat(ficus.humedad().maximo()).isEqualTo(65.0);
        assertThat(ficus.luz().minimo()).isEqualTo(1500.0);
        assertThat(ficus.luz().maximo()).isEqualTo(3000.0);
    }
}
