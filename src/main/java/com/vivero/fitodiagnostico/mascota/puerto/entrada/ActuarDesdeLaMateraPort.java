package com.vivero.fitodiagnostico.mascota.puerto.entrada;

import com.vivero.fitodiagnostico.mascota.modelo.ResultadoDeAccion;

import java.util.UUID;

/** La matera acaricia a su mascota con el botón (fase 13, D181). El vínculo del dispositivo ya lo verificó quien llama. */
public interface ActuarDesdeLaMateraPort {

    /** Mismo enfriamiento que las caricias de la persona (D172). El evento queda con {@code origen: MATERA}. */
    ResultadoDeAccion acariciar(UUID plantaId, String serial);
}
