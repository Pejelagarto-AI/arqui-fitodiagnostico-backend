package com.vivero.fitodiagnostico.dominio.modelo;

/**
 * Qué página de una lista se pide. Es del dominio y no el {@code Pageable} de
 * Spring Data porque el puerto que la recibe (RepositorioDiagnosticos) está en
 * dominio y ahí no pueden entrar ni Spring ni JPA (RA4). Páginas desde 0.
 */
public record Paginacion(int pagina, int tamano) {

    public static final int TAMANO_POR_DEFECTO = 20;
    public static final int TAMANO_MAXIMO = 100;

    public Paginacion {
        if (pagina < 0) {
            throw new IllegalArgumentException("la página no puede ser negativa");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new IllegalArgumentException("el tamaño de página debe estar entre 1 y " + TAMANO_MAXIMO);
        }
    }

    public static Paginacion primera() {
        return new Paginacion(0, TAMANO_POR_DEFECTO);
    }

    public long desplazamiento() {
        return (long) pagina * tamano;
    }
}
