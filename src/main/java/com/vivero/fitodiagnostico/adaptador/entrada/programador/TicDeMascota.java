package com.vivero.fitodiagnostico.adaptador.entrada.programador;

import com.vivero.fitodiagnostico.mascota.puerto.entrada.AvanzarMascotasPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.UUID;

/**
 * Adaptador de entrada "el reloj" (D141): cada minuto (por defecto) le pide a la mascota que avance todas las
 * plantas. Es un adaptador como un controlador: quien llama es el tiempo y no una persona. Una planta que falla se
 * registra y no frena a las demás (cada una corre en su transacción).
 *
 * <p>Se apaga con {@code fitodiagnostico.mascota.tic.habilitado=false} (el perfil de pruebas lo apaga).
 */
public class TicDeMascota {

    private static final Logger log = LoggerFactory.getLogger(TicDeMascota.class);

    private final AvanzarMascotasPort mascotas;

    public TicDeMascota(AvanzarMascotasPort mascotas) {
        this.mascotas = mascotas;
    }

    @Scheduled(fixedDelayString = "${fitodiagnostico.mascota.tic.intervalo:PT1M}",
            initialDelayString = "${fitodiagnostico.mascota.tic.intervalo:PT1M}")
    public void tic() {
        for (UUID plantaId : mascotas.mascotasActivas()) {
            try {
                mascotas.avanzar(plantaId);
            } catch (RuntimeException e) {
                log.warn("no se pudo avanzar la mascota de la planta {}: {}", plantaId, e.getMessage());
            }
        }
    }
}
