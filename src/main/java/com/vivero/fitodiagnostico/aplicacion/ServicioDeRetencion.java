package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.historial.AlmacenDeHistorial;
import com.vivero.fitodiagnostico.historial.PodarHistorialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * La retención de las lecturas crudas (fase 12, D176): por defecto 30 días. Una matera a 5 s genera unas 17 000
 * lecturas al día; sin límite la tabla crece sin parar. Lo que se va se conserva como resumen por hora, que es lo único
 * que la regla de luz acumulada necesita y pesa unas 700 veces menos.
 *
 * <p>Corta siempre en hora en punto (nunca a media hora, para que un resumen no quede a medias) y poda por tandas de un
 * día por planta, cada una en su transacción: una poda grande no sostiene un bloqueo de minutos, y si algo falla a mitad
 * lo ya podado ya estaba resumido y lo que falta se poda en la pasada siguiente. Una planta que falla se registra y no
 * frena a las demás.
 *
 * <p>Se arma en {@code ConfiguracionHistorial}, que le pasa la retención.
 */
public class ServicioDeRetencion implements PodarHistorialPort {

    private static final Logger log = LoggerFactory.getLogger(ServicioDeRetencion.class);
    static final Duration TANDA = Duration.ofDays(1);

    private final AlmacenDeHistorial almacen;
    private final Clock reloj;
    private final Duration retencion;

    public ServicioDeRetencion(AlmacenDeHistorial almacen, Clock reloj, Duration retencion) {
        if (retencion.compareTo(Duration.ofHours(48)) < 0) {
            // Las últimas 24 h son las que la regla de luz lee con lecturas crudas y la hora en curso nunca se resume.
            throw new IllegalArgumentException("la retención de lecturas crudas debe ser de al menos 48 horas");
        }
        this.almacen = almacen;
        this.reloj = reloj;
        this.retencion = retencion;
    }

    @Override
    public int podar() {
        Instant corte = reloj.instant().minus(retencion).truncatedTo(ChronoUnit.HOURS);
        int borradas = 0;
        for (UUID plantaId : almacen.plantasConLecturasAnterioresA(corte)) {
            try {
                borradas += podarPlanta(plantaId, corte);
            } catch (RuntimeException e) {
                log.warn("no se pudo podar el historial de la planta {}: {}", plantaId, e.getMessage());
            }
        }
        return borradas;
    }

    private int podarPlanta(UUID plantaId, Instant corte) {
        Optional<Instant> primera = almacen.primeraLecturaDe(plantaId);
        int borradas = 0;
        for (Instant desde = primera.map(i -> i.truncatedTo(ChronoUnit.HOURS)).orElse(corte);
             desde.isBefore(corte); desde = desde.plus(TANDA)) {
            Instant hasta = desde.plus(TANDA).isAfter(corte) ? corte : desde.plus(TANDA);
            borradas += almacen.resumirYBorrar(plantaId, desde, hasta);
        }
        return borradas;
    }
}
