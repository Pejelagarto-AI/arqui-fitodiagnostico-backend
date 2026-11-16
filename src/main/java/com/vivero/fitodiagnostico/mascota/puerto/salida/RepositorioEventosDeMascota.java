package com.vivero.fitodiagnostico.mascota.puerto.salida;

import com.vivero.fitodiagnostico.dominio.modelo.Pagina;
import com.vivero.fitodiagnostico.dominio.modelo.Paginacion;
import com.vivero.fitodiagnostico.mascota.modelo.EventoDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeEvento;

import java.util.Optional;
import java.util.UUID;

/** La línea de tiempo (tabla {@code evento_mascota}, V17). Solo se agrega: un evento no se edita. */
public interface RepositorioEventosDeMascota {

    void guardar(EventoDeMascota evento);

    /** El más reciente de ese tipo, o vacío (fase 12: la última caricia, para el enfriamiento). */
    Optional<EventoDeMascota> ultimoDeTipo(UUID plantaId, TipoDeEvento tipo);

    /** Del más reciente al más antiguo por {@code ocurridoEn}. */
    Pagina<EventoDeMascota> de(UUID plantaId, Paginacion paginacion);
}
