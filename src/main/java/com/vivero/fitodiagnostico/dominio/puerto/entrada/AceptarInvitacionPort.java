package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;

import java.util.List;
import java.util.UUID;

/** Puerto de entrada: el invitado acepta, ve lo compartido con él y renuncia (RF-E2, RF-E5, RF-E6). */
public interface AceptarInvitacionPort {

    /** El resultado de aceptar registrándose (RF-E2): la cuenta nueva y la planta a la que ya tiene acceso. */
    record AceptacionConRegistro(Usuario usuario, Planta planta) { }

    Planta aceptar(UUID usuarioId, String token);

    AceptacionConRegistro registrarYAceptar(String token, String nombre, String correo, String contrasena);

    List<Planta> compartidasConmigo(UUID usuarioId);

    void renunciar(UUID usuarioId, UUID plantaId);
}
