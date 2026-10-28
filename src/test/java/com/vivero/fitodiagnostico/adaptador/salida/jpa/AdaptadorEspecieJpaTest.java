package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoEditable;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RangosPorEspecie;
import com.vivero.fitodiagnostico.adaptador.salida.ContratoFuenteDeEspeciesTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prueba de integración (sección 11) e implementación JPA del contrato de
 * {@link ContratoFuenteDeEspeciesTest}, contra H2 (perfil por defecto de
 * application.yml, sin Testcontainers). Confirma, además de los casos
 * comunes, que Flyway corrió las migraciones (incluida V4, que agrega
 * {@code activa} y deja la tabla vacía para que el CSV la siembre, D14).
 *
 * <p>Se usa {@code Replace.NONE} para que el test corra contra el datasource
 * H2 ya declarado en application.yml (con {@code spring.flyway.enabled=true}
 * y {@code locations=classpath:db/migration/{vendor}}), en vez de un
 * embebido anónimo que {@code @DataJpaTest} sustituiría por defecto.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
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

    @Override
    protected CatalogoEditable catalogoEditable() {
        return adaptador;
    }

    @Test
    void despuesDeLasMigracionesLaTablaSoloTieneLasCincoEspeciesDelCsvSemilla() {
        // V2/V3 sembraron desde SQL y V4 vació la tabla; el @BeforeEach del contrato
        // la llenó otra vez desde el CSV: cinco filas, no diez.
        assertThat(jpaRepository.count()).isEqualTo(5);
    }

    @Test
    void laMigracionV4DeclaraActivasLasEspeciesSembradas() {
        assertThat(jpaRepository.findAll()).allMatch(EspecieEntity::isActiva);
    }
}
