package com.vivero.fitodiagnostico.infraestructura.notificacion;

import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.modelo.TipoDeAviso;
import com.vivero.fitodiagnostico.dominio.puerto.Notificador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Reparte cada aviso a los canales que le corresponden (D58). Resuelve el
 * límite del bloque 3 de "un solo bean de Notificador": los servicios ven UN
 * {@link Notificador}, y este decide si va a log, a correo, a la vista en vivo
 * o a varios.
 *
 * <p>Qué tipo va por qué canal es una tabla visible ({@link #RUTAS}) y no
 * una decisión de cada adaptador, para poder leerla entera y probar que
 * ningún tipo queda sin ruta. Agregar un {@link TipoDeAviso} obliga a decidir
 * su ruta (la prueba falla si falta); agregar un canal es una entrada del
 * {@link Canal} y una clase que implemente el puerto.
 *
 * <p>Un canal que falla no impide a los demás: se registra y se sigue.
 */
public class NotificadorCompuesto implements Notificador {

    /** Los canales que existen. */
    public enum Canal { REGISTRO, CORREO, VIVO }

    /**
     * <ul>
     *   <li>{@code DIAGNOSTICO_NUEVO}: solo VIVO. Es una por medición: en el registro sería ruido
     *       y por correo, una avalancha. Su razón de ser es refrescar la pantalla abierta (RF-G2).</li>
     *   <li>{@code CAMBIO_DE_ESTADO}, {@code ACCESO_PERDIDO}, {@code SOLICITUD_RESUELTA}: los tres
     *       canales. Son los que el usuario quiere enterarse aunque no esté mirando (correo) y,
     *       si está mirando, ver al instante (vivo).</li>
     *   <li>{@code INVITACION}, {@code RESTABLECIMIENTO}: registro y correo, NUNCA vivo. Llevan un
     *       token y su destinatario puede ni tener sesión; el único canal que le llega es el correo.</li>
     * </ul>
     */
    public static final Map<TipoDeAviso, Set<Canal>> RUTAS;

    static {
        Map<TipoDeAviso, Set<Canal>> rutas = new EnumMap<>(TipoDeAviso.class);
        rutas.put(TipoDeAviso.DIAGNOSTICO_NUEVO, Set.of(Canal.VIVO));
        rutas.put(TipoDeAviso.CAMBIO_DE_ESTADO, Set.of(Canal.REGISTRO, Canal.CORREO, Canal.VIVO));
        rutas.put(TipoDeAviso.ACCESO_PERDIDO, Set.of(Canal.REGISTRO, Canal.CORREO, Canal.VIVO));
        rutas.put(TipoDeAviso.SOLICITUD_RESUELTA, Set.of(Canal.REGISTRO, Canal.CORREO, Canal.VIVO));
        rutas.put(TipoDeAviso.INVITACION, Set.of(Canal.REGISTRO, Canal.CORREO));
        rutas.put(TipoDeAviso.RESTABLECIMIENTO, Set.of(Canal.REGISTRO, Canal.CORREO));
        RUTAS = Map.copyOf(rutas);
    }

    private static final Logger log = LoggerFactory.getLogger(NotificadorCompuesto.class);

    private final Map<Canal, Notificador> canales;

    public NotificadorCompuesto(Map<Canal, Notificador> canales) {
        this.canales = Map.copyOf(canales);
    }

    @Override
    public void avisar(Aviso aviso) {
        for (Canal canal : RUTAS.getOrDefault(aviso.tipo(), Set.of())) {
            Notificador adaptador = canales.get(canal);
            if (adaptador == null) {
                continue;
            }
            try {
                adaptador.avisar(aviso);
            } catch (RuntimeException e) {
                log.error("el canal {} falló al entregar el aviso {}", canal, aviso.tipo(), e);
            }
        }
    }
}
