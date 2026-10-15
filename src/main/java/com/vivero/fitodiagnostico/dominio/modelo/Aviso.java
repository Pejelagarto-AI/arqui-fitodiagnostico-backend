package com.vivero.fitodiagnostico.dominio.modelo;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Qué se avisa, a quién y con qué texto. No dice POR QUÉ CANAL: eso lo decide
 * cada adaptador de {@code Notificador} (log, correo, WebSocket).
 *
 * <p>Además del texto lleva dos cosas que un canal puede necesitar (D53):
 * <ul>
 *   <li>{@code datos}: valores estructurados para quien quiera algo más que
 *       una frase (el id de la planta para la vista en vivo, el token de una
 *       invitación o de un restablecimiento para armar el enlace). El texto es
 *       para leer; los datos, para máquinas. Las claves conocidas son las
 *       constantes {@link #PLANTA_ID}, {@link #TOKEN}, etc.</li>
 *   <li>{@code correos}: direcciones de personas que todavía NO son usuarios
 *       (la invitación a quien no tiene cuenta, RF-E2). Es la única excepción
 *       a "los destinatarios son ids": quien no tiene cuenta no tiene id.</li>
 * </ul>
 *
 * <p>El token es una credencial: {@link #toString()} no imprime {@code datos}
 * ni {@code correos}, para que un registro o un mensaje de error nunca lo filtren.
 */
public record Aviso(Set<UUID> destinatarios, TipoDeAviso tipo, String mensaje,
                    Set<String> correos, Map<String, String> datos) {

    public static final String PLANTA_ID = "plantaId";
    public static final String ESTADO = "estado";
    public static final String REGISTRADO_EN = "registradoEn";
    public static final String ORIGEN = "origen";
    public static final String TOKEN = "token";
    public static final String SOLICITUD_ID = "solicitudId";
    public static final String RESOLUCION = "resolucion";

    public Aviso {
        if (tipo == null) {
            throw new IllegalArgumentException("el aviso requiere tipo");
        }
        if (mensaje == null || mensaje.isBlank()) {
            throw new IllegalArgumentException("el aviso requiere mensaje");
        }
        destinatarios = destinatarios == null ? Set.of() : Set.copyOf(destinatarios);
        correos = correos == null ? Set.of() : Set.copyOf(correos);
        datos = datos == null ? Map.of() : Map.copyOf(datos);
    }

    /** Aviso a usuarios, sin datos adicionales. */
    public Aviso(Set<UUID> destinatarios, TipoDeAviso tipo, String mensaje) {
        this(destinatarios, tipo, mensaje, Set.of(), Map.of());
    }

    /** Aviso a usuarios con datos estructurados. */
    public Aviso(Set<UUID> destinatarios, TipoDeAviso tipo, String mensaje, Map<String, String> datos) {
        this(destinatarios, tipo, mensaje, Set.of(), datos);
    }

    /** Aviso a direcciones de correo de personas sin cuenta (la invitación, RF-E2). */
    public static Aviso aCorreos(Set<String> correos, TipoDeAviso tipo, String mensaje, Map<String, String> datos) {
        return new Aviso(Set.of(), tipo, mensaje, correos, datos);
    }

    @Override
    public String toString() {
        return "Aviso[tipo=" + tipo + ", destinatarios=" + destinatarios.size()
                + ", correos=" + correos.size() + ", mensaje=" + mensaje + "]";
    }
}
