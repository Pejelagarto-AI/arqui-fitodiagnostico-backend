package com.vivero.fitodiagnostico.dominio.modelo;

import java.time.Instant;

public record Diagnostico(
        Especie especie,
        Ambiente ambiente,
        String estado,
        String detalle,
        Instant evaluadoEn) { }
