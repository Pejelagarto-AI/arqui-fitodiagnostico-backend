package com.vivero.fitodiagnostico.arquitectura;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.beans.factory.annotation.Autowired;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

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

    // RA-02: Spring Data solo se nombra en el adaptador de persistencia.
    @ArchTest
    static final ArchRule spring_data_solo_en_el_adaptador_jpa =
        noClasses().that().resideOutsideOfPackage("..adaptador.salida.jpa..")
            .should().dependOnClassesThat()
            .resideInAPackage("org.springframework.data..");

    // RA-03: el controlador no accede a persistencia.
    @ArchTest
    static final ArchRule el_controlador_no_toca_persistencia =
        noClasses().that().resideInAPackage("..adaptador.entrada..")
            .should().dependOnClassesThat().resideInAPackage("..adaptador.salida.jpa..");

    // RA-04: las entidades JPA no cruzan el borde de adaptador.salida.jpa.
    @ArchTest
    static final ArchRule las_entidades_no_se_escapan =
        classes().that().areAnnotatedWith(Entity.class)
            .should().onlyBeAccessed().byClassesThat()
            .resideInAPackage("..adaptador.salida.jpa..");

    // RA-09: inyección por constructor, prohibido @Autowired sobre campos.
    @ArchTest
    static final ArchRule sin_inyeccion_por_campo =
        noFields().should().beAnnotatedWith(Autowired.class);

    // RA-02 (DIP): la aplicación colabora con el dominio a través de sus puertos.
    // No basta con prohibir org.springframework.data: nombrar el repositorio
    // concreto que vive en un adaptador invierte la flecha de dependencia
    // igual de mal, y esa es la forma en que la violación suele colarse.
    @ArchTest
    static final ArchRule la_aplicacion_no_depende_de_adaptadores_ni_de_la_composicion =
        noClasses().that().resideInAPackage("..aplicacion..")
            .should().dependOnClassesThat().resideInAnyPackage("..adaptador..", "com.vivero.fitodiagnostico.config..");

    // Complemento de RA-01/RA-03: aplicación no depende de la entrada HTTP (ya cubierto arriba; se deja nombrado).
    @ArchTest
    static final ArchRule la_aplicacion_no_depende_del_adaptador_rest =
        noClasses().that().resideInAPackage("..aplicacion..")
            .should().dependOnClassesThat().resideInAPackage("..adaptador.entrada.rest..");

    // Complemento de RA-01: el dominio no depende de ninguna otra capa del sistema.
    @ArchTest
    static final ArchRule el_dominio_no_depende_de_otras_capas =
        noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..aplicacion..", "..adaptador..", "com.vivero.fitodiagnostico.config..");

    // Corte 2 (RA4): el dominio no conoce la seguridad ni el cifrado. El hash de
    // contraseñas es un puerto (CifradorDeContrasena); BCrypt vive en adaptador.salida.seguridad.
    // "..crypto.." cubre org.springframework.security.crypto.. y javax.crypto..
    @ArchTest
    static final ArchRule el_dominio_no_conoce_seguridad_ni_cifrado =
        noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.security..", "..crypto..",
                    "org.bouncycastle..", "com.nimbusds..");

    // Corte 2 (RA4): el dominio no conoce el transporte WebSocket. Pedirle una lectura
    // a una maceta será un puerto de salida; que detrás haya un socket es detalle.
    @ArchTest
    static final ArchRule el_dominio_no_conoce_websocket =
        noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.web.socket..", "jakarta.websocket..",
                    "javax.websocket..");

    // Corte 2, bloque 3 (D3, RA4): "consultar nunca escribe" hecho estructura. La consulta del
    // diagnóstico de una planta no puede depender de lo que produce un diagnóstico (evaluador),
    // lo avisa (detector, notificador) ni del servicio que registra mediciones. Si mañana alguien
    // quiere "recalcular al consultar", esta regla obliga a decidirlo a la vista.
    @ArchTest
    static final ArchRule consultar_diagnosticos_no_puede_producir_ni_avisar =
        noClasses().that().haveSimpleName("ServicioConsultaDeDiagnosticos")
            .should().dependOnClassesThat().belongToAnyOf(
                    com.vivero.fitodiagnostico.dominio.servicio.EvaluadorDeEstado.class,
                    com.vivero.fitodiagnostico.dominio.servicio.DetectorDeCambioDeEstado.class,
                    com.vivero.fitodiagnostico.dominio.puerto.salida.Notificador.class,
                    com.vivero.fitodiagnostico.aplicacion.ServicioMediciones.class);

    // Corte 2, bloque 3 (D19): las políticas de dominio son funciones puras. Reciben sus datos ya
    // cargados; si una empezara a llamar a un puerto, dejaría de probarse con dos objetos en memoria.
    @ArchTest
    static final ArchRule las_politicas_de_dominio_no_llaman_puertos =
        noClasses().that().haveSimpleName("PoliticaDeAcceso")
            .or().haveSimpleName("DetectorDeCambioDeEstado")
            .or().haveSimpleName("PoliticaDeAdministradores")
            .should().dependOnClassesThat().resideInAPackage("..dominio.puerto..");

    // Corte 2, bloque 4 (RA4, RA5): el dominio no conoce el correo. Avisar es el puerto Notificador;
    // que detrás haya SMTP, y con qué librería, es un adaptador de salida.
    @ArchTest
    static final ArchRule el_dominio_no_conoce_el_correo =
        noClasses().that().resideInAPackage("..dominio..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.mail..", "jakarta.mail..", "javax.mail..");

    // Corte 2, bloque 4 (RA5): correo, WebSocket y mensajería STOMP solo se nombran en los adaptadores y en la composición.
    // Es lo que hace cierta la pregunta de mutación "si el WebSocket pasa a Server-Sent Events, ¿qué cambia?":
    // ni la aplicación ni los controladores conocen el transporte de la vista en vivo ni el del correo.
    @ArchTest
    static final ArchRule correo_y_websocket_solo_en_adaptadores_y_config =
        noClasses().that().resideOutsideOfPackage("..adaptador..").and().resideOutsideOfPackage("com.vivero.fitodiagnostico.config..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.mail..", "jakarta.mail..", "javax.mail..",
                    "org.springframework.web.socket..", "org.springframework.messaging..",
                    "jakarta.websocket..", "javax.websocket..");

    // Corte 2, bloque 4 (D57): la aplicación avisa por el puerto y nada más. Que los avisos esperen al
    // commit es un decorador de un adaptador de salida; si un servicio nombrara el decorador o los eventos
    // transaccionales de Spring, el "después del commit" dejaría de ser invisible para la aplicación.
    @ArchTest
    static final ArchRule la_aplicacion_no_conoce_la_sincronizacion_de_transacciones =
        noClasses().that().resideInAPackage("..aplicacion..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework.transaction.support..", "org.springframework.context.event..")
            .orShould().dependOnClassesThat().haveSimpleName("ApplicationEventPublisher");

    // Corte 2, bloque 4 (D51): el token de un enlace nunca es un campo de una entidad JPA: solo existe su huella.
    @ArchTest
    static final ArchRule las_entidades_no_guardan_tokens_en_claro =
        noFields().that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
            .should().haveNameMatching("(?i)token|tokenEnClaro|contrasena");

    // ------------------------------------------------------------------------------------------
    // Fase 2: arquitectura hexagonal (puertos y adaptadores)
    // ------------------------------------------------------------------------------------------

    // El dominio (modelo, servicios, excepciones y los PUERTOS de entrada y salida) solo conoce
    // dominio y la biblioteca estándar de Java. Es la regla más fuerte: no hay JSON, HTTP, SQL, CSV,
    // JWT ni Spring que se cuele, ni siquiera una anotación.
    @ArchTest
    static final ArchRule el_dominio_solo_depende_de_dominio_y_java =
        classes().that().resideInAPackage("..dominio..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("..dominio..", "java..");

    // Los puertos son interfaces: los define el dominio y otros los implementan. (Los records anidados
    // de los puertos de entrada son tipos de resultado, no clases de comportamiento.)
    @ArchTest
    static final ArchRule los_puertos_son_interfaces =
        classes().that().resideInAnyPackage("..dominio.puerto.entrada..", "..dominio.puerto.salida..")
            .and().areTopLevelClasses()
            .should().beInterfaces();

    // Los dos lados del hexágono no se conocen: un adaptador de entrada no nombra uno de salida
    // (solo habla con puertos) y un adaptador de salida jamás llama a uno de entrada.
    @ArchTest
    static final ArchRule la_entrada_no_depende_de_la_salida =
        noClasses().that().resideInAPackage("..adaptador.entrada..")
            .should().dependOnClassesThat().resideInAPackage("..adaptador.salida..");

    @ArchTest
    static final ArchRule la_salida_no_depende_de_la_entrada =
        noClasses().that().resideInAPackage("..adaptador.salida..")
            .should().dependOnClassesThat().resideInAPackage("..adaptador.entrada..");

    // Cada tecnología de salida es una isla: JPA no conoce al CSV, ni el CSV al correo.
    @ArchTest
    static final ArchRule los_adaptadores_de_salida_no_se_conocen_entre_si =
        slices().matching("..adaptador.salida.(*)..").should().notDependOnEachOther();

    // Idem entre los de entrada (REST, dispositivo, WebSocket, arranque).
    @ArchTest
    static final ArchRule los_adaptadores_de_entrada_no_se_conocen_entre_si =
        slices().matching("..adaptador.entrada.(*)..").should().notDependOnEachOther();

    // La composición es el ÚNICO sitio que conoce a todos: ningún adaptador depende de config.
    @ArchTest
    static final ArchRule los_adaptadores_no_dependen_de_la_composicion =
        noClasses().that().resideInAPackage("..adaptador..")
            .should().dependOnClassesThat().resideInAPackage("com.vivero.fitodiagnostico.config..");

    // Los adaptadores de entrada llaman a PUERTOS de entrada, no a las clases de la aplicación que
    // los implementan. Única excepción: RutasDelFront, que es una tabla de rutas del front y no un caso de uso.
    @ArchTest
    static final ArchRule los_adaptadores_dependen_de_puertos_y_no_de_servicios =
        noClasses().that().resideInAPackage("..adaptador..")
            .should().dependOnClassesThat(
                resideInAPackage("..aplicacion..").and(not(simpleName("RutasDelFront"))));

    // Quien implementa un puerto de entrada es la aplicación (los casos de uso viven dentro del hexágono).
    @ArchTest
    static final ArchRule los_puertos_de_entrada_los_implementa_la_aplicacion =
        classes().that().implement(resideInAPackage("..dominio.puerto.entrada.."))
            .should().resideInAPackage("..aplicacion..");

    // Quien implementa un puerto de salida es un adaptador de salida (la composición puede armar decoradores
    // y la aplicación no implementa puertos de salida).
    @ArchTest
    static final ArchRule los_puertos_de_salida_los_implementa_un_adaptador_de_salida =
        classes().that().implement(resideInAPackage("..dominio.puerto.salida.."))
            .and().areNotInterfaces()
            .should().resideInAPackage("..adaptador.salida..");
}
