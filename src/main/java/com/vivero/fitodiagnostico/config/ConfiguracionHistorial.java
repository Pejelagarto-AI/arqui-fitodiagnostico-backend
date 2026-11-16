package com.vivero.fitodiagnostico.config;

import com.vivero.fitodiagnostico.adaptador.entrada.programado.RetencionDeLecturas;
import com.vivero.fitodiagnostico.aplicacion.ServicioDeRetencion;
import com.vivero.fitodiagnostico.historial.AlmacenDeHistorial;
import com.vivero.fitodiagnostico.historial.PodarHistorialPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;

/**
 * La retención de las lecturas crudas (fase 12, D176). Valores por defecto: crudas 30 días, resumen por hora para
 * siempre, una pasada diaria a las 3:30.
 *
 * <ul>
 *   <li>{@code FITO_HISTORIAL_RETENCION_CRUDAS} cuánto se guardan las lecturas crudas, ISO-8601 (por defecto P30D;
 *       mínimo PT48H).</li>
 *   <li>{@code FITO_HISTORIAL_RETENCION_HABILITADA} {@code false} apaga la tarea diaria (por defecto true; el perfil de
 *       pruebas la apaga). Apagada, el resumen por hora sigue funcionando: solo no se borra nada.</li>
 *   <li>{@code FITO_HISTORIAL_RETENCION_CRON} cuándo corre (por defecto {@code 0 30 3 * * *}).</li>
 * </ul>
 */
@Configuration
public class ConfiguracionHistorial {

    @Bean
    PodarHistorialPort servicioDeRetencion(AlmacenDeHistorial almacen, Clock reloj,
                                           @Value("${fitodiagnostico.historial.retencion.crudas:P30D}") Duration crudas) {
        return new ServicioDeRetencion(almacen, reloj, crudas);
    }

    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "fitodiagnostico.historial.retencion", name = "habilitada", havingValue = "true",
            matchIfMissing = true)
    static class Tarea {

        @Bean
        RetencionDeLecturas retencionDeLecturas(PodarHistorialPort historial) {
            return new RetencionDeLecturas(historial);
        }
    }
}
