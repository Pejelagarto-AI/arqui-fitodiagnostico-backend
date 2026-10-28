package com.vivero.fitodiagnostico.aplicacion;

import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.puerto.salida.Notificador;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioDiagnosticos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioInvitaciones;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioPlantas;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioRestablecimientos;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioSolicitudes;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La cascada de la RN12 y la D11 en un solo sitio. Eliminar una planta (RF-D5)
 * y eliminar una cuenta (RF-A6, RF-B4) terminan en lo mismo: se borra el
 * historial, se quitan los accesos y las invitaciones pendientes, se borra la
 * planta y se avisa a los invitados que la perdieron (RF-G3). Son tres caminos y dos servicios; si la
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
    private final RepositorioInvitaciones invitaciones;
    private final RepositorioSolicitudes solicitudes;
    private final RepositorioRestablecimientos restablecimientos;
    private final Notificador notificador;

    public CascadaDeEliminacion(RepositorioPlantas plantas, RepositorioDiagnosticos diagnosticos,
                                RepositorioAccesos accesos, RepositorioInvitaciones invitaciones,
                                RepositorioSolicitudes solicitudes,
                                RepositorioRestablecimientos restablecimientos, Notificador notificador) {
        this.plantas = plantas;
        this.diagnosticos = diagnosticos;
        this.accesos = accesos;
        this.invitaciones = invitaciones;
        this.solicitudes = solicitudes;
        this.restablecimientos = restablecimientos;
        this.notificador = notificador;
    }

    /**
     * RN12: historial, accesos, invitaciones pendientes y planta; después el aviso ACCESO_PERDIDO
     * a los invitados, si había (D56: un enlace de invitación a una planta que ya no existe no
     * debe seguir circulando).
     */
    public void eliminarPlanta(Planta planta) {
        Set<UUID> invitados = accesos.invitadosDe(planta.id());
        diagnosticos.eliminarDe(planta.id());
        accesos.revocarTodosDe(planta.id());
        invitaciones.eliminarDe(planta.id());
        plantas.eliminar(planta.id());
        if (!invitados.isEmpty()) {
            notificador.avisar(new Aviso(invitados, TipoDeAviso.ACCESO_PERDIDO,
                    "Perdiste el acceso a la planta «" + planta.apodo() + "» porque su dueño la eliminó.",
                    Map.of(Aviso.PLANTA_ID, planta.id().toString())));
        }
    }

    /**
     * Todo lo que un usuario deja atrás al darse de baja: sus plantas con su
     * cascada, los accesos que él tenía como invitado en plantas ajenas, sus
     * solicitudes de especie y sus solicitudes de restablecimiento (D56).
     * No elimina al usuario (eso, con la RN4 delante, es del servicio).
     */
    public void eliminarLoDe(UUID usuarioId) {
        for (Planta planta : plantas.deDueno(usuarioId)) {
            eliminarPlanta(planta);
        }
        accesos.revocarTodosDelInvitado(usuarioId);
        solicitudes.eliminarDe(usuarioId);
        restablecimientos.eliminarDe(usuarioId);
    }
}
