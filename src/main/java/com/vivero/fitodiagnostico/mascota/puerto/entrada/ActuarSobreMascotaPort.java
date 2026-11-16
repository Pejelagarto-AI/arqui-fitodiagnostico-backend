package com.vivero.fitodiagnostico.mascota.puerto.entrada;

import com.vivero.fitodiagnostico.mascota.modelo.ResultadoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeAccion;

import java.util.UUID;

/** Acariciar o declarar un riego. Solo el dueño (un invitado recibe 403, un ajeno 404). */
public interface ActuarSobreMascotaPort {

    ResultadoDeAccion actuar(UUID solicitante, UUID plantaId, TipoDeAccion tipo);
}
