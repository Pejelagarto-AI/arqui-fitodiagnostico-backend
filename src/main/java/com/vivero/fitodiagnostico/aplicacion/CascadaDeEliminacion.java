package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.puerto.Notificador;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioAccesos;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioDiagnosticos;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioPlantas;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;

/**
 * La cascada de la RN12 y la D11 en un solo sitio. Eliminar una planta (RF-D5)
 * y eliminar una cuenta (RF-A6, RF-B4) terminan en lo mismo: se borra el
 * historial, se quitan los accesos, se borra la planta y se avisa a los
 * invitados que la perdieron (RF-G3). Son tres caminos y dos servicios; si la
 * secuencia viviera en cada uno, el próximo camino sería el que se olvida de
 * avisar. Mismo argumento de D18.
 *
 * <p>El orden importa: la lista de invitados se lee ANTES de quitar los accesos,
 * y el aviso sale al final, cuando ya no hay nada que pueda fallar. Corre dentro
 * de la transacción del servicio que la llama.
 */
@Component
public class CascadaDeEliminacion {

    private final RepositorioPlantas plantas;
    private final RepositorioDiagnosticos diagnosticos;
    private final RepositorioAccesos accesos;
    private final Notificador notificador;

    public CascadaDeEliminacion(RepositorioPlantas plantas, RepositorioDiagnosticos diagnosticos,
                                RepositorioAccesos accesos, Notificador notificador) {
        this.plantas = plantas;
        this.diagnosticos = diagnosticos;
        this.accesos = accesos;
        this.notificador = notificador;
    }

    /** RN12: historial, accesos y planta; después el aviso ACCESO_PERDIDO a los invitados, si había. */
    public void eliminarPlanta(Planta planta) {
        Set<UUID> invitados = accesos.invitadosDe(planta.id());
        diagnosticos.eliminarDe(planta.id());
        accesos.revocarTodosDe(planta.id());
        plantas.eliminar(planta.id());
        if (!invitados.isEmpty()) {
            notificador.avisar(new Aviso(invitados, TipoDeAviso.ACCESO_PERDIDO,
                    "Perdiste el acceso a la planta «" + planta.apodo() + "» porque su dueño la eliminó."));
        }
    }

    /**
     * Todo lo que un usuario deja atrás al darse de baja: sus plantas con su
     * cascada, y los accesos que él tenía como invitado en plantas ajenas.
     * No elimina al usuario (eso, con la RN4 delante, es del servicio).
     */
    public void eliminarLoDe(UUID usuarioId) {
        for (Planta planta : plantas.deDueno(usuarioId)) {
            eliminarPlanta(planta);
        }
        accesos.revocarTodosDelInvitado(usuarioId);
    }
}
