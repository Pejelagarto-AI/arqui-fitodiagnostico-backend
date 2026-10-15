package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.Clasificacion;
import com.vivero.fitodiagnostico.dominio.modelo.Magnitud;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Una fila de {@code diagnostico_parametro}: lo que se midió y contra qué rango se clasificó. */
@Embeddable
public record ParametroEmbebido(
        @Column(name = "magnitud", nullable = false) @Enumerated(EnumType.STRING) Magnitud magnitud,
        @Column(name = "valor", nullable = false) double valor,
        @Column(name = "rango_minimo", nullable = false) double rangoMinimo,
        @Column(name = "rango_maximo", nullable = false) double rangoMaximo,
        @Column(name = "clasificacion", nullable = false) @Enumerated(EnumType.STRING) Clasificacion clasificacion) { }
