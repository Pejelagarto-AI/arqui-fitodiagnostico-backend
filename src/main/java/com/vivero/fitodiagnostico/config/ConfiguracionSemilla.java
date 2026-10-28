package com.vivero.fitodiagnostico.config;

import com.vivero.fitodiagnostico.adaptador.salida.csv.LectorDeEspeciesCsv;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** El lector del CSV no lleva anotación de Spring: es la composición quien lo ensambla. */
@Configuration
public class ConfiguracionSemilla {

    @Bean
    LectorDeEspeciesCsv lectorDeEspeciesCsv() {
        return new LectorDeEspeciesCsv();
    }
}
