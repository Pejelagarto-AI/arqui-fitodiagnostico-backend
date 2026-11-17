package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.mascota.modelo.ResumenDeMascota;
import com.vivero.fitodiagnostico.salud.ConsultarSaludPort;

import java.time.Instant;
import java.util.UUID;

/**
 * Forma pública de una planta. {@code esDueno} le ahorra al front comparar ids
 * para decidir si muestra editar y eliminar (RN5); no es una medida de seguridad,
 * esa la hace el servidor en cada operación.
 *
 * <p>Fase 13 (D185): {@code mascota} y {@code salud} son el tablero en una sola llamada. Solo los traen los listados
 * ({@code GET /plantas} y {@code GET /plantas/compartidas}), que los arman por lote; en el resto de las respuestas de
 * planta valen {@code null}, igual que cuando la planta no tiene mascota o ninguna de sus materas ha reportado.
 */
public record RespuestaPlanta(UUID id, UUID duenoId, String apodo, String especie,
                              Instant registradaEn, boolean esDueno, Mascota mascota, Salud salud) {

    /** Lo que dibuja la tarjeta de la mascota. */
    public record Mascota(int vitalidad, String animo, int racha, String accesorio) {
        public static Mascota desde(ResumenDeMascota r) {
            return new Mascota(r.vitalidad(), r.animo().name(), r.racha(), r.accesorio().name());
        }
    }

    /** La peor salud entre las materas de la planta, con la frase en primera persona (D158). */
    public record Salud(String estado, String mensaje) {
        public static Salud desde(ConsultarSaludPort.Vista v) {
            return new Salud(v.salud().estado().name(), v.mensaje());
        }
    }

    public static RespuestaPlanta desde(Planta planta, UUID solicitante) {
        return desde(planta, solicitante, null, null);
    }

    public static RespuestaPlanta desde(Planta planta, UUID solicitante, ResumenDeMascota mascota,
                                        ConsultarSaludPort.Vista salud) {
        return new RespuestaPlanta(planta.id(), planta.duenoId(), planta.apodo(),
                planta.especie().nombre(), planta.registradaEn(), planta.esDe(solicitante),
                mascota == null ? null : Mascota.desde(mascota), salud == null ? null : Salud.desde(salud));
    }
}
