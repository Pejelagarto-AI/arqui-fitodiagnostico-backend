package com.vivero.fitodiagnostico.adaptador.entrada.programado;

import com.vivero.fitodiagnostico.salud.RevisarSilencioPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adaptador de entrada del reloj: cada minuto le pide al caso de uso que revise qué materas callaron. No decide
 * nada; un fallo se registra y la siguiente pasada lo reintenta.
 */
@Component
public class RevisorDeSilencio {

    private static final Logger log = LoggerFactory.getLogger(RevisorDeSilencio.class);

    private final RevisarSilencioPort silencio;

    public RevisorDeSilencio(RevisarSilencioPort silencio) {
        this.silencio = silencio;
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void revisar() {
        try {
            int callaron = silencio.revisar();
            if (callaron > 0) {
                log.info("{} matera(s) sin noticias", callaron);
            }
        } catch (RuntimeException e) {
            log.error("no se pudo revisar el silencio de las materas", e);
        }
    }
}
