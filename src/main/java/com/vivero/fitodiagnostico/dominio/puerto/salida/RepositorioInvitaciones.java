package com.vivero.fitodiagnostico.dominio.puerto.salida;

import com.vivero.fitodiagnostico.dominio.modelo.Invitacion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de las invitaciones (RF-E1..E3). Busca por huella: el token en claro no se guarda (D51). */
public interface RepositorioInvitaciones {

    /** Crea o reemplaza (por id). */
    void guardar(Invitacion invitacion);

    Optional<Invitacion> porHuella(String huellaDelToken);

    Optional<Invitacion> buscar(UUID id);

    /** Las que la base tiene PENDIENTES para la planta, la más reciente primero; el servicio descarta las caducadas. */
    List<Invitacion> pendientesDe(UUID plantaId);

    /** Parte de eliminar la planta (RN12, D37): no deja invitaciones colgando de una planta que ya no existe. */
    void eliminarDe(UUID plantaId);
}
