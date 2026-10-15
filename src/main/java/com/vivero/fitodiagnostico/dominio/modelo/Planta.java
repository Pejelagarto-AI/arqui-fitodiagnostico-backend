package com.vivero.fitodiagnostico.dominio.modelo;

import com.vivero.fitodiagnostico.dominio.excepcion.EspecieInactivaException;

import java.time.Instant;
import java.util.UUID;

/**
 * Una planta con exactamente un dueño (RN6, D2). Guarda {@code duenoId} y no
 * el {@link Usuario} completo (D19): para decidir "¿es suyo?" basta el id, y
 * cargar al usuario entero solo para compararlo sería acoplar dos agregados.
 * Tampoco sabe nada de macetas ni de invitados: los invitados viven en
 * {@code RepositorioAccesos} y el vínculo con hardware, cuando exista, en la maceta.
 *
 * <p>Inmutable; cada cambio devuelve una copia. La regla RN11 vive en las
 * fábricas ({@link #nueva} y {@link #conEspecie}) y NO en el constructor: el
 * constructor también reconstruye plantas desde la base, y una planta cuya
 * especie se desactivó después debe poder reconstruirse y seguir funcionando.
 */
public record Planta(
        UUID id,
        UUID duenoId,
        String apodo,
        Especie especie,
        Instant registradaEn) {

    /** Largo máximo del apodo; coincide con la columna de V6. */
    public static final int LARGO_MAXIMO_DEL_APODO = 60;

    public Planta {
        if (id == null) {
            throw new IllegalArgumentException("la planta requiere id");
        }
        if (duenoId == null) {
            throw new IllegalArgumentException("la planta requiere dueño");
        }
        if (especie == null) {
            throw new IllegalArgumentException("la planta requiere especie");
        }
        if (registradaEn == null) {
            throw new IllegalArgumentException("la planta requiere fecha de registro");
        }
        apodo = normalizarApodo(apodo);
    }

    /**
     * RF-D1. La planta nace del lado de quien la registra.
     *
     * @throws EspecieInactivaException si la especie está descontinuada (RN11)
     */
    public static Planta nueva(UUID duenoId, String apodo, Especie especie) {
        exigirActiva(especie);
        return new Planta(UUID.randomUUID(), duenoId, apodo, especie, Instant.now());
    }

    /** RF-D4: solo cambia el apodo; la especie, activa o no, se conserva tal cual. */
    public Planta conApodo(String nuevoApodo) {
        return new Planta(id, duenoId, nuevoApodo, especie, registradaEn);
    }

    /**
     * RF-D4: cambia la especie. Volver a poner la MISMA especie no exige que
     * esté activa (no es un cambio, y así editar el apodo de una planta cuya
     * especie se descontinuó no falla); cambiarla por OTRA sí (RN11).
     *
     * @throws EspecieInactivaException si es otra especie y está descontinuada
     */
    public Planta conEspecie(Especie nuevaEspecie) {
        if (nuevaEspecie == null) {
            throw new IllegalArgumentException("la planta requiere especie");
        }
        if (!nuevaEspecie.nombre().equalsIgnoreCase(especie.nombre())) {
            exigirActiva(nuevaEspecie);
        }
        return new Planta(id, duenoId, apodo, nuevaEspecie, registradaEn);
    }

    public boolean esDe(UUID usuario) {
        return duenoId.equals(usuario);
    }

    private static void exigirActiva(Especie especie) {
        if (especie == null) {
            throw new IllegalArgumentException("la planta requiere especie");
        }
        if (!especie.activa()) {
            throw new EspecieInactivaException(especie.nombre());
        }
    }

    private static String normalizarApodo(String apodo) {
        if (apodo == null || apodo.isBlank()) {
            throw new IllegalArgumentException("la planta requiere apodo");
        }
        String limpio = apodo.trim();
        if (limpio.length() > LARGO_MAXIMO_DEL_APODO) {
            throw new IllegalArgumentException(
                    "el apodo no puede pasar de " + LARGO_MAXIMO_DEL_APODO + " caracteres");
        }
        return limpio;
    }
}
