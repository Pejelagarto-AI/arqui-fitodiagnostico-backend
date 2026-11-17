package com.vivero.fitodiagnostico.mascota.puerto.entrada;

import com.vivero.fitodiagnostico.mascota.modelo.Accesorio;

import java.util.UUID;

/** El dueño le pone un accesorio a la mascota de su planta (fase 13, D182). Un invitado recibe 403, un ajeno 404. */
public interface CambiarAccesorioPort {

    void cambiar(UUID solicitante, UUID plantaId, Accesorio accesorio);
}
