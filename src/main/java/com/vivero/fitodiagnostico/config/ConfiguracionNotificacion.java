package com.vivero.fitodiagnostico.config;

import com.vivero.fitodiagnostico.adaptador.salida.notificacion.NotificadorCompuesto;
import com.vivero.fitodiagnostico.adaptador.salida.notificacion.NotificadorEnRegistro;
import com.vivero.fitodiagnostico.adaptador.salida.notificacion.NotificadorPorCorreo;
import com.vivero.fitodiagnostico.adaptador.salida.notificacion.NotificadorPorWebSocket;
import com.vivero.fitodiagnostico.adaptador.salida.notificacion.NotificadorTrasCommit;
import com.vivero.fitodiagnostico.dominio.puerto.salida.Notificador;
import com.vivero.fitodiagnostico.adaptador.salida.notificacion.NotificadorCompuesto.Canal;
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
