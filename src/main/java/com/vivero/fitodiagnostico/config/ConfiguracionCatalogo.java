package com.vivero.fitodiagnostico.config;

import com.vivero.fitodiagnostico.adaptador.salida.csv.AdaptadorEspecieCsv;
import com.vivero.fitodiagnostico.adaptador.salida.csv.CatalogoCsvSoloLectura;
import com.vivero.fitodiagnostico.adaptador.salida.csv.EspejoDeEspecies;
import com.vivero.fitodiagnostico.adaptador.salida.csv.LectorDeEspeciesCsv;
import com.vivero.fitodiagnostico.adaptador.salida.csv.SembradorDeEspecies;
import com.vivero.fitodiagnostico.adaptador.salida.jpa.AdaptadorEspecieJpa;
import com.vivero.fitodiagnostico.adaptador.salida.jpa.EspecieJpaRepository;
import com.vivero.fitodiagnostico.adaptador.salida.jpa.MapeadorEspecie;
import com.vivero.fitodiagnostico.dominio.puerto.salida.CatalogoEditable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EL punto donde se elige la fuente de la tabla de referencia, y el único: con
 * {@code fitodiagnostico.catalogo.fuente} (variable {@code FITO_CATALOGO_FUENTE}).
 * Ni el dominio, ni la aplicación, ni los controladores saben cuál está activa.
 *
 * <ul>
 *   <li><b>bd</b> (por defecto): el adaptador JPA implementa los tres puertos del
 *       catálogo (lectura y escritura). El CSV solo siembra la tabla si está vacía (D14).</li>
 *   <li><b>csv</b>: {@link AdaptadorEspecieCsv} implementa los puertos de lectura y
 *       {@link CatalogoCsvSoloLectura} el de escritura (responde 409). La tabla
 *       {@code especie} sigue existiendo como espejo del CSV por la clave foránea de las
 *       plantas; el espejo NO es candidato a inyección, solo lo usa {@link EspejoDeEspecies}.</li>
 * </ul>
 */
@Configuration
public class ConfiguracionCatalogo {

    public static final String PROPIEDAD = "fitodiagnostico.catalogo.fuente";

    @Configuration
    @ConditionalOnProperty(name = PROPIEDAD, havingValue = "bd", matchIfMissing = true)
    static class CatalogoEnBaseDeDatos {

        @Bean
        AdaptadorEspecieJpa adaptadorEspecieJpa(EspecieJpaRepository jpa, MapeadorEspecie mapeador) {
            return new AdaptadorEspecieJpa(jpa, mapeador);
        }

        @Bean
        SembradorDeEspecies sembradorDeEspecies(AdaptadorEspecieJpa catalogo, LectorDeEspeciesCsv lector) {
            return new SembradorDeEspecies(catalogo, catalogo, lector);
        }
    }

    @Configuration
    @ConditionalOnProperty(name = PROPIEDAD, havingValue = "csv")
    static class CatalogoEnCsv {

        @Bean
        AdaptadorEspecieCsv adaptadorEspecieCsv(LectorDeEspeciesCsv lector) {
            return new AdaptadorEspecieCsv(lector);
        }

        @Bean
        CatalogoEditable catalogoSoloLectura() {
            return new CatalogoCsvSoloLectura();
        }

        /** Espejo para la clave foránea de las plantas: fuera de la inyección por tipo a propósito. */
        @Bean(autowireCandidate = false)
        AdaptadorEspecieJpa espejoJpa(EspecieJpaRepository jpa, MapeadorEspecie mapeador) {
            return new AdaptadorEspecieJpa(jpa, mapeador);
        }

        @Bean
        EspejoDeEspecies espejoDeEspecies(AdaptadorEspecieCsv fuente, EspecieJpaRepository jpa,
                                          MapeadorEspecie mapeador) {
            return new EspejoDeEspecies(fuente, espejoJpa(jpa, mapeador));
        }
    }
}
