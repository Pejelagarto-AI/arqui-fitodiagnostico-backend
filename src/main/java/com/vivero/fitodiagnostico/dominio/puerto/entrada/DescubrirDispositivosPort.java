package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Puerto de entrada de la PERSONA que está vinculando una matera: ¿cuáles hay cerca? "Cerca" es: sin dueño, con
 * contacto con el servidor en los últimos {@link #VENTANA_DE_CERCANIA} y llegando desde la misma red que quien
 * pregunta ({@code MismaRed}). Hallarla no la reclama: para eso hace falta el serial completo, que solo está en la
 * pantalla de la matera.
 */
public interface DescubrirDispositivosPort {

    Duration VENTANA_DE_CERCANIA = Duration.ofSeconds(30);

    /** Lo que se le cuenta al usuario de una matera cercana: el dispositivo y hace cuánto se la oyó. */
    record Cercano(Dispositivo dispositivo, long vistoHaceSegundos) { }

    /** El más reciente primero. {@code ipDelSolicitante} es la dirección desde la que llega la petición. */
    List<Cercano> cercanos(UUID usuario, String ipDelSolicitante);
}
