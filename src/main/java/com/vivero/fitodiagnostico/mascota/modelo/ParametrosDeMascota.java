package com.vivero.fitodiagnostico.mascota.modelo;

import java.time.Duration;
import java.util.Objects;

/**
 * Todos los umbrales y tasas de la mascota en un solo lugar (D139). Los valores por defecto son las constantes de
 * abajo; la composición los sobreescribe con {@code fitodiagnostico.mascota.*}. Son provisionales: se calibran con la
 * sonda real (ver "Cómo calibrar" en docs/fase9/IMPLEMENTACION.md).
 */
public record ParametrosDeMascota(
        double vitalidadInicial,
        double vitalidadAlRevivir,
        double perdidaPorHoraEnCritico,
        double perdidaPorHoraEnRiesgo,
        double gananciaPorHoraSaludable,
        Duration tiempoParaRevivir,
        Duration ventanaSinLecturas,
        Duration duracionDelDiaDeRacha,
        double subidaMinimaDeRiego,
        double factorDeSol,
        double subidaMinimaDeSol,
        Duration ventanaDeReferencia,
        int muestrasMinimasDeReferencia,
        int lecturasARevisar,
        Duration enfriamientoDeEventos,
        Duration plazoParaConfirmarRiego,
        double vitalidadCansada) {

    /** Con la que nace una mascota en su primera lectura: ni llena ni en peligro. */
    public static final double VITALIDAD_INICIAL = 80;
    /** Piso al revivir, para que la próxima lectura EN_RIESGO no la vuelva a desmayar al instante. */
    public static final double VITALIDAD_AL_REVIVIR = 20;
    /** Puntos por hora en CRITICO: de 100 a 0 en 5 horas. */
    public static final double PERDIDA_POR_HORA_EN_CRITICO = 20;
    /** Puntos por hora EN_RIESGO: de 100 a 0 en 20 horas. */
    public static final double PERDIDA_POR_HORA_EN_RIESGO = 5;
    /** Puntos por hora SALUDABLE: de 0 a 100 en 10 horas. */
    public static final double GANANCIA_POR_HORA_SALUDABLE = 10;
    /** Cuánto tiempo seguido SALUDABLE necesita una desmayada para revivir. */
    public static final Duration TIEMPO_PARA_REVIVIR = Duration.ofMinutes(10);
    /** Hasta dónde se extrapola el último estado conocido sin lecturas nuevas (sin datos no se castiga). */
    public static final Duration VENTANA_SIN_LECTURAS = Duration.ofHours(2);
    /** Un "día" de racha: 24 horas corridas sin CRITICO, no un día de calendario. */
    public static final Duration DURACION_DEL_DIA_DE_RACHA = Duration.ofHours(24);
    /** Puntos de humedad sobre la mediana para llamar riego (la regla candidata de la spec). */
    public static final double SUBIDA_MINIMA_DE_RIEGO = 15;
    /** La luz tiene que multiplicarse al menos por esto respecto de la mediana... */
    public static final double FACTOR_DE_SOL = 2.0;
    /** ...y subir al menos estos lux, para que de 10 a 25 lux en la noche no cuente como sol. */
    public static final double SUBIDA_MINIMA_DE_SOL = 2000;
    /** La ventana de la mediana de referencia. */
    public static final Duration VENTANA_DE_REFERENCIA = Duration.ofHours(1);
    /** Menos lecturas que esto en la ventana y no se detecta nada: no hay contra qué comparar. */
    public static final int MUESTRAS_MINIMAS_DE_REFERENCIA = 3;
    /** Cuántas lecturas recientes se leen del historial por cada lectura nueva (una página, máximo 100). */
    public static final int LECTURAS_A_REVISAR = 60;
    /** Tiempo mínimo entre dos REGADA (o dos SALIO_AL_SOL) seguidas. */
    public static final Duration ENFRIAMIENTO_DE_EVENTOS = Duration.ofMinutes(30);
    /** Plazo para que el sensor confirme un "la regué". */
    public static final Duration PLAZO_PARA_CONFIRMAR_RIEGO = Duration.ofMinutes(15);
    /** Con todo en rango pero menos vitalidad que esto, el ánimo es CANSADA y no FELIZ. */
    public static final double VITALIDAD_CANSADA = 30;

    public static final ParametrosDeMascota POR_DEFECTO = new ParametrosDeMascota(
            VITALIDAD_INICIAL, VITALIDAD_AL_REVIVIR, PERDIDA_POR_HORA_EN_CRITICO, PERDIDA_POR_HORA_EN_RIESGO,
            GANANCIA_POR_HORA_SALUDABLE, TIEMPO_PARA_REVIVIR, VENTANA_SIN_LECTURAS, DURACION_DEL_DIA_DE_RACHA,
            SUBIDA_MINIMA_DE_RIEGO, FACTOR_DE_SOL, SUBIDA_MINIMA_DE_SOL, VENTANA_DE_REFERENCIA,
            MUESTRAS_MINIMAS_DE_REFERENCIA, LECTURAS_A_REVISAR, ENFRIAMIENTO_DE_EVENTOS, PLAZO_PARA_CONFIRMAR_RIEGO,
            VITALIDAD_CANSADA);

    public ParametrosDeMascota {
        Objects.requireNonNull(tiempoParaRevivir);
        Objects.requireNonNull(ventanaSinLecturas);
        Objects.requireNonNull(duracionDelDiaDeRacha);
        Objects.requireNonNull(ventanaDeReferencia);
        Objects.requireNonNull(enfriamientoDeEventos);
        Objects.requireNonNull(plazoParaConfirmarRiego);
        exigir(vitalidadInicial >= 0 && vitalidadInicial <= 100, "vitalidad-inicial entre 0 y 100");
        exigir(vitalidadAlRevivir >= 0 && vitalidadAlRevivir <= 100, "vitalidad-al-revivir entre 0 y 100");
        exigir(perdidaPorHoraEnCritico >= 0 && perdidaPorHoraEnRiesgo >= 0 && gananciaPorHoraSaludable >= 0,
                "las tasas no pueden ser negativas");
        exigir(!duracionDelDiaDeRacha.isNegative() && !duracionDelDiaDeRacha.isZero(), "el día de racha debe durar");
        exigir(subidaMinimaDeRiego > 0 && subidaMinimaDeSol >= 0 && factorDeSol >= 1, "umbrales de salto inválidos");
        exigir(muestrasMinimasDeReferencia >= 1, "muestras-minimas-de-referencia al menos 1");
        exigir(lecturasARevisar >= muestrasMinimasDeReferencia + 2 && lecturasARevisar <= 100,
                "lecturas-a-revisar entre muestras mínimas + 2 y 100");
    }

    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) {
            throw new IllegalArgumentException("parámetros de mascota: " + mensaje);
        }
    }
}
