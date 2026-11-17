package com.vivero.fitodiagnostico.mascota.modelo;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Vitalidad, desmayo, revivir y racha (D131, D132). Funciones puras: entran un estado y una hora, sale el estado
 * nuevo y los eventos que produjo. Sin Spring, sin puertos, sin reloj propio.
 *
 * <p>La vitalidad se integra por tramos: entre {@code actualizadaEn} y la hora pedida, la planta estuvo en su
 * {@code ultimoEstado} (lo último que dijo el sensor), y cada estado tiene una tasa por hora. El tramo se corta en
 * {@code ultimaLecturaEn + ventanaSinLecturas}: sin datos nuevos no se sigue castigando ni premiando.
 */
public final class ReglasDeVitalidad {

    /** El estado nuevo y los eventos que dejó el paso, en orden. */
    public record Avance(EstadoDeMascota estado, List<EventoDeMascota> eventos) {
        public Avance {
            eventos = List.copyOf(eventos);
        }
    }

    private static final double MS_POR_HORA = 3_600_000.0;

    private final ParametrosDeMascota parametros;

    public ReglasDeVitalidad(ParametrosDeMascota parametros) {
        this.parametros = parametros;
    }

    /** Puntos por hora con los que cambia la vitalidad en un estado: negativo si baja. */
    public double tasaPorHora(EstadoGlobal estado) {
        return switch (estado) {
            case CRITICO -> -parametros.perdidaPorHoraEnCritico();
            case EN_RIESGO -> -parametros.perdidaPorHoraEnRiesgo();
            case SALUDABLE -> parametros.gananciaPorHoraSaludable();
        };
    }

    /**
     * Lleva la mascota hasta {@code hasta} con su último estado conocido. Es lo que hace la tarea periódica, y el
     * primer paso de cada lectura. Una hora anterior a {@code actualizadaEn} no cambia nada.
     */
    public Avance avanzar(EstadoDeMascota e, Instant hasta) {
        if (!hasta.isAfter(e.actualizadaEn()) || e.ultimoEstado() == null) {
            return new Avance(hasta.isAfter(e.actualizadaEn()) ? conActualizadaEn(e, hasta) : e, List.of());
        }
        List<EventoDeMascota> eventos = new ArrayList<>();
        Instant limite = e.ultimaLecturaEn().plus(parametros.ventanaSinLecturas());
        Instant finDelTramo = hasta.isBefore(limite) ? hasta : limite;
        double horas = Math.max(0, Duration.between(e.actualizadaEn(), finDelTramo).toMillis()) / MS_POR_HORA;
        double tasa = tasaPorHora(e.ultimoEstado());
        double vitalidad = acotar(e.vitalidad() + tasa * horas);

        boolean desmayada = e.desmayada();
        if (!desmayada && tasa < 0 && horas > 0 && vitalidad <= 0) {
            desmayada = true;
            vitalidad = 0;
            long msHastaCero = (long) (e.vitalidad() / -tasa * MS_POR_HORA);
            eventos.add(EventoDeMascota.nuevo(e.plantaId(), TipoDeEvento.DESMAYO,
                    e.actualizadaEn().plusMillis(msHastaCero), Map.of("estado", e.ultimoEstado().name())));
        }
        if (desmayada && e.ultimoEstado() == EstadoGlobal.SALUDABLE && e.saludableDesde() != null) {
            Instant revive = e.saludableDesde().plus(parametros.tiempoParaRevivir());
            if (!finDelTramo.isBefore(revive)) {
                desmayada = false;
                vitalidad = Math.max(vitalidad, parametros.vitalidadAlRevivir());
                Instant cuando = revive.isAfter(e.actualizadaEn()) ? revive : e.actualizadaEn();
                eventos.add(EventoDeMascota.nuevo(e.plantaId(), TipoDeEvento.REVIVIO, cuando,
                        Map.of("vitalidad", String.valueOf((int) Math.round(vitalidad)))));
            }
        }

        int rachaDias = e.rachaDias();
        if (e.ultimoEstado() != EstadoGlobal.CRITICO && e.rachaDesde() != null && finDelTramo.isAfter(e.rachaDesde())) {
            long dias = Duration.between(e.rachaDesde(), finDelTramo).toMillis()
                    / parametros.duracionDelDiaDeRacha().toMillis();
            if (dias > rachaDias) {
                rachaDias = (int) dias;
                eventos.add(EventoDeMascota.nuevo(e.plantaId(), TipoDeEvento.RACHA,
                        e.rachaDesde().plus(parametros.duracionDelDiaDeRacha().multipliedBy(dias)),
                        Map.of("dias", String.valueOf(rachaDias))));
            }
        }

        EstadoDeMascota nuevo = new EstadoDeMascota(e.plantaId(), vitalidad, desmayada, e.ultimoEstado(),
                e.ultimaLecturaEn(), hasta, e.saludableDesde(), rachaDias, e.rachaDesde(), e.ultimaRegadaEn(),
                e.ultimoSolEn(), e.accesorio());
        return new Avance(nuevo, eventos);
    }

    /**
     * Una lectura nueva con estado {@code estado} llegó a la hora {@code ahora} (del servidor): primero se avanza con
     * el estado anterior y después se adopta el nuevo. Entrar en CRITICO rompe la racha; salir de él la empieza.
     */
    public Avance alimentar(EstadoDeMascota e, EstadoGlobal estado, Instant ahora) {
        Avance avance = avanzar(e, ahora);
        EstadoDeMascota s = avance.estado();
        Instant saludableDesde = estado != EstadoGlobal.SALUDABLE ? null
                : s.ultimoEstado() == EstadoGlobal.SALUDABLE && s.saludableDesde() != null ? s.saludableDesde() : ahora;
        int rachaDias = estado == EstadoGlobal.CRITICO ? 0 : s.rachaDias();
        Instant rachaDesde = estado == EstadoGlobal.CRITICO ? null : s.rachaDesde() != null ? s.rachaDesde() : ahora;
        Instant actualizadaEn = ahora.isAfter(s.actualizadaEn()) ? ahora : s.actualizadaEn();
        EstadoDeMascota nuevo = new EstadoDeMascota(s.plantaId(), s.vitalidad(), s.desmayada(), estado, ahora,
                actualizadaEn, saludableDesde, rachaDias, rachaDesde, s.ultimaRegadaEn(), s.ultimoSolEn(), s.accesorio());
        return new Avance(nuevo, avance.eventos());
    }

    private static EstadoDeMascota conActualizadaEn(EstadoDeMascota e, Instant en) {
        return new EstadoDeMascota(e.plantaId(), e.vitalidad(), e.desmayada(), e.ultimoEstado(), e.ultimaLecturaEn(),
                en, e.saludableDesde(), e.rachaDias(), e.rachaDesde(), e.ultimaRegadaEn(), e.ultimoSolEn(), e.accesorio());
    }

    private static double acotar(double v) {
        return Math.max(0, Math.min(100, v));
    }
}
