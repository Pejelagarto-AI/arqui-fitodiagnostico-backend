package com.vivero.fitodiagnostico.salud;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lo que el servidor sabe de la salud de una matera. Inmutable: cada cambio devuelve otro valor.
 *
 * <p>{@code reportadoEn} es cuándo habló la matera de su salud; {@code ultimaLecturaEn} es cuándo el servidor
 * recibió su última lectura (hora del servidor, no la de la matera: es la que sirve para medir el silencio);
 * {@code cambioEn} es cuándo cambió {@code estado} o algún sensor por última vez. Los tres pueden ser nulos.
 */
public record SaludDelDispositivo(String serial, EstadoDeSalud estado, Map<SensorDeMatera, EstadoDeSensor> sensores,
                                  String detalle, Instant reportadoEn, Instant ultimaLecturaEn, Instant cambioEn) {

    public SaludDelDispositivo {
        if (serial == null || serial.isBlank()) {
            throw new IllegalArgumentException("la salud requiere el serial del dispositivo");
        }
        if (estado == null) {
            throw new IllegalArgumentException("la salud requiere estado");
        }
        sensores = completar(sensores);
    }

    /** Un sensor que no se nombra cuenta como OK. */
    public static Map<SensorDeMatera, EstadoDeSensor> completar(Map<SensorDeMatera, EstadoDeSensor> sensores) {
        EnumMap<SensorDeMatera, EstadoDeSensor> completos = new EnumMap<>(SensorDeMatera.class);
        for (SensorDeMatera s : SensorDeMatera.values()) {
            completos.put(s, sensores == null ? EstadoDeSensor.OK : sensores.getOrDefault(s, EstadoDeSensor.OK));
        }
        return Map.copyOf(completos);
    }

    public static Map<SensorDeMatera, EstadoDeSensor> todosOk() {
        return Map.of(SensorDeMatera.HUMEDAD, EstadoDeSensor.OK, SensorDeMatera.LUZ, EstadoDeSensor.OK,
                SensorDeMatera.TEMPERATURA, EstadoDeSensor.OK);
    }

    /** Cuándo fue lo último que se supo de ella: una lectura o un reporte de salud. */
    public Optional<Instant> ultimaNoticiaEn() {
        if (reportadoEn == null) return Optional.ofNullable(ultimaLecturaEn);
        if (ultimaLecturaEn == null) return Optional.of(reportadoEn);
        return Optional.of(reportadoEn.isAfter(ultimaLecturaEn) ? reportadoEn : ultimaLecturaEn);
    }

    /** Los sensores que fallan, en el orden humedad, luz, temperatura. */
    public List<SensorDeMatera> sensoresConFalla() {
        return List.of(SensorDeMatera.values()).stream()
                .filter(s -> sensores.get(s) == EstadoDeSensor.FALLA).toList();
    }

    public SaludDelDispositivo conLectura(Instant ahora) {
        return new SaludDelDispositivo(serial, estado, sensores, detalle, reportadoEn, ahora, cambioEn);
    }

    /** Lo que la matera acaba de reportar, con su sello de cambio ya decidido por quien llama. */
    public SaludDelDispositivo conReporte(EstadoDeSalud nuevoEstado, Map<SensorDeMatera, EstadoDeSensor> nuevosSensores,
                                          String nuevoDetalle, Instant ahora, Instant nuevoCambioEn) {
        return new SaludDelDispositivo(serial, nuevoEstado, nuevosSensores, nuevoDetalle, ahora, ultimaLecturaEn,
                nuevoCambioEn);
    }

    public SaludDelDispositivo sinNoticias(Instant ahora) {
        return new SaludDelDispositivo(serial, EstadoDeSalud.SIN_NOTICIAS, sensores, detalle, reportadoEn,
                ultimaLecturaEn, ahora);
    }

    /** La frase que la app y la OLED dicen en primera persona (D158). */
    public String mensaje(Instant ahora) {
        return switch (estado) {
            case OK -> "todos mis sensores responden";
            case FALLA -> {
                List<SensorDeMatera> falla = sensoresConFalla();
                yield falla.size() == 1 ? "no te siento " + falla.get(0).conArticulo() : "no siento mis sensores";
            }
            case SIN_NOTICIAS -> "no sé de la matera desde hace " + haceCuanto(ahora);
        };
    }

    private String haceCuanto(Instant ahora) {
        Duration silencio = ultimaNoticiaEn().map(t -> Duration.between(t, ahora)).orElse(Duration.ZERO);
        long minutos = Math.max(1, silencio.toMinutes());
        return minutos < 60 ? minutos + " min" : (minutos / 60) + " h";
    }
}
