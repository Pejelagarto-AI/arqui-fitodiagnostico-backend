package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de entrada de la "fábrica": un administrador da de alta un dispositivo y obtiene el par
 * serial + secreto que se imprime en el sticker y se graba en el chip. El secreto se entrega UNA vez.
 */
public interface RegistrarDispositivoDeFabricaPort {

    /** Lo que sale de fábrica. Es la única vez que el secreto viaja en una respuesta. */
    record DispositivoDeFabrica(String serial, String secreto, Instant creadoEn) { }

    /**
     * Si {@code serial} viene vacío, el servidor genera uno ({@code fito-XXXXXX}).
     *
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.DispositivoYaExisteException si el serial está tomado
     */
    DispositivoDeFabrica registrar(UUID administrador, Optional<String> serial);

    /** Todos los dispositivos, reclamados o no, para el panel del administrador. */
    List<Dispositivo> listar(UUID administrador);
}
