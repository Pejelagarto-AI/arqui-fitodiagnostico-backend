package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Siembra lecturas del dispositivo directo por JDBC (con 17 000 por día, pasar por el adaptador tardaría minutos). Cada
 * lectura trae solo el parámetro de luz: es lo que leen el resumen por hora y la serie. {@code luxEn} devuelve
 * {@code null} para saltarse una lectura (la matera apagada).
 */
final class SiembraDeLecturas {

    private SiembraDeLecturas() { }

    static int sembrar(JdbcTemplate jdbc, UUID plantaId, String especie, Instant desde, Instant hasta,
                       long cadaSegundos, Function<Instant, Double> luxEn) {
        List<Object[]> cabeceras = new ArrayList<>();
        List<Object[]> parametros = new ArrayList<>();
        for (Instant t = desde; t.isBefore(hasta); t = t.plusSeconds(cadaSegundos)) {
            Double lux = luxEn.apply(t);
            if (lux == null) {
                continue;
            }
            UUID id = UUID.randomUUID();
            Timestamp ts = Timestamp.from(t);
            cabeceras.add(new Object[]{id, plantaId, "DISPOSITIVO", ts, ts, especie, "SALUDABLE", ts});
            parametros.add(new Object[]{id, "LUZ", lux, 500.0, 1000.0, "OPTIMO"});
        }
        jdbc.batchUpdate("insert into diagnostico_planta (id, planta_id, origen, registrado_en, evaluado_en, "
                + "especie_nombre, estado, tomada_en) values (?,?,?,?,?,?,?,?)", cabeceras);
        jdbc.batchUpdate("insert into diagnostico_parametro (diagnostico_id, magnitud, valor, rango_minimo, "
                + "rango_maximo, clasificacion) values (?,?,?,?,?,?)", parametros);
        return cabeceras.size();
    }
}
