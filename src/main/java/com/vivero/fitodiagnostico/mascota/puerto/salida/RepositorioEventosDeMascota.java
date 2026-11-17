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

    /**
     * Los eventos con {@code registradoEn} posterior a {@code despuesDe} (fase 13, D180): los últimos {@code maximo},
     * del más antiguo al más reciente por hora de registro. Es la hora en que el servidor los anotó, no {@code ocurridoEn}:
     * una REGADA ocurre a la hora de la lectura que mostró el salto, pero la matera la necesita cuando se anota.
     */
    java.util.List<EventoDeMascota> registradosDespuesDe(UUID plantaId, java.time.Instant despuesDe, int maximo);

    /** Del más reciente al más antiguo por {@code ocurridoEn}. */
    Pagina<EventoDeMascota> de(UUID plantaId, Paginacion paginacion);
}
