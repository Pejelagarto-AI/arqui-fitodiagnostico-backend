package com.vivero.fitodiagnostico.config;

import com.vivero.fitodiagnostico.aplicacion.ServicioSalud;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioDispositivos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioPlantas;
import com.vivero.fitodiagnostico.salud.AvisadorDeSalud;
import com.vivero.fitodiagnostico.salud.RepositorioDeSalud;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;

/**
 * Arma la salud de la matera (fase 10) y enciende el reloj que la vigila. Los tiempos se leen aquí y el servicio
 * recibe {@link Duration}: la aplicación no sabe de variables de entorno.
 *
 * <ul>
 *   <li>{@code FITO_SALUD_INTERVALO_ESPERADO} cada cuánto reporta lecturas la matera (por defecto PT5S, la demo).</li>
 *   <li>{@code FITO_SALUD_SILENCIO_MINIMO} suelo del silencio tolerado (por defecto PT2M).</li>
 *   <li>{@code FITO_SALUD_LATIDO_DE_FALLA} cada cuánto reporta la matera mientras un sensor falla (por defecto PT5M;
 *       el firmware manda ese latido).</li>
 * </ul>
 */
@Configuration
@EnableScheduling
public class ConfiguracionSalud {

    @Bean
    ServicioSalud servicioSalud(RepositorioDeSalud salud, RepositorioDispositivos dispositivos,
                                RepositorioPlantas plantas, RepositorioAccesos accesos, AvisadorDeSalud avisador,
                                Clock reloj,
                                @Value("${fitodiagnostico.salud.intervalo-esperado:PT5S}") Duration intervalo,
                                @Value("${fitodiagnostico.salud.silencio-minimo:PT2M}") Duration silencioMinimo,
                                @Value("${fitodiagnostico.salud.latido-de-falla:PT5M}") Duration latido) {
        return new ServicioSalud(salud, dispositivos, plantas, accesos, avisador, reloj, intervalo,
                silencioMinimo, latido);
    }
}
