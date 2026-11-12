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
 * Solo en modo {@code csv}: copia el CSV a la tabla {@code especie} en CADA arranque.
 *
 * <p>Por qué existe: la planta guarda una clave foránea a {@code especie} (V6) y el
 * historial de plantas se lee con los rangos de esa fila. Si la fuente es el CSV, la
 * tabla sigue haciendo falta como ESPEJO para que esa clave tenga a dónde apuntar; el
 * CSV manda y el espejo se reescribe, así que editar el CSV y reiniciar cambia los
 * rangos de todo (el diagnóstico suelto y las plantas). Solo ve los puertos: no sabe
 * que detrás del {@code espejo} hay JPA.
 */
@Order(1)
public class EspejoDeEspecies implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EspejoDeEspecies.class);

    private final CatalogoDeEspecies fuente;
    private final CatalogoEditable espejo;

    public EspejoDeEspecies(CatalogoDeEspecies fuente, CatalogoEditable espejo) {
        this.fuente = fuente;
        this.espejo = espejo;
    }

    @Override
    public void run(ApplicationArguments args) {
        sincronizar();
    }

    /** @return cuántas especies se copiaron */
    public int sincronizar() {
        List<Especie> especies = fuente.listar();
        especies.forEach(espejo::guardar);
        log.info("fuente csv: se sincronizaron {} especies con la tabla espejo", especies.size());
        return especies.size();
    }
}
