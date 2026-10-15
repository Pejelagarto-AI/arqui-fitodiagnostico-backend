package com.vivero.fitodiagnostico.infraestructura.notificacion;

import com.vivero.fitodiagnostico.dominio.puerto.Notificador;
import com.vivero.fitodiagnostico.infraestructura.notificacion.NotificadorCompuesto.Canal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;

/**
 * Arma la cadena de notificación (D57, D58): los tres adaptadores de canal,
 * el compuesto que reparte entre ellos y, por encima, el decorador que retiene
 * todo hasta el commit. Los servicios piden un {@link Notificador} y reciben
 * el decorador porque es el {@code @Primary}; el resto de beans del mismo tipo
 * existen pero nadie los inyecta por tipo.
 *
 * <pre>
 *   servicio ──▶ NotificadorTrasCommit ──(commit)──▶ NotificadorCompuesto ──▶ registro | correo | vivo
 * </pre>
 */
@Configuration
public class ConfiguracionNotificacion {

    @Bean
    NotificadorCompuesto notificadorCompuesto(NotificadorEnRegistro registro, NotificadorPorCorreo correo,
                                              NotificadorPorWebSocket vivo) {
        return new NotificadorCompuesto(Map.of(Canal.REGISTRO, registro, Canal.CORREO, correo, Canal.VIVO, vivo));
    }

    @Bean
    @Primary
    Notificador notificadorTrasCommit(NotificadorCompuesto compuesto) {
        return new NotificadorTrasCommit(compuesto);
    }
}
