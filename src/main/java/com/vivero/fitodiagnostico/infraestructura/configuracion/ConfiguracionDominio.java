package com.vivero.fitodiagnostico.infraestructura.configuracion;

import com.vivero.fitodiagnostico.dominio.servicio.ClasificadorDeParametros;
import com.vivero.fitodiagnostico.dominio.servicio.EvaluadorDeEstado;
import com.vivero.fitodiagnostico.dominio.servicio.RedactorDeRecomendaciones;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaDeAgregacion;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaPorDesviacion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El dominio no se anota con @Component: es la infraestructura la que decide
 * cómo se ensambla. El umbral de la regla de agregación queda visible aquí,
 * en un único lugar.
 */
@Configuration
public class ConfiguracionDominio {

    @Bean
    ClasificadorDeParametros clasificadorDeParametros() {
        return new ClasificadorDeParametros();
    }

    @Bean
    ReglaDeAgregacion reglaDeAgregacion() {
        return new ReglaPorDesviacion(ReglaPorDesviacion.UMBRAL_POR_DEFECTO);
    }

    @Bean
    RedactorDeRecomendaciones redactorDeRecomendaciones() {
        return new RedactorDeRecomendaciones();
    }

    @Bean
    EvaluadorDeEstado evaluadorDeEstado(ClasificadorDeParametros clasificadorDeParametros,
                                         ReglaDeAgregacion reglaDeAgregacion,
                                         RedactorDeRecomendaciones redactorDeRecomendaciones) {
        return new EvaluadorDeEstado(clasificadorDeParametros, reglaDeAgregacion, redactorDeRecomendaciones);
    }
}
