package com.vivero.fitodiagnostico.mascota.modelo;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Vitalidad, desmayo, revivir y racha (D131, D132), sin Spring ni puertos. Tasas por defecto: -20, -5, +10 por hora. */
class ReglasDeVitalidadDeMascotaTest {

    private final ParametrosDeMascota parametros = ParametrosDeMascota.POR_DEFECTO;
    private final ReglasDeVitalidad reglas = new ReglasDeVitalidad(parametros);
    private final UUID planta = UUID.randomUUID();
    private final Instant t0 = Instant.parse("2026-11-16T08:00:00Z");

    private EstadoDeMascota conLectura(EstadoGlobal estado, double vitalidad) {
        EstadoDeMascota nueva = EstadoDeMascota.nueva(planta, t0, parametros);
        EstadoDeMascota alimentada = reglas.alimentar(nueva, estado, t0).estado();
        return new EstadoDeMascota(planta, vitalidad, false, alimentada.ultimoEstado(), alimentada.ultimaLecturaEn(),
                alimentada.actualizadaEn(), alimentada.saludableDesde(), 0, alimentada.rachaDesde(), null, null);
    }

    private static Instant mas(Instant t, Duration d) {
        return t.plus(d);
    }

    @Test
    void laPrimeraLecturaNaceConLaVitalidadInicialYNoCambiaHastaQuePaseTiempo() {
        ReglasDeVitalidad.Avance avance = reglas.alimentar(EstadoDeMascota.nueva(planta, t0, parametros),
                EstadoGlobal.CRITICO, t0);

        assertThat(avance.estado().vitalidad()).isEqualTo(ParametrosDeMascota.VITALIDAD_INICIAL);
        assertThat(avance.estado().ultimoEstado()).isEqualTo(EstadoGlobal.CRITICO);
        assertThat(avance.eventos()).isEmpty();
    }

    @Test
    void unaHoraEnCriticoQuitaVeinteYEnRiesgoCinco() {
        assertThat(reglas.avanzar(conLectura(EstadoGlobal.CRITICO, 80), mas(t0, Duration.ofHours(1))).estado().vitalidad())
                .isCloseTo(60, within(0.001));
        assertThat(reglas.avanzar(conLectura(EstadoGlobal.EN_RIESGO, 80), mas(t0, Duration.ofHours(1))).estado().vitalidad())
                .isCloseTo(75, within(0.001));
    }

    @Test
    void saludableSubeDiezPorHoraYNoPasaDeCien() {
        assertThat(reglas.avanzar(conLectura(EstadoGlobal.SALUDABLE, 50), mas(t0, Duration.ofMinutes(30))).estado().vitalidad())
                .isCloseTo(55, within(0.001));
        assertThat(reglas.avanzar(conLectura(EstadoGlobal.SALUDABLE, 95), mas(t0, Duration.ofHours(3))).estado().vitalidad())
                .isEqualTo(100);
    }

    @Test
    void sinLecturasNuevasSoloSeExtrapolaHastaLaVentana() {
        // 5 horas sin lecturas en CRITICO: solo cuentan 2 (la ventana), así que baja 40 y no 100.
        EstadoDeMascota e = reglas.avanzar(conLectura(EstadoGlobal.CRITICO, 80), mas(t0, Duration.ofHours(5))).estado();

        assertThat(e.vitalidad()).isCloseTo(40, within(0.001));
        assertThat(e.desmayada()).isFalse();
        assertThat(e.actualizadaEn()).isEqualTo(mas(t0, Duration.ofHours(5)));
    }

    @Test
    void enCeroSeDesmayaUnaSolaVezConLaHoraEnQueLlegoACero() {
        ReglasDeVitalidad.Avance avance = reglas.avanzar(conLectura(EstadoGlobal.CRITICO, 10), mas(t0, Duration.ofHours(1)));

        assertThat(avance.estado().vitalidad()).isZero();
        assertThat(avance.estado().desmayada()).isTrue();
        assertThat(avance.eventos()).singleElement().satisfies(ev -> {
            assertThat(ev.tipo()).isEqualTo(TipoDeEvento.DESMAYO);
            assertThat(ev.ocurridoEn()).isEqualTo(mas(t0, Duration.ofMinutes(30)));
            assertThat(ev.detalle()).containsEntry("estado", "CRITICO");
        });

        // Nunca muere: sigue en 0 y no se vuelve a desmayar.
        ReglasDeVitalidad.Avance despues = reglas.avanzar(avance.estado(), mas(t0, Duration.ofMinutes(90)));
        assertThat(despues.estado().vitalidad()).isZero();
        assertThat(despues.eventos()).isEmpty();
    }

    @Test
    void revivTrasDiezMinutosSeguidosSaludableYQuedaConElPiso() {
        EstadoDeMascota desmayada = reglas.avanzar(conLectura(EstadoGlobal.CRITICO, 10), mas(t0, Duration.ofHours(1))).estado();
        Instant t1 = mas(t0, Duration.ofHours(1));
        EstadoDeMascota saludable = reglas.alimentar(desmayada, EstadoGlobal.SALUDABLE, t1).estado();

        ReglasDeVitalidad.Avance antes = reglas.avanzar(saludable, mas(t1, Duration.ofMinutes(9)));
        assertThat(antes.estado().desmayada()).isTrue();
        assertThat(antes.eventos()).isEmpty();

        ReglasDeVitalidad.Avance revive = reglas.avanzar(antes.estado(), mas(t1, Duration.ofMinutes(10)));
        assertThat(revive.estado().desmayada()).isFalse();
        assertThat(revive.estado().vitalidad()).isEqualTo(ParametrosDeMascota.VITALIDAD_AL_REVIVIR);
        assertThat(revive.eventos()).extracting(EventoDeMascota::tipo).containsExactly(TipoDeEvento.REVIVIO);
    }

    @Test
    void unaRecaidaAntesDeLosDiezMinutosReiniciaLaCuenta() {
        EstadoDeMascota desmayada = reglas.avanzar(conLectura(EstadoGlobal.CRITICO, 10), mas(t0, Duration.ofHours(1))).estado();
        Instant t1 = mas(t0, Duration.ofHours(1));
        EstadoDeMascota e = reglas.alimentar(desmayada, EstadoGlobal.SALUDABLE, t1).estado();
        e = reglas.alimentar(e, EstadoGlobal.EN_RIESGO, mas(t1, Duration.ofMinutes(5))).estado();
        e = reglas.alimentar(e, EstadoGlobal.SALUDABLE, mas(t1, Duration.ofMinutes(6))).estado();

        assertThat(reglas.avanzar(e, mas(t1, Duration.ofMinutes(12))).estado().desmayada()).isTrue();
        assertThat(reglas.avanzar(e, mas(t1, Duration.ofMinutes(16))).estado().desmayada()).isFalse();
    }

    @Test
    void cadaDiaCompletoSinCriticoSumaUnoALaRacha() {
        EstadoDeMascota e = conLectura(EstadoGlobal.SALUDABLE, 100);
        // Lecturas cada 1 h 30 min para no salir de la ventana sin lecturas.
        java.util.List<EventoDeMascota> eventos = new java.util.ArrayList<>();
        for (int i = 1; i <= 34; i++) {
            ReglasDeVitalidad.Avance a = reglas.alimentar(e, EstadoGlobal.SALUDABLE, mas(t0, Duration.ofMinutes(90L * i)));
            eventos.addAll(a.eventos());
            e = a.estado();
        }   // 51 horas

        assertThat(e.rachaDias()).isEqualTo(2);
        assertThat(eventos).filteredOn(ev -> ev.tipo() == TipoDeEvento.RACHA)
                .extracting(ev -> ev.detalle().get("dias")).containsExactly("1", "2");
        assertThat(eventos).filteredOn(ev -> ev.tipo() == TipoDeEvento.RACHA).first()
                .extracting(EventoDeMascota::ocurridoEn).isEqualTo(mas(t0, Duration.ofHours(24)));
    }

    @Test
    void entrarEnCriticoRompeLaRachaYSalirLaEmpiezaDeNuevo() {
        EstadoDeMascota e = conLectura(EstadoGlobal.SALUDABLE, 100);
        e = new EstadoDeMascota(planta, 100, false, EstadoGlobal.SALUDABLE, t0, t0, t0, 3, t0.minus(Duration.ofDays(3)),
                null, null);

        EstadoDeMascota rota = reglas.alimentar(e, EstadoGlobal.CRITICO, mas(t0, Duration.ofMinutes(5))).estado();
        assertThat(rota.rachaDias()).isZero();
        assertThat(rota.rachaDesde()).isNull();

        EstadoDeMascota otraVez = reglas.alimentar(rota, EstadoGlobal.EN_RIESGO, mas(t0, Duration.ofMinutes(10))).estado();
        assertThat(otraVez.rachaDesde()).isEqualTo(mas(t0, Duration.ofMinutes(10)));
    }

    @Test
    void unaHoraAnteriorALaUltimaActualizacionNoCambiaNada() {
        EstadoDeMascota e = conLectura(EstadoGlobal.CRITICO, 50);

        assertThat(reglas.avanzar(e, t0.minusSeconds(30)).estado()).isEqualTo(e);
    }

    @Test
    void lasTasasSonConfigurables() {
        ParametrosDeMascota lentos = new ParametrosDeMascota(80, 20, 2, 1, 1, Duration.ofMinutes(10), Duration.ofHours(2),
                Duration.ofHours(24), 15, 2, 2000, Duration.ofHours(1), 3, 60, Duration.ofMinutes(30),
                Duration.ofMinutes(15), 30);

        assertThat(new ReglasDeVitalidad(lentos).tasaPorHora(EstadoGlobal.CRITICO)).isEqualTo(-2);
    }
}
