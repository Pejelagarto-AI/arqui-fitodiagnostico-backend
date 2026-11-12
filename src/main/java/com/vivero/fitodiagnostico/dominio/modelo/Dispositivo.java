package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dominio.excepcion.DispositivoYaReclamadoException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    private final OrigenDeRegistro registradoPor;
    private UUID plantaId;
    private Instant reclamadoEn;
    private Instant ultimaCredencialEn;
    private Reto reto;
    private Instant ultimoContactoEn;
    private String ultimaIp;

    /** Mínimo de caracteres del secreto de un dispositivo que se autorregistra. */
    public static final int LARGO_MINIMO_DEL_SECRETO_AUTORREGISTRADO = 32;

    /** Reconstruye un dispositivo ya guardado dado de alta por un administrador. */
    public Dispositivo(String serial, String secreto, UUID plantaId, Instant reclamadoEn,
                       Instant ultimaCredencialEn, Reto reto, Instant creadoEn) {
        this(serial, secreto, plantaId, reclamadoEn, ultimaCredencialEn, reto, creadoEn, OrigenDeRegistro.ADMIN);
    }

    /** Reconstruye un dispositivo ya guardado sin datos de contacto (nunca habló con el servidor). */
    public Dispositivo(String serial, String secreto, UUID plantaId, Instant reclamadoEn,
                       Instant ultimaCredencialEn, Reto reto, Instant creadoEn, OrigenDeRegistro registradoPor) {
        this(serial, secreto, plantaId, reclamadoEn, ultimaCredencialEn, reto, creadoEn, registradoPor, null, null);
    }

    /** Reconstruye un dispositivo ya guardado (lo usa el adaptador de persistencia). */
    public Dispositivo(String serial, String secreto, UUID plantaId, Instant reclamadoEn,
                       Instant ultimaCredencialEn, Reto reto, Instant creadoEn, OrigenDeRegistro registradoPor,
                       Instant ultimoContactoEn, String ultimaIp) {
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
        if (registradoPor == null) {
            throw new IllegalArgumentException("el dispositivo requiere saber quién lo registró");
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
        this.registradoPor = registradoPor;
        this.ultimoContactoEn = ultimoContactoEn;
        this.ultimaIp = ultimaIp;
    }

    /** Un dispositivo recién salido de fábrica: sin planta, sin reto, sin credencial. */
    public static Dispositivo deFabrica(String serial, String secreto, Instant ahora) {
        return new Dispositivo(serial, secreto, null, null, null, null, ahora);
    }

    /**
     * Un dispositivo que se dio de alta solo, con la firma de la flota ya verificada por quien lo llama. Exige un
     * secreto de al menos {@value #LARGO_MINIMO_DEL_SECRETO_AUTORREGISTRADO} caracteres: lo genera el chip y no
     * hay un administrador que lo haya elegido bien.
     */
    public static Dispositivo autorregistrado(String serial, String secreto, Instant ahora) {
        if (secreto == null || secreto.length() < LARGO_MINIMO_DEL_SECRETO_AUTORREGISTRADO) {
            throw new IllegalArgumentException("el secreto debe tener al menos "
                    + LARGO_MINIMO_DEL_SECRETO_AUTORREGISTRADO + " caracteres");
        }
        return new Dispositivo(serial, secreto, null, null, null, null, ahora, OrigenDeRegistro.FLOTA);
    }

    /** ¿Este secreto es el del dispositivo? Comparación en tiempo constante. */
    public boolean tieneElSecreto(String candidato) {
        return candidato != null && MessageDigest.isEqual(secreto.getBytes(StandardCharsets.UTF_8),
                candidato.getBytes(StandardCharsets.UTF_8));
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

    // ---- contacto ----

    /** Cuándo vino a hablar con el servidor y desde qué dirección. Una IP desconocida (null) no borra la anterior. */
    public void registrarContacto(Instant ahora, String ip) {
        if (ahora == null) {
            throw new IllegalArgumentException("registrar contacto requiere la hora");
        }
        this.ultimoContactoEn = ahora;
        if (ip != null && !ip.isBlank()) {
            this.ultimaIp = ip;
        }
    }

    /** ¿Nadie lo ha reclamado y habló con el servidor en los últimos {@code ventana}? */
    public boolean estaCercaSinReclamar(Instant ahora, Duration ventana) {
        return plantaId == null && ultimoContactoEn != null && ultimaIp != null
                && !ultimoContactoEn.plus(ventana).isBefore(ahora);
    }

    // ---- lectura ----

    public String serial() { return serial; }

    /** Texto opaco; solo lo usan la fábrica (una vez) y el verificador de la respuesta. */
    public String secreto() { return secreto; }

    public Optional<UUID> plantaId() { return Optional.ofNullable(plantaId); }

    public Optional<Instant> reclamadoEn() { return Optional.ofNullable(reclamadoEn); }

    public Optional<Instant> ultimaCredencialEn() { return Optional.ofNullable(ultimaCredencialEn); }

    public Optional<Reto> reto() { return Optional.ofNullable(reto); }

    public Optional<Instant> ultimoContactoEn() { return Optional.ofNullable(ultimoContactoEn); }

    public Optional<String> ultimaIp() { return Optional.ofNullable(ultimaIp); }

    public Instant creadoEn() { return creadoEn; }

    public OrigenDeRegistro registradoPor() { return registradoPor; }

    @Override
    public String toString() {
        return "Dispositivo[serial=" + serial + ", plantaId=" + plantaId + ", reclamadoEn=" + reclamadoEn + "]";
    }
}
