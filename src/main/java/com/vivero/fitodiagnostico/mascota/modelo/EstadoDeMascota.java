package com.vivero.fitodiagnostico.mascota.modelo;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que la mascota recuerda de una planta (una fila de {@code mascota}, V16). Inmutable: las reglas devuelven
 * copias.
 *
 * @param vitalidad       0 a 100, con decimales para no perder lo que se acumula minuto a minuto
 * @param ultimoEstado    el estado global de la última lectura; nulo si nunca hubo
 * @param ultimaLecturaEn hora del servidor en que llegó la última lectura (el reloj de la vitalidad, D131)
 * @param actualizadaEn   hasta dónde está integrada la vitalidad
 * @param saludableDesde  desde cuándo está SALUDABLE sin interrupción; nulo si no lo está
 * @param rachaDesde      inicio del tramo actual sin CRITICO; nulo si la última lectura fue CRITICO
 * @param ultimaRegadaEn  hora (de la lectura) de la última REGADA, para el enfriamiento
 * @param ultimoSolEn     ídem para SALIO_AL_SOL
 */
public record EstadoDeMascota(UUID plantaId, double vitalidad, boolean desmayada, EstadoGlobal ultimoEstado,
                              Instant ultimaLecturaEn, Instant actualizadaEn, Instant saludableDesde,
                              int rachaDias, Instant rachaDesde, Instant ultimaRegadaEn, Instant ultimoSolEn) {

    public EstadoDeMascota {
        if (plantaId == null || actualizadaEn == null) {
            throw new IllegalArgumentException("el estado de la mascota requiere planta y fecha");
        }
        if (vitalidad < 0 || vitalidad > 100 || Double.isNaN(vitalidad)) {
            throw new IllegalArgumentException("la vitalidad va de 0 a 100: " + vitalidad);
        }
        if (rachaDias < 0) {
            throw new IllegalArgumentException("la racha no puede ser negativa");
        }
    }

    /** La mascota de una planta que todavía no tuvo lecturas. */
    public static EstadoDeMascota nueva(UUID plantaId, Instant ahora, ParametrosDeMascota parametros) {
        return new EstadoDeMascota(plantaId, parametros.vitalidadInicial(), false, null, null, ahora, null,
                0, null, null, null);
    }

    /** La vitalidad que se muestra: entera, redondeada. */
    public int vitalidadEntera() {
        return (int) Math.round(vitalidad);
    }

    public EstadoDeMascota conRegadaEn(Instant en) {
        return new EstadoDeMascota(plantaId, vitalidad, desmayada, ultimoEstado, ultimaLecturaEn, actualizadaEn,
                saludableDesde, rachaDias, rachaDesde, en, ultimoSolEn);
    }

    public EstadoDeMascota conSolEn(Instant en) {
        return new EstadoDeMascota(plantaId, vitalidad, desmayada, ultimoEstado, ultimaLecturaEn, actualizadaEn,
                saludableDesde, rachaDias, rachaDesde, ultimaRegadaEn, en);
    }
}
