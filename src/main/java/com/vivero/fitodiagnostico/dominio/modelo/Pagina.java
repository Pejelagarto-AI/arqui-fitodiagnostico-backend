package com.vivero.fitodiagnostico.dominio.modelo;

import java.util.List;

/** Una página de resultados más el total, para que el cliente sepa cuántas hay (DA6: paginación desde el principio). */
public record Pagina<T>(List<T> elementos, int pagina, int tamano, long total) {

    public Pagina {
        elementos = elementos == null ? List.of() : List.copyOf(elementos);
    }

    public int totalDePaginas() {
        return (int) ((total + tamano - 1) / tamano);
    }
}
