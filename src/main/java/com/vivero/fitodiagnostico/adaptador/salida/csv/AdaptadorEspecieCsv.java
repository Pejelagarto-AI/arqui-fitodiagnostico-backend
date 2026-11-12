package com.vivero.fitodiagnostico.adaptador.salida.csv;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RangosPorEspecie;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adaptador de LECTURA de la tabla de referencia sobre el CSV del Anexo B
 * (modo {@code fitodiagnostico.catalogo.fuente=csv}). Implementa los dos
 * puertos de lectura que usa el dominio ({@link RangosPorEspecie} y
 * {@link CatalogoDeEspecies}) y NO el de escritura: un CSV empaquetado en el
 * jar no se edita en caliente (ver {@link CatalogoCsvSoloLectura}).
 *
 * <p>Carga el archivo una vez al construirse: si está mal formado la aplicación
 * no arranca, en lugar de fallar en la primera petición. Iguala el comportamiento
 * del adaptador JPA: búsqueda sin distinguir mayúsculas y listado por nombre.
 */
public class AdaptadorEspecieCsv implements RangosPorEspecie, CatalogoDeEspecies {

    private final Map<String, Especie> porNombre;

    public AdaptadorEspecieCsv(LectorDeEspeciesCsv lector) {
        this.porNombre = lector.leer().stream()
                .collect(Collectors.toUnmodifiableMap(e -> clave(e.nombre()), Function.identity()));
    }

    @Override
    public Optional<Especie> buscar(String nombre) {
        return nombre == null ? Optional.empty() : Optional.ofNullable(porNombre.get(clave(nombre)));
    }

    @Override
    public List<Especie> listar() {
        return porNombre.values().stream().sorted(Comparator.comparing(Especie::nombre)).toList();
    }

    private static String clave(String nombre) {
        return nombre.trim().toLowerCase(Locale.ROOT);
    }
}
