package com.vivero.fitodiagnostico.config;

import com.vivero.fitodiagnostico.dominio.servicio.ClasificadorDeParametros;
import com.vivero.fitodiagnostico.dominio.servicio.DetectorDeCambioDeEstado;
import com.vivero.fitodiagnostico.dominio.servicio.EvaluadorDeEstado;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAcceso;
import com.vivero.fitodiagnostico.dominio.servicio.PoliticaDeAdministradores;
import com.vivero.fitodiagnostico.dominio.servicio.RedactorDeRecomendaciones;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaDeAgregacion;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaDeHumedadTrasRiego;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaDeLuzAcumulada;
import com.vivero.fitodiagnostico.dominio.servicio.ReglaPorDesviacion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;

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

    /**
     * La regla de luz con su franja de día (fase 16, D205 a D207). El dominio no lee configuración:
     * la zona y las horas llegan por aquí. Por defecto 06:00 a 18:00 en America/Bogota.
     */
    @Bean
    ReglaDeLuzAcumulada reglaDeLuzAcumulada(
            @Value("${fitodiagnostico.zona-horaria:America/Bogota}") String zonaHoraria,
            @Value("${fitodiagnostico.dia.inicio:06:00}") String inicioDelDia,
            @Value("${fitodiagnostico.dia.fin:18:00}") String finDelDia) {
        return new ReglaDeLuzAcumulada(ZoneId.of(zonaHoraria), LocalTime.parse(inicioDelDia), LocalTime.parse(finDelDia));
    }

    @Bean
    EvaluadorDeEstado evaluadorDeEstado(ClasificadorDeParametros clasificadorDeParametros,
                                         ReglaDeAgregacion reglaDeAgregacion,
                                         RedactorDeRecomendaciones redactorDeRecomendaciones,
                                         ReglaDeLuzAcumulada reglaDeLuzAcumulada) {
        return new EvaluadorDeEstado(clasificadorDeParametros, reglaDeAgregacion, redactorDeRecomendaciones,
                reglaDeLuzAcumulada, new ReglaDeHumedadTrasRiego());
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
