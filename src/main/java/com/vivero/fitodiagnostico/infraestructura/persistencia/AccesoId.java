package com.vivero.fitodiagnostico.infraestructura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

/** Clave compuesta de {@code acceso_compartido}: un invitado por planta. */
@Embeddable
public record AccesoId(
        @Column(name = "planta_id") UUID plantaId,
        @Column(name = "invitado_id") UUID invitadoId) { }
