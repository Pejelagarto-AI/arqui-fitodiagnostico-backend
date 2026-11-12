package com.vivero.fitodiagnostico.adaptador.salida.csv;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import com.vivero.fitodiagnostico.dominio.modelo.Rango;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lee la tabla de referencia del Anexo B desde un CSV del classpath y la
 * entrega como objetos de dominio. Ya NO es un adaptador de los puertos de
 * lectura (D14): el CSV son datos semilla que {@link SembradorDeEspecies}
 * copia a la base al arrancar, y la base es la única fuente en ejecución.
 * Por eso esta clase no implementa {@code RangosPorEspecie} ni
 * {@code CatalogoDeEspecies} y no tiene condición de activación.
 *
 * <p>El mapeo de columna por magnitud vive en un único lugar
 * ({@link #PREFIJO_COLUMNA}) y la lectura recorre {@link Magnitud#values()},
 * así que agregar una magnitud nueva no obliga a tocar la lógica de parseo,
 * solo ese mapa y el CSV.
 */
public class LectorDeEspeciesCsv {

    private static final String RUTA_CSV = "referencia/especies.csv";
    private static final String COLUMNA_NOMBRE = "especie";

    private static final Map<Magnitud, String> PREFIJO_COLUMNA = Map.of(
            Magnitud.HUMEDAD, "humedad",
            Magnitud.LUZ, "luz",
            Magnitud.TEMPERATURA, "temp");

    /** Lee el CSV del classpath cada vez que se invoca; falla con un mensaje claro si está mal formado. */
    public List<Especie> leer() {
        return cargarDesdeClasspath();
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
