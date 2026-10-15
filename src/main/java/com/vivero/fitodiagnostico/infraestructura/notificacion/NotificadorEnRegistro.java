package com.vivero.fitodiagnostico.infraestructura.notificacion;

import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.puerto.Notificador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adaptador de {@link Notificador} que escribe el aviso en el registro de la
 * aplicación: es el que se ve en consola al probar a mano. Desde el bloque 4
 * es uno de tres canales ({@code NotificadorCompuesto} reparte entre este, el
 * correo y la vista en vivo); este adaptador no cambió.
 *
 * <p>Registra ids y tipo, y el texto del aviso (que menciona el apodo de la
 * planta); nunca correos, nombres de personas ni el token de un enlace (viaja
 * en {@link Aviso#datos()}, que aquí no se imprime).
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
