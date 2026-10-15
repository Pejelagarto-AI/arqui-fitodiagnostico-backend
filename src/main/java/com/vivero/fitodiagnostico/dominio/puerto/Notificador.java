package com.vivero.fitodiagnostico.dominio.puerto;

import com.vivero.fitodiagnostico.dominio.modelo.Aviso;

/**
 * Puerto de salida que AVISA, no persiste. El dominio dice "avisa a estas
 * personas de esto" y no sabe si detrás hay un log, un correo o un WebSocket.
 * No lanza excepciones de negocio: si un canal falla, el aviso se pierde pero
 * la medición que lo originó ya quedó registrada (ver límites en IMPLEMENTACION.md).
 */
public interface Notificador {

    void avisar(Aviso aviso);
}
