package com.vivero.fitodiagnostico.infraestructura.configuracion;

import com.vivero.fitodiagnostico.dominio.estado.*;
import com.vivero.fitodiagnostico.dominio.servicio.EvaluadorDeEstado;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

/**
 * El dominio no se anota con @Component: es la infraestructura la que decide
 * cómo se ensambla. El ORDEN de esta lista ES la política de prioridad.
 */
@Configuration
public class ConfiguracionDominio {

    @Bean
    EvaluadorDeEstado evaluadorDeEstado() {
        return new EvaluadorDeEstado(List.of(
                new EstadoNecesitaAbrigo(),
                new EstadoNecesitaAgua(),
                new EstadoNecesitaLuz(),
                new EstadoOptimo()));
    }
}
