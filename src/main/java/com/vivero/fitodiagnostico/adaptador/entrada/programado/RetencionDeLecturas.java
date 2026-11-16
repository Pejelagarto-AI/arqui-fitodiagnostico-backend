package com.vivero.fitodiagnostico.adaptador.entrada.programado;

import com.vivero.fitodiagnostico.historial.PodarHistorialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Adaptador de entrada del reloj (fase 12, D176): una vez al día le pide al caso de uso que pode las lecturas crudas
 * viejas. No decide nada; un fallo se registra y la pasada del día siguiente lo reintenta. Se enciende y apaga con
 * {@code fitodiagnostico.historial.retencion.habilitada} (el perfil de pruebas lo apaga).
 */
public class RetencionDeLecturas {

    private static final Logger log = LoggerFactory.getLogger(RetencionDeLecturas.class);

    private final PodarHistorialPort historial;

    public RetencionDeLecturas(PodarHistorialPort historial) {
        this.historial = historial;
    }

    @Scheduled(cron = "${fitodiagnostico.historial.retencion.cron:0 30 3 * * *}")
    public void podar() {
        try {
            int borradas = historial.podar();
            log.info("retención del historial: {} lectura(s) cruda(s) borradas", borradas);
        } catch (RuntimeException e) {
            log.error("no se pudo podar el historial de lecturas", e);
        }
    }
}
