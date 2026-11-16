package com.vivero.fitodiagnostico.mascota.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.mascota.modelo.EventoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.VistaDeMascota;

import java.util.UUID;

/** Las dos lecturas de la mascota. Dueño e invitados; un ajeno recibe el mismo 404 que si no existiera. */
public interface ConsultarMascotaPort {

    VistaDeMascota consultar(UUID solicitante, UUID plantaId);

    /** La línea de tiempo, del más reciente al más antiguo. */
    Pagina<EventoDeMascota> eventos(UUID solicitante, UUID plantaId, Paginacion paginacion);
}
