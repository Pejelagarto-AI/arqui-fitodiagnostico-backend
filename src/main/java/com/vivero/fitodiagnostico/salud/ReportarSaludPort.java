package com.vivero.fitodiagnostico.salud;

import java.util.Map;
import java.util.UUID;

/**
 * Puerto de entrada de la matera: lo que dice de su salud y el aviso de que mandó una lectura. El vínculo con la
 * planta ya lo verificó quien llama ({@code VerificarVinculoPort}), igual que en las lecturas.
 */
public interface ReportarSaludPort {

    /** Lo que la matera dijo de sus sensores. {@code cambio} es verdadero si el estado difiere del anterior. */
    record Resultado(EstadoDeSalud estado, boolean cambio) { }

    Resultado reportar(String serial, UUID plantaId, Map<SensorDeMatera, EstadoDeSensor> sensores, String detalle);

    /** Llegó una lectura: la matera está viva y todos sus sensores respondieron. Si estaba mal, se recupera. */
    void registrarLectura(String serial, UUID plantaId);
}
