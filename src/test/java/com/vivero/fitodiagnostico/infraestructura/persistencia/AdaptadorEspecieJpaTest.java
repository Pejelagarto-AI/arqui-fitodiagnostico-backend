package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.puerto.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.RangosPorEspecie;
import com.vivero.fitodiagnostico.infraestructura.ContratoFuenteDeEspeciesTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración (sección 11) e implementación JPA del contrato de
 * {@link ContratoFuenteDeEspeciesTest}, contra H2 (perfil por defecto de
 * application.yml, sin Testcontainers). Confirma, además de los casos
 * comunes, que Flyway corrió las migraciones y sembró las cinco especies
 * del Anexo B.
 *
 * <p>Se usa {@code Replace.NONE} para que el test corra contra el datasource
 * H2 ya declarado en application.yml (con {@code spring.flyway.enabled=true}
 * y {@code locations=classpath:db/migration/{vendor}}), en vez de un
 * embebido anónimo que {@code @DataJpaTest} sustituiría por defecto.
 * {@code fitodiagnostico.tabla-referencia=bd} activa el adaptador JPA, que
 * por defecto está apagado (RA-05: la fuente por defecto es el CSV).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "fitodiagnostico.tabla-referencia=bd")
@Import({AdaptadorEspecieJpa.class, MapeadorEspecie.class})
class AdaptadorEspecieJpaTest extends ContratoFuenteDeEspeciesTest {

    @Autowired
    private EspecieJpaRepository jpaRepository;

    @Autowired
    private AdaptadorEspecieJpa adaptador;

    @Override
    protected RangosPorEspecie rangosPorEspecie() {
        return adaptador;
    }

    @Override
    protected CatalogoDeEspecies catalogoDeEspecies() {
        return adaptador;
    }

    @Test
    void flywaySembroLasCincoEspeciesDelAnexoB() {
        assertThat(jpaRepository.count()).isEqualTo(5);
    }
}
