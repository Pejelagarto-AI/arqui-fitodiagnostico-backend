package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
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
 * migraciones y sembró las cinco especies del Anexo B, que la búsqueda es
 * insensible a mayúsculas (RF-03) y que el mapeo arma bien los tres
 * {@link com.vivero.fitodiagnostico.dominio.modelo.Rango}.
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
    void flywaySembroLasCincoEspeciesDelAnexoB() {
        assertThat(jpaRepository.count()).isEqualTo(5);
    }

    @Test
    void laBusquedaEsInsensibleAMayusculas() {
        Optional<Especie> porMinusculas = adaptador.buscar("sansevieria");
        Optional<Especie> porMayusculas = adaptador.buscar("SANSEVIERIA");

        assertThat(porMinusculas).isPresent();
        assertThat(porMayusculas).isPresent();
        assertThat(porMinusculas.get().nombre()).isEqualTo("sansevieria");
        assertThat(porMayusculas.get().nombre()).isEqualTo("sansevieria");
    }

    @Test
    void unaEspecieInexistenteDevuelveOptionalVacio() {
        assertThat(adaptador.buscar("especie inexistens")).isEmpty();
    }

    @Test
    void elMapeoArmaBienLosTresRangos() {
        Especie potos = adaptador.buscar("potos")
                .orElseThrow(() -> new AssertionError("potos debería existir en la semilla"));

        assertThat(potos.nombre()).isEqualTo("potos");
        assertThat(potos.rango(Magnitud.HUMEDAD).minimo()).isEqualTo(40.0);
        assertThat(potos.rango(Magnitud.HUMEDAD).maximo()).isEqualTo(70.0);
        assertThat(potos.rango(Magnitud.LUZ).minimo()).isEqualTo(300.0);
        assertThat(potos.rango(Magnitud.LUZ).maximo()).isEqualTo(1200.0);
        assertThat(potos.rango(Magnitud.TEMPERATURA).minimo()).isEqualTo(18.0);
        assertThat(potos.rango(Magnitud.TEMPERATURA).maximo()).isEqualTo(30.0);
    }
}
