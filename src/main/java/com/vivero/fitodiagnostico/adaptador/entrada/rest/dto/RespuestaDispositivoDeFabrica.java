package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.puerto.entrada.RegistrarDispositivoDeFabricaPort.DispositivoDeFabrica;

import java.time.Instant;

/** El sticker: serial y secreto. El secreto solo se entrega aquí, una vez; no hay forma de volver a verlo. */
public record RespuestaDispositivoDeFabrica(String serial, String secreto, Instant creadoEn) {

    public static RespuestaDispositivoDeFabrica desde(DispositivoDeFabrica d) {
        return new RespuestaDispositivoDeFabrica(d.serial(), d.secreto(), d.creadoEn());
    }
}
