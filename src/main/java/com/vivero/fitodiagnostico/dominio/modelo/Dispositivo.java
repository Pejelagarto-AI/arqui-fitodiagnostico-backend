package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dominio.excepcion.DispositivoYaReclamadoException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Un dispositivo de fábrica (la matera con su ESP32): nace con un {@code serial} impreso y un
 * {@code secreto} guardado adentro, sin planta. El dueño lo RECLAMA escribiendo el serial; el
 * dispositivo, por su cuenta, demuestra que conoce el secreto con un reto de un solo uso y recibe
 * una credencial corta. El humano nunca toca una credencial.
 *
 * <p>Es una entidad con identidad (el serial) y estado que cambia, por eso es mutable y no un
 * record como {@link Invitacion}: las reglas de cada transición viven en sus métodos. Como el resto
 * del dominio, no llama al reloj: toda decisión que depende del tiempo recibe {@code ahora}.
 *
 * <p>El {@code secreto} es un texto opaco para el dominio (no sabe que es una clave HMAC). Nunca
 * sale en {@link #toString()}.
 */
public class Dispositivo {

    /** Cuánto vale un reto sin usar: lo bastante para que el chip responda, muy poco para guardarlo. */
    public static final Duration VIGENCIA_DEL_RETO = Duration.ofSeconds(60);

    private final String serial;
    private final String secreto;
    private final Instant creadoEn;
    private UUID plantaId;
    private Instant reclamadoEn;
    private Instant ultimaCredencialEn;
    private Reto reto;

    /** Reconstruye un dispositivo ya guardado (lo usa el adaptador de persistencia). */
    public Dispositivo(String serial, String secreto, UUID plantaId, Instant reclamadoEn,
                       Instant ultimaCredencialEn, Reto reto, Instant creadoEn) {
        if (serial == null || !TokenDeDispositivo.FORMATO_DEL_ID.matcher(serial).matches()) {
            throw new IllegalArgumentException(
                    "el serial debe tener de 1 a 64 letras, números, punto, guion o guion bajo");
        }
        if (secreto == null || secreto.isBlank()) {
            throw new IllegalArgumentException("el dispositivo requiere secreto");
        }
        if (creadoEn == null) {
            throw new IllegalArgumentException("el dispositivo requiere fecha de creación");
        }
        if ((plantaId == null) != (reclamadoEn == null)) {
            throw new IllegalArgumentException("planta y fecha de reclamo van juntas: o ambas o ninguna");
        }
        this.serial = serial;
        this.secreto = secreto;
        this.plantaId = plantaId;
        this.reclamadoEn = reclamadoEn;
        this.ultimaCredencialEn = ultimaCredencialEn;
        this.reto = reto;
        this.creadoEn = creadoEn;
    }

    /** Un dispositivo recién salido de fábrica: sin planta, sin reto, sin credencial. */
    public static Dispositivo deFabrica(String serial, String secreto, Instant ahora) {
        return new Dispositivo(serial, secreto, null, null, null, null, ahora);
    }

    // ---- reclamo ----

    /**
     * El dueño de una planta lo reclama. Idempotente para la misma planta (no cambia la fecha del
     * primer reclamo); si ya es de otra planta, falla.
     *
     * @throws DispositivoYaReclamadoException si está reclamado por otra planta
     */
    public void reclamar(UUID planta, Instant ahora) {
        if (planta == null) {
            throw new IllegalArgumentException("reclamar requiere planta");
        }
        if (plantaId != null) {
            if (plantaId.equals(planta)) {
                return;
            }
            throw new DispositivoYaReclamadoException(serial);
        }
        this.plantaId = planta;
        this.reclamadoEn = ahora;
    }

    /**
     * Vuelve a SIN_RECLAMAR. Borra también la última credencial y cualquier reto pendiente: un
     * dispositivo desvinculado no puede renovar nada hasta que alguien lo reclame de nuevo.
     */
    public void desvincular() {
        this.plantaId = null;
        this.reclamadoEn = null;
        this.ultimaCredencialEn = null;
        this.reto = null;
    }

    public boolean estaReclamado() {
        return plantaId != null;
    }

    // ---- reto y credencial ----

    /** Un reto nuevo reemplaza al anterior. */
    public Reto asignarReto(String valor, Instant expira) {
        this.reto = new Reto(valor, expira);
        return reto;
    }

    /**
     * Gasta el reto: devuelve su valor si estaba vigente y lo borra; si había uno vencido también
     * lo borra pero devuelve vacío. Un reto sirve para UN intento, acierte o no.
     */
    public Optional<String> consumirReto(Instant ahora) {
        Reto actual = this.reto;
        this.reto = null;
        if (actual == null || !actual.vigenteEn(ahora)) {
            return Optional.empty();
        }
        return Optional.of(actual.valor());
    }

    public void registrarCredencial(Instant ahora) {
        this.ultimaCredencialEn = ahora;
    }

    // ---- lectura ----

    public String serial() { return serial; }

    /** Texto opaco; solo lo usan la fábrica (una vez) y el verificador de la respuesta. */
    public String secreto() { return secreto; }

    public Optional<UUID> plantaId() { return Optional.ofNullable(plantaId); }

    public Optional<Instant> reclamadoEn() { return Optional.ofNullable(reclamadoEn); }

    public Optional<Instant> ultimaCredencialEn() { return Optional.ofNullable(ultimaCredencialEn); }

    public Optional<Reto> reto() { return Optional.ofNullable(reto); }

    public Instant creadoEn() { return creadoEn; }

    @Override
    public String toString() {
        return "Dispositivo[serial=" + serial + ", plantaId=" + plantaId + ", reclamadoEn=" + reclamadoEn + "]";
    }
}
