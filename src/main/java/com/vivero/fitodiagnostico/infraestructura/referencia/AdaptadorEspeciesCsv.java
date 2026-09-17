package com.vivero.fitodiagnostico.infraestructura.referencia;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;
import com.vivero.fitodiagnostico.dominio.puerto.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.RangosPorEspecie;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adaptador de {@link RangosPorEspecie} y {@link CatalogoDeEspecies} que lee
 * la tabla de referencia de un CSV en el classpath en vez de una base de
 * datos (RA-05): cambiar la fuente de la tabla de referencia es esta clase,
 * no un cambio en el dominio ni en {@code ServicioDiagnostico}.
 *
 * <p>Activo por defecto ({@code fitodiagnostico.tabla-referencia=csv} o
 * ausente); se desactiva con {@code fitodiagnostico.tabla-referencia=bd}.
 *
 * <p>El CSV se lee una sola vez, en el constructor. El mapeo de columna por
 * magnitud vive en un único lugar ({@link #PREFIJO_COLUMNA}) y la lectura
 * recorre {@link Magnitud#values()}, así que agregar una magnitud nueva no
 * obliga a tocar la lógica de parseo, solo ese mapa y el CSV.
 */
@Component
@ConditionalOnProperty(prefix = "fitodiagnostico", name = "tabla-referencia",
        havingValue = "csv", matchIfMissing = true)
public class AdaptadorEspeciesCsv implements RangosPorEspecie, CatalogoDeEspecies {

    private static final String RUTA_CSV = "referencia/especies.csv";
    private static final String COLUMNA_NOMBRE = "especie";

    private static final Map<Magnitud, String> PREFIJO_COLUMNA = Map.of(
            Magnitud.HUMEDAD, "humedad",
            Magnitud.LUZ, "luz",
            Magnitud.TEMPERATURA, "temp");

    private final List<Especie> especiesOrdenadas;
    private final Map<String, Especie> porNombreNormalizado;

    public AdaptadorEspeciesCsv() {
        List<Especie> cargadas = cargarDesdeClasspath();
        this.especiesOrdenadas = cargadas.stream()
                .sorted(Comparator.comparing(Especie::nombre, String.CASE_INSENSITIVE_ORDER))
                .toList();
        this.porNombreNormalizado = especiesOrdenadas.stream()
                .collect(Collectors.toMap(e -> normalizar(e.nombre()), e -> e));
    }

    @Override
    public Optional<Especie> buscar(String nombre) {
        if (nombre == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(porNombreNormalizado.get(normalizar(nombre)));
    }

    @Override
    public List<Especie> listar() {
        return especiesOrdenadas;
    }

    private static String normalizar(String nombre) {
        return nombre.toLowerCase(Locale.ROOT);
    }

    private List<Especie> cargarDesdeClasspath() {
        try (InputStream flujo = getClass().getClassLoader().getResourceAsStream(RUTA_CSV)) {
            if (flujo == null) {
                throw new IllegalStateException(
                    "no se encontró el CSV de referencia en el classpath: " + RUTA_CSV);
            }
            return leer(flujo);
        } catch (IOException e) {
            throw new IllegalStateException("no se pudo leer el CSV de referencia: " + RUTA_CSV, e);
        }
    }

    private List<Especie> leer(InputStream flujo) throws IOException {
        try (BufferedReader lector = new BufferedReader(new InputStreamReader(flujo, StandardCharsets.UTF_8))) {
            String encabezado = lector.readLine();
            if (encabezado == null || encabezado.isBlank()) {
                throw new IllegalStateException("el CSV de referencia está vacío: " + RUTA_CSV);
            }
            Map<String, Integer> indice = indexarColumnas(encabezado);

            List<Especie> especies = new ArrayList<>();
            String linea;
            int numeroDeLinea = 1;
            while ((linea = lector.readLine()) != null) {
                numeroDeLinea++;
                if (linea.isBlank()) {
                    continue;
                }
                especies.add(parsearFila(linea, indice, numeroDeLinea));
            }
            if (especies.isEmpty()) {
                throw new IllegalStateException(
                    "el CSV de referencia no tiene filas de datos: " + RUTA_CSV);
            }
            return especies;
        }
    }

    private Map<String, Integer> indexarColumnas(String encabezado) {
        String[] columnas = encabezado.split(",", -1);
        Map<String, Integer> indice = new HashMap<>();
        for (int i = 0; i < columnas.length; i++) {
            indice.put(columnas[i].trim(), i);
        }

        List<String> requeridas = new ArrayList<>();
        requeridas.add(COLUMNA_NOMBRE);
        for (Magnitud magnitud : Magnitud.values()) {
            String prefijo = PREFIJO_COLUMNA.get(magnitud);
            requeridas.add(prefijo + "_min");
            requeridas.add(prefijo + "_max");
        }
        for (String columna : requeridas) {
            if (!indice.containsKey(columna)) {
                throw new IllegalStateException(
                    "el CSV de referencia no tiene la columna requerida '" + columna + "': " + RUTA_CSV);
            }
        }
        return indice;
    }

    private Especie parsearFila(String linea, Map<String, Integer> indice, int numeroDeLinea) {
        String[] campos = linea.split(",", -1);
        String nombre = valorDe(campos, indice, COLUMNA_NOMBRE, numeroDeLinea).trim();

        Map<Magnitud, Rango> rangos = new EnumMap<>(Magnitud.class);
        for (Magnitud magnitud : Magnitud.values()) {
            String prefijo = PREFIJO_COLUMNA.get(magnitud);
            double minimo = parsearNumero(
                    valorDe(campos, indice, prefijo + "_min", numeroDeLinea), numeroDeLinea, prefijo + "_min");
            double maximo = parsearNumero(
                    valorDe(campos, indice, prefijo + "_max", numeroDeLinea), numeroDeLinea, prefijo + "_max");
            rangos.put(magnitud, new Rango(minimo, maximo));
        }

        try {
            return new Especie(nombre, rangos);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                "fila inválida en el CSV de referencia (línea " + numeroDeLinea + "): " + e.getMessage(), e);
        }
    }

    private String valorDe(String[] campos, Map<String, Integer> indice, String columna, int numeroDeLinea) {
        int posicion = indice.get(columna);
        if (posicion >= campos.length) {
            throw new IllegalStateException(
                "fila incompleta en el CSV de referencia (línea " + numeroDeLinea
                    + "), falta la columna '" + columna + "'");
        }
        return campos[posicion];
    }

    private double parsearNumero(String valor, int numeroDeLinea, String columna) {
        try {
            return Double.parseDouble(valor.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                "valor numérico inválido en el CSV de referencia (línea " + numeroDeLinea
                    + "), columna '" + columna + "': '" + valor + "'", e);
        }
    }
}
