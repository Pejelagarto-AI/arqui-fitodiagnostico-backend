package com.vivero.fitodiagnostico.mascota.puerto.salida;

import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.mascota.modelo.EventoDeMascota;

import java.util.UUID;

/** La línea de tiempo (tabla {@code evento_mascota}, V17). Solo se agrega: un evento no se edita. */
public interface RepositorioEventosDeMascota {

    void guardar(EventoDeMascota evento);

    /** Del más reciente al más antiguo por {@code ocurridoEn}. */
    Pagina<EventoDeMascota> de(UUID plantaId, Paginacion paginacion);
}
