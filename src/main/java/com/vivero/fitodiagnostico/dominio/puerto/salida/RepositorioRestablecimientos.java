package com.vivero.fitodiagnostico.dominio.puerto.salida;

import com.vivero.fitodiagnostico.dominio.modelo.SolicitudRestablecimiento;

import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de las solicitudes de restablecimiento de contraseña (RF-A7, RF-A8). */
public interface RepositorioRestablecimientos {

    /** Crea o reemplaza (por id). */
    void guardar(SolicitudRestablecimiento solicitud);

    Optional<SolicitudRestablecimiento> porHuella(String huellaDelToken);

    /** Parte de eliminar la cuenta (D37, D56). */
    void eliminarDe(UUID usuarioId);
}
