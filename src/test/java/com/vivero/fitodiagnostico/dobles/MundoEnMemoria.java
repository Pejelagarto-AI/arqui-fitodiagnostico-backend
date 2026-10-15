package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.aplicacion.CascadaDeEliminacion;

/**
 * Los dobles de plantas ya cableados entre sí (los accesos de las plantas, la
 * cascada sobre todos los repositorios y el notificador que graba), para que cada
 * prueba de aplicación no repita diez líneas de armado.
 */
public class MundoEnMemoria {

    public final AccesosEnMemoria accesos = new AccesosEnMemoria();
    public final PlantasEnMemoria plantas = new PlantasEnMemoria(accesos);
    public final DiagnosticosEnMemoria diagnosticos = new DiagnosticosEnMemoria();
    public final InvitacionesEnMemoria invitaciones = new InvitacionesEnMemoria();
    public final SolicitudesEnMemoria solicitudes = new SolicitudesEnMemoria();
    public final RestablecimientosEnMemoria restablecimientos = new RestablecimientosEnMemoria();
    public final NotificadorGrabador notificador = new NotificadorGrabador();
    public final CascadaDeEliminacion cascada = new CascadaDeEliminacion(plantas, diagnosticos, accesos,
            invitaciones, solicitudes, restablecimientos, notificador);
}
