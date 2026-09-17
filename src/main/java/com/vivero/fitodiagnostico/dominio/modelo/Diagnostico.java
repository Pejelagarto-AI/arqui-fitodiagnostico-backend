package com.vivero.fitodiagnostico.dominio.modelo;

import java.time.Instant;

public record Diagnostico(
        Especie especie,
        Medicion medicion,
        String estado,
        String detalle,
        Instant evaluadoEn) { }
