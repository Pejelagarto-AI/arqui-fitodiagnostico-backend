package com.vivero.fitodiagnostico.dominio.puerto.salida;

import com.vivero.fitodiagnostico.dominio.modelo.Planta;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de plantas. No decide permisos: eso es {@code PoliticaDeAcceso}. */
public interface RepositorioPlantas {

    Optional<Planta> buscar(UUID id);

    /** Las plantas de las que {@code duenoId} es dueño, la más reciente primero (RF-D2, RF-B2). */
    List<Planta> deDueno(UUID duenoId);

    /** Las plantas a las que {@code usuarioId} fue invitado, la más reciente primero (RF-E5). */
    List<Planta> compartidasCon(UUID usuarioId);

    /** Crea o reemplaza (por id). */
    void guardar(Planta planta);

    /**
     * Elimina SOLO la planta. El historial y los accesos se eliminan antes con
     * sus propios puertos (RN12): la base los protege con claves foráneas sin
     * cascada, así que olvidar ese paso falla ruidosamente en vez de dejar
     * huérfanos.
     */
    void eliminar(UUID id);
}
