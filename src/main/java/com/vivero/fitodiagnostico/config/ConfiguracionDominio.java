package com.vivero.fitodiagnostico.config;

import com.vivero.fitodiagnostico.dominio.servicio.ClasificadorDeParametros;
import com.vivero.fitodiagnostico.dominio.servicio.DetectorDeCambioDeEstado;
import com.vivero.fitodiagnostico.dominio.servicio.EvaluadorDeEstado;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAdministradores;
import com.vivero.fitodiagnostico.dominio.servicio.RedactorDeRecomendaciones;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaDeAgregacion;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaPorDesviacion;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * El dominio no se anota con @Component: es la composición (este paquete config) la que decide
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

    @Bean
    PoliticaDeAdministradores politicaDeAdministradores() {
        return new PoliticaDeAdministradores();
    }

    @Bean
    PoliticaDeAcceso politicaDeAcceso() {
        return new PoliticaDeAcceso();
    }

    @Bean
    DetectorDeCambioDeEstado detectorDeCambioDeEstado() {
        return new DetectorDeCambioDeEstado();
    }

    /**
     * El reloj de los casos de uso que deciden por la hora (caducidad de invitaciones y de
     * enlaces de restablecimiento). Inyectado y no llamado con {@code Instant.now()} para
     * probarlos con fechas fijas.
     */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
