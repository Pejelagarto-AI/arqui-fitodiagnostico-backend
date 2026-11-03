package com.vivero.fitodiagnostico.dominio.puerto.salida;

import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de los dispositivos de fábrica, reclamados o no. */
public interface RepositorioDispositivos {

    /** Crea o reemplaza (por serial). */
    void guardar(Dispositivo dispositivo);

    Optional<Dispositivo> porSerial(String serial);

    /** Los reclamados por la planta, el más antiguo primero. */
    List<Dispositivo> porPlanta(UUID plantaId);

    /** Todos, el más antiguo primero. */
    List<Dispositivo> todos();
}
