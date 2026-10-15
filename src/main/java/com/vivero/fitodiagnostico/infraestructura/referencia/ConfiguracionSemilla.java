package com.vivero.fitodiagnostico.infraestructura.referencia;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** El lector del CSV no lleva anotación de Spring: es la infraestructura quien lo ensambla. */
@Configuration
class ConfiguracionSemilla {

    @Bean
    LectorDeEspeciesCsv lectorDeEspeciesCsv() {
        return new LectorDeEspeciesCsv();
    }
}
