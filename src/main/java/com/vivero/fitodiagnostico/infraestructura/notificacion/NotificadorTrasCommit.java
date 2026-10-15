package com.vivero.fitodiagnostico.infraestructura.notificacion;

import com.vivero.fitodiagnostico.dominio.modelo.Aviso;
import com.vivero.fitodiagnostico.dominio.puerto.Notificador;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Decorador que retiene los avisos hasta que la transacción en curso se
 * confirma (D57). Resuelve el límite del bloque 3: si un servicio avisaba y
 * después la transacción se revertía, el aviso ya había salido. Ahora, en
 * rollback, NO sale nunca.
 *
 * <p>Por qué es un decorador del puerto y no un {@code @TransactionalEventListener}:
 * el servicio sigue llamando a {@code Notificador.avisar} sin saber que hay
 * transacciones de por medio, así que ni el dominio ni la aplicación ganan un
 * solo import de Spring (los eventos transaccionales habrían obligado a los
 * servicios a publicar eventos con {@code ApplicationEventPublisher}).
 *
 * <p>Sin transacción activa (un llamador sin {@code @Transactional}) el aviso
 * sale de inmediato: no hay commit que esperar. Un fallo del destino jamás
 * se propaga: en {@code afterCommit} una excepción llegaría al llamador del
 * commit y respondería 500 por una operación que SÍ quedó guardada.
 */
public class NotificadorTrasCommit implements Notificador {

    private static final Logger log = LoggerFactory.getLogger(NotificadorTrasCommit.class);

    private final Notificador destino;

    public NotificadorTrasCommit(Notificador destino) {
        this.destino = destino;
    }

    @Override
    public void avisar(Aviso aviso) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    entregar(aviso);
                }
            });
        } else {
            entregar(aviso);
        }
    }

    private void entregar(Aviso aviso) {
        try {
            destino.avisar(aviso);
        } catch (RuntimeException e) {
            log.error("no se pudo entregar el aviso {}", aviso.tipo(), e);
        }
    }
}
