package com.vivero.fitodiagnostico.dominio.servicio;

import com.vivero.fitodiagnostico.dominio.modelo.Planta;

import java.util.Set;
import java.util.UUID;

/**
 * Quién puede qué sobre una planta (RN3, RN5, D2). Función pura: recibe la
 * planta y el conjunto de invitados YA cargados y decide (D19). No consulta
 * ningún puerto, así que se prueba con dos objetos en memoria, y quien la usa
 * decide cuándo pagar la consulta de invitados.
 */
public final class PoliticaDeAcceso {

    /** Dueño o invitado (RN3). */
    public boolean puedeLeer(UUID usuario, Planta planta, Set<UUID> invitados) {
        exigir(usuario, planta);
        return planta.esDe(usuario) || (invitados != null && invitados.contains(usuario));
    }

    /** Solo el dueño: editar, eliminar, medir, compartir (RN5). */
    public boolean puedeModificar(UUID usuario, Planta planta) {
        exigir(usuario, planta);
        return planta.esDe(usuario);
    }

    private static void exigir(UUID usuario, Planta planta) {
        if (usuario == null || planta == null) {
            throw new IllegalArgumentException("la política requiere usuario y planta");
        }
    }
}
