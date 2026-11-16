package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;
import java.util.UUID;

/** Clave compuesta de {@code resumen_luz_hora}: una fila por planta y hora. */
@Embeddable
public record ResumenLuzHoraId(
        @Column(name = "planta_id") UUID plantaId,
        @Column(name = "hora") Instant hora) { }
