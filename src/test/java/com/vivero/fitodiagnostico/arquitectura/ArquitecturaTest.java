package com.vivero.fitodiagnostico.arquitectura;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * Convierte las restricciones arquitectónicas (RA) de la sección 10 de la
 * especificación en pruebas automáticas (sección 11). Si una de estas reglas
 * falla, la corrección correcta es siempre el código de producción, nunca
 * relajar la regla.
 *
 * <p>Se excluyen las clases de prueba del análisis ({@code DoNotIncludeTests}):
 * las RA gobiernan el diseño de src/main, y el uso de {@code @Autowired} sobre
 * campos de {@code @WebMvcTest}/{@code @DataJpaTest} es la forma idiomática de
 * inyectar colaboradores de test en Spring, no una violación de RA-09.
 */
@AnalyzeClasses(packages = "com.vivero.fitodiagnostico", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    // RA-01: el dominio no conoce frameworks.
    @ArchTest
    static final ArchRule el_dominio_no_conoce_frameworks =
        noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta.persistence..");

    // RA-02: Spring Data solo se nombra en infraestructura.
    @ArchTest
    static final ArchRule spring_data_solo_en_infraestructura =
        noClasses().that().resideOutsideOfPackage("..infraestructura..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.springframework.data..");

    // RA-03: el controlador no accede a persistencia.
    @ArchTest
    static final ArchRule el_controlador_no_toca_persistencia =
        noClasses().that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAPackage("..infraestructura.persistencia..");

    // RA-04: las entidades JPA no cruzan el borde de infraestructura.persistencia.
    @ArchTest
    static final ArchRule las_entidades_no_se_escapan =
        classes().that().areAnnotatedWith(Entity.class)
            .should().onlyBeAccessed().byClassesThat()
            .resideInAPackage("..infraestructura.persistencia..");

    // RA-09: inyección por constructor, prohibido @Autowired sobre campos.
    @ArchTest
    static final ArchRule sin_inyeccion_por_campo =
        noFields().should().beAnnotatedWith(Autowired.class);

    // RA-02 (DIP): la aplicación colabora con el dominio a través de sus puertos.
    // No basta con prohibir org.springframework.data: nombrar el repositorio
    // concreto que vive en infraestructura invierte la flecha de dependencia
    // igual de mal, y esa es la forma en que la violación suele colarse.
    @ArchTest
    static final ArchRule la_aplicacion_no_depende_de_infraestructura =
        noClasses().that().resideInAPackage("..aplicacion..")
            .should().dependOnClassesThat().resideInAPackage("..infraestructura..");

    // Complemento de RA-01/RA-03: aplicación no depende de web.
    @ArchTest
    static final ArchRule la_aplicacion_no_depende_de_web =
        noClasses().that().resideInAPackage("..aplicacion..")
            .should().dependOnClassesThat().resideInAPackage("..web..");

    // Complemento de RA-01: el dominio no depende de ninguna otra capa del sistema.
    @ArchTest
    static final ArchRule el_dominio_no_depende_de_otras_capas =
        noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..aplicacion..", "..web..", "..infraestructura..");
}
