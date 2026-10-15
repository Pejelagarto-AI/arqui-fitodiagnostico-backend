package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dobles.Muestras;
import com.vivero.fitodiagnostico.dominio.modelo.Credencial;
import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;
import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Medicion;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;
import com.vivero.fitodiagnostico.dominio.servicio.ClasificadorDeParametros;
import com.vivero.fitodiagnostico.dominio.servicio.EvaluadorDeEstado;
import com.vivero.fitodiagnostico.dominio.servicio.RedactorDeRecomendaciones;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaPorDesviacion;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Base de las pruebas de los adaptadores de plantas, accesos y diagnósticos:
 * H2 con Flyway real (V6 a V10). Cada prueba corre en una transacción que se
 * deshace, así que las especies y usuarios que crea (con nombres únicos, porque
 * la base es compartida en la JVM) no sobreviven a la prueba.
 *
 * <p>Los instantes se truncan a microsegundos: es la precisión de la columna
 * {@code TIMESTAMP WITH TIME ZONE} y, sin truncar, la prueba compararía una
 * fecha con nanosegundos contra una leída sin ellos.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({AdaptadorPlantaJpa.class, MapeadorPlanta.class, AdaptadorEspecieJpa.class, MapeadorEspecie.class,
        AdaptadorUsuarioJpa.class, MapeadorUsuario.class, AdaptadorAccesoJpa.class,
        AdaptadorDiagnosticoJpa.class, MapeadorDiagnosticoDePlanta.class,
        AdaptadorInvitacionJpa.class, AdaptadorRestablecimientoJpa.class, AdaptadorSolicitudEspecieJpa.class})
abstract class BaseDePersistenciaDePlantas {

    @Autowired protected AdaptadorPlantaJpa plantas;
    @Autowired protected AdaptadorAccesoJpa accesos;
    @Autowired protected AdaptadorDiagnosticoJpa diagnosticos;
    @Autowired protected AdaptadorEspecieJpa especies;
    @Autowired protected AdaptadorUsuarioJpa usuarios;
    @Autowired protected AdaptadorInvitacionJpa invitaciones;
    @Autowired protected AdaptadorRestablecimientoJpa restablecimientos;
    @Autowired protected AdaptadorSolicitudEspecieJpa solicitudes;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected EntityManager entityManager;

    private final EvaluadorDeEstado evaluador = new EvaluadorDeEstado(new ClasificadorDeParametros(),
            new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO), new RedactorDeRecomendaciones());

    protected static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    protected Usuario usuario(String prefijo) {
        Usuario usuario = Usuario.nuevo(prefijo, prefijo + "-" + UUID.randomUUID() + "@vivero.co", new Credencial("hash"));
        usuarios.guardar(usuario);
        return usuario;
    }

    protected Especie especie() {
        Especie especie = Muestras.especie("jpa-" + UUID.randomUUID());
        especies.guardar(especie);
        return especie;
    }

    protected Planta planta(Usuario dueno, Especie especie, String apodo, Instant registradaEn) {
        Planta planta = new Planta(UUID.randomUUID(), dueno.id(), apodo, especie, registradaEn);
        plantas.guardar(planta);
        return planta;
    }

    protected Planta planta(Usuario dueno, Especie especie) {
        return planta(dueno, especie, "Pepa", ahora());
    }

    /** Un diagnóstico real del evaluador, con la fecha truncada a la precisión de la base. */
    protected Diagnostico evaluar(Especie especie, Medicion medicion) {
        Diagnostico d = evaluador.evaluar(especie, medicion);
        return new Diagnostico(d.especie(), d.medicion(), d.estado(), d.parametros(), d.recomendaciones(),
                d.evaluadoEn().truncatedTo(ChronoUnit.MICROS));
    }

    /** Fuerza que lo pendiente llegue a la base y vacía la sesión: lo siguiente que se lea sale de la tabla. */
    protected void sincronizar() {
        entityManager.flush();
        entityManager.clear();
    }
}
