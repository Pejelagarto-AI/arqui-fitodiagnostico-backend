package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import java.util.UUID;

/**
 * Puerto de entrada que los canales de lecturas (HTTP y MQTT) consultan ANTES de registrar una lectura de un
 * dispositivo. Regla de negocio: una lectura solo se acepta si ese dispositivo SIGUE vinculado a la planta que dice
 * su token. Un token sigue siendo criptográficamente válido hasta una hora después de desvincular la matera; sin esta
 * regla, la matera traspasada a otro usuario seguiría reportando a la planta anterior (D96).
 */
public interface VerificarVinculoPort {

    /** El vínculo que sigue vigente: la planta y el nombre con que su dueño la llama (lo muestra la pantalla de la matera). */
    record VinculoVigente(UUID plantaId, String apodo) { }

    /**
     * @throws com.vivero.fitodiagnostico.dominio.excepcion.CredencialRechazadaException si el dispositivo no
     *         existe, está sin reclamar o está reclamado por otra planta. El chip la ve como 401: renueva su
     *         credencial y recibe el 409 de «sin reclamar» o un token de la planta nueva.
     */
    VinculoVigente exigirVinculo(String serial, UUID plantaId);
}
