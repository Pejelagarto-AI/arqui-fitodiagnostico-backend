package com.vivero.fitodiagnostico.mascota.puerto.salida;

import com.vivero.fitodiagnostico.mascota.modelo.AvisoDeMascota;

/**
 * Empuja un evento de la mascota hacia afuera. Detrás hay dos canales (D142): la vista en vivo del front (STOMP,
 * por persona) y la matera (MQTT, por serial). La mascota no sabe cuáles ni cuántos.
 */
public interface PublicadorDeMascota {

    void publicar(AvisoDeMascota aviso);

    /** Algo cambió que no es un evento (fase 13, D184: el accesorio). Por defecto no hace nada: no todo canal lo necesita. */
    default void publicarActualizacion(com.vivero.fitodiagnostico.mascota.modelo.ActualizacionDeMascota cambio) { }
}
