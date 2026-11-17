package com.vivero.fitodiagnostico.adaptador.salida.notificacion;

import com.vivero.fitodiagnostico.mascota.modelo.ActualizacionDeMascota;
import com.vivero.fitodiagnostico.mascota.modelo.AvisoDeMascota;
import com.vivero.fitodiagnostico.mascota.puerto.salida.PublicadorDeMascota;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * El {@link PublicadorDeMascota} que ven los servicios (D142): reparte cada evento a todos los canales (vista en
 * vivo y, si MQTT está encendido, la matera) y solo después del commit, por la misma razón que la D57: si la
 * transacción de la mascota se revierte, el evento no existió y no debe salir. Un canal que falla no impide a los
 * demás ni llega al llamador.
 *
 * <p>Conoce solo el puerto, no las clases de los canales: así vive en este paquete sin depender de
 * {@code adaptador.salida.mqtt} (cada salida es una isla). La composición le pasa la lista.
 */
public class PublicadorDeMascotaTrasCommit implements PublicadorDeMascota {

    private static final Logger log = LoggerFactory.getLogger(PublicadorDeMascotaTrasCommit.class);

    private final List<PublicadorDeMascota> canales;

    public PublicadorDeMascotaTrasCommit(List<PublicadorDeMascota> canales) {
        this.canales = List.copyOf(canales);
    }

    @Override
    public void publicar(AvisoDeMascota aviso) {
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

    @Override
    public void publicarActualizacion(ActualizacionDeMascota cambio) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    entregarActualizacion(cambio);
                }
            });
        } else {
            entregarActualizacion(cambio);
        }
    }

    private void entregarActualizacion(ActualizacionDeMascota cambio) {
        for (PublicadorDeMascota canal : canales) {
            try {
                canal.publicarActualizacion(cambio);
            } catch (RuntimeException e) {
                log.error("no se pudo publicar la actualización de mascota por {}", canal.getClass().getSimpleName(), e);
            }
        }
    }

    private void entregar(AvisoDeMascota aviso) {
        for (PublicadorDeMascota canal : canales) {
            try {
                canal.publicar(aviso);
            } catch (RuntimeException e) {
                log.error("no se pudo publicar el evento de mascota {} por {}", aviso.evento().tipo(),
                        canal.getClass().getSimpleName(), e);
            }
        }
    }
}
