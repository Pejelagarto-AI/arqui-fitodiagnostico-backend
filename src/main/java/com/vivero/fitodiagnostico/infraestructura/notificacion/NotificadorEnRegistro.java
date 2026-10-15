package com.vivero.fitodiagnostico.infraestructura.notificacion;

import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.puerto.Notificador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Único adaptador de {@link Notificador} en este bloque: escribe el aviso en el
 * registro de la aplicación. Cumple el contrato (el aviso "sale") sin
 * infraestructura de correo ni sockets, y es el que se ve en consola al
 * probar a mano. Cuando lleguen el correo y la vista en vivo serán clases
 * nuevas que implementen el mismo puerto; este adaptador no cambia.
 *
 * <p>Registra ids y tipo, y el texto del aviso (que menciona el apodo de la
 * planta); nunca correos ni nombres de personas.
 */
@Component
public class NotificadorEnRegistro implements Notificador {

    private static final Logger log = LoggerFactory.getLogger(NotificadorEnRegistro.class);

    @Override
    public void avisar(Aviso aviso) {
        log.info("aviso {} para {} destinatario(s) {}: {}",
                aviso.tipo(), aviso.destinatarios().size(), aviso.destinatarios(), aviso.mensaje());
    }
}
