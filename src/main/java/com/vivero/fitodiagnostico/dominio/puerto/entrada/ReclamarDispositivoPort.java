package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;

import java.util.List;
import java.util.UUID;

/**
 * Puerto de entrada del DUEÑO: reclama un dispositivo escribiendo su serial, lista los de su planta
 * y los desvincula. Reemplaza a {@code VincularDispositivoPort}: el dueño ya no recibe ni copia ninguna
 * credencial.
 *
 * <p>Solo el dueño (RN5), igual que editar la planta: un invitado recibe
 * {@code OperacionNoPermitidaException} y un ajeno, la misma {@code PlantaNoEncontradaException} que
 * si la planta no existiera.
 */
public interface ReclamarDispositivoPort {

    /**
     * Idempotente: reclamar de nuevo el mismo serial para la misma planta devuelve el mismo dispositivo.
     *
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.DispositivoNoEncontradoException si el serial no existe
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.DispositivoYaReclamadoException si es de otra planta
     */
    Dispositivo reclamar(UUID solicitante, UUID plantaId, String serial);

    List<Dispositivo> listar(UUID solicitante, UUID plantaId);

    /**
     * El dispositivo vuelve a SIN_RECLAMAR y no puede renovar credencial.
     *
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.DispositivoNoEncontradoException si no está en esa planta
     */
    void desvincular(UUID solicitante, UUID plantaId, String serial);
}
