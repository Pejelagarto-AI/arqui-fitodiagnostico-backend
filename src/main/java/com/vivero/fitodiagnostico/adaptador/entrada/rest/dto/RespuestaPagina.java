package com.vivero.fitodiagnostico.adaptador.entrada.rest.dto;

import com.vivero.fitodiagnostico.dominio.modelo.Pagina;

import java.util.List;
import java.util.function.Function;

/** Forma pública de una página (páginas desde 0). */
public record RespuestaPagina<T>(List<T> contenido, int pagina, int tamano, long total, int totalDePaginas) {

    public static <E, T> RespuestaPagina<T> desde(Pagina<E> pagina, Function<E, T> conversion) {
        return new RespuestaPagina<>(pagina.elementos().stream().map(conversion).toList(),
                pagina.pagina(), pagina.tamano(), pagina.total(), pagina.totalDePaginas());
    }
}
