package com.vivero.fitodiagnostico.dominio.puerto.salida;

import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Puerto de persistencia de los accesos compartidos (D19). Sin él, nadie persistiría a los invitados. */
public interface RepositorioAccesos {

    /** Ids de los invitados de la planta; vacío si no se ha compartido. */
    Set<UUID> invitadosDe(UUID plantaId);

    /** Los accesos de la planta con su fecha, el más antiguo primero (el dueño ve a quién le dio acceso y cuándo, RF-E4). */
    List<AccesoCompartido> accesosDe(UUID plantaId);

    /** Idempotente: otorgar dos veces el mismo acceso deja uno solo. */
    void otorgar(AccesoCompartido acceso);

    /** Idempotente: revocar un acceso que no existe no falla. */
    void revocar(UUID plantaId, UUID invitadoId);

    /** Quita a todos los invitados de la planta (parte de eliminarla, RN12). */
    void revocarTodosDe(UUID plantaId);

    /** Quita los accesos que tenía un usuario como invitado (parte de eliminar su cuenta, RN12). */
    void revocarTodosDelInvitado(UUID invitadoId);
}
