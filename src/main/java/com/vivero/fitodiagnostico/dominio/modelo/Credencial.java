package com.vivero.fitodiagnostico.dominio.modelo;

/**
 * Lo único que el sistema guarda de una contraseña: su hash (RN10). El dominio
 * no sabe con qué algoritmo se calculó; eso es del puerto
 * {@code CifradorDeContrasena}. Este tipo existe para que una contraseña en
 * claro y un hash no sean ambos un {@code String} intercambiable por error.
 *
 * <p>{@link #toString()} no imprime el hash: un {@code Usuario} que termine en
 * un log o en un mensaje de excepción no debe filtrarlo.
 */
public record Credencial(String hash) {

    public Credencial {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("la credencial requiere un hash");
        }
    }

    @Override
    public String toString() {
        return "Credencial[hash=****]";
    }
}
