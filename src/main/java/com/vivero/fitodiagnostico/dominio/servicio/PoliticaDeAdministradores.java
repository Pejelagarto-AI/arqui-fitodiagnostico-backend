package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Usuario;

/**
 * Hace cumplir la RN4: siempre debe existir al menos un administrador (D18).
 * Servicio de dominio puro: no consulta ningún puerto, recibe el conteo ya
 * calculado. Así se prueba con dos números y un objeto en memoria, y no
 * puede "olvidar" consultar nada (D19 aplica el mismo criterio a la política
 * de acceso).
 *
 * <p>Tres caminos distintos terminan aquí —revocar el rol (RF-B5), eliminar a
 * un usuario (RF-B4) y que alguien elimine su propia cuenta (RF-A6)— desde dos
 * servicios de aplicación. La regla vive una sola vez.
 */
public class PoliticaDeAdministradores {

    /**
     * ¿Puede este usuario dejar de ser administrador (por revocación o por
     * desaparecer)? Si no lo es, no pierde nada y la respuesta es siempre sí.
     * Si lo es, solo mientras quede al menos otro.
     *
     * @param totalAdministradores cuántos administradores hay ahora, él incluido
     */
    public boolean puedeDejarDeSerAdministrador(Usuario usuario, long totalAdministradores) {
        if (!usuario.esAdministrador()) {
            return true;
        }
        return totalAdministradores > 1;
    }
}
