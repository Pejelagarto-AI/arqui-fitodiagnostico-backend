package com.vivero.fitodiagnostico.adaptador.salida.csv;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoDeEspecies;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoEditable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * Carga el CSV de referencia a la base al arrancar, solo si el catálogo está
 * vacío (D14; modo {@code bd}). Lo arma {@code config.ConfiguracionCatalogo}, no el escaneo. Habla con los puertos, no con JPA: así la misma clase siembra
 * cualquier adaptador y se prueba con un doble en memoria.
 *
 * <p>"Solo si está vacío" es lo que lo hace seguro de ejecutar en cada
 * arranque: una vez que el administrador edita o desactiva especies, el CSV
 * nunca vuelve a pisar sus cambios.
 */
@Order(1)
public class SembradorDeEspecies implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SembradorDeEspecies.class);

    private final CatalogoDeEspecies catalogo;
    private final CatalogoEditable catalogoEditable;
    private final LectorDeEspeciesCsv lector;

    public SembradorDeEspecies(CatalogoDeEspecies catalogo, CatalogoEditable catalogoEditable,
                               LectorDeEspeciesCsv lector) {
        this.catalogo = catalogo;
        this.catalogoEditable = catalogoEditable;
        this.lector = lector;
    }

    @Override
    public void run(ApplicationArguments args) {
        sembrarSiVacio();
    }

    /** @return cuántas especies se cargaron (0 si el catálogo ya tenía datos) */
    public int sembrarSiVacio() {
        if (!catalogo.listar().isEmpty()) {
            return 0;
        }
        List<Especie> semilla = lector.leer();
        semilla.forEach(catalogoEditable::guardar);
        log.info("catálogo vacío: se cargaron {} especies desde el CSV semilla", semilla.size());
        return semilla.size();
    }
}
