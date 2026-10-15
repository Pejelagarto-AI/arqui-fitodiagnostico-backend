package com.vivero.fitodiagnostico.dominio.modelo;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Un único tipo de usuario con un conjunto de roles (D1, RN2). Inmutable: cada
 * cambio devuelve una copia, igual que {@link Especie}, así que un servicio
 * nunca deja un usuario a medio modificar si algo falla en el camino.
 *
 * <p>Invariantes que impone el constructor (y por tanto toda copia):
 * nombre y correo no vacíos, correo con forma mínima de dirección y
 * normalizado (RN1 se compara sobre el correo normalizado) y {@link Rol#CLIENTE}
 * siempre presente (RN2): no existe forma de construir un usuario sin él.
 */
public record Usuario(
        UUID id,
        String nombre,
        String correo,
        Credencial credencial,
        Set<Rol> roles) {

    public Usuario {
        if (id == null) {
            throw new IllegalArgumentException("el usuario requiere id");
        }
        if (nombre == null || nombre.isBlank()) {
            throw new IllegalArgumentException("el usuario requiere nombre");
        }
        if (credencial == null) {
            throw new IllegalArgumentException("el usuario requiere credencial");
        }
        nombre = nombre.trim();
        correo = normalizarCorreo(correo);
        EnumSet<Rol> completos = EnumSet.of(Rol.CLIENTE);
        if (roles != null) {
            completos.addAll(roles);
        }
        roles = Collections.unmodifiableSet(completos);
    }

    /** Usuario recién registrado: solo CLIENTE (RN2). */
    public static Usuario nuevo(String nombre, String correo, Credencial credencial) {
        return new Usuario(UUID.randomUUID(), nombre, correo, credencial, Set.of());
    }

    /**
     * Forma canónica del correo: sin espacios en los extremos y en minúscula.
     * Es la que se guarda y la que se busca, para que "Ana@x.co" y "ana@x.co"
     * sean el mismo correo (RN1).
     *
     * @throws IllegalArgumentException si está vacío o no tiene la forma {@code algo@algo}
     */
    public static String normalizarCorreo(String correo) {
        if (correo == null || correo.isBlank()) {
            throw new IllegalArgumentException("el usuario requiere correo");
        }
        String normalizado = correo.trim().toLowerCase(Locale.ROOT);
        int arroba = normalizado.indexOf('@');
        boolean formaValida = arroba > 0
                && arroba == normalizado.lastIndexOf('@')
                && arroba < normalizado.length() - 1
                && normalizado.chars().noneMatch(Character::isWhitespace);
        if (!formaValida) {
            throw new IllegalArgumentException("el correo no tiene forma de dirección: " + correo);
        }
        return normalizado;
    }

    public boolean esAdministrador() {
        return roles.contains(Rol.ADMINISTRADOR);
    }

    /** RF-B5: otorgar el rol. Idempotente. */
    public Usuario conRolAdministrador() {
        EnumSet<Rol> nuevos = EnumSet.copyOf(roles);
        nuevos.add(Rol.ADMINISTRADOR);
        return new Usuario(id, nombre, correo, credencial, nuevos);
    }

    /** RF-B5: revocar el rol. Idempotente; CLIENTE no se puede quitar (RN2). */
    public Usuario sinRolAdministrador() {
        EnumSet<Rol> nuevos = EnumSet.copyOf(roles);
        nuevos.remove(Rol.ADMINISTRADOR);
        return new Usuario(id, nombre, correo, credencial, nuevos);
    }

    /** RF-A4 / RF-B3: nuevos datos personales, misma identidad, roles y credencial. */
    public Usuario conDatos(String nuevoNombre, String nuevoCorreo) {
        return new Usuario(id, nuevoNombre, nuevoCorreo, credencial, roles);
    }

    /** RF-A5: nueva credencial (ya cifrada). */
    public Usuario conCredencial(Credencial nuevaCredencial) {
        return new Usuario(id, nombre, correo, nuevaCredencial, roles);
    }
}
