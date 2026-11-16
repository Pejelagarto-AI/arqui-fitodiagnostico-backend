package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.mascota.modelo.TipoDeEvento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Fila de {@code evento_mascota} (V17). El detalle va como JSON plano de texto a texto. */
@Entity
@Table(name = "evento_mascota")
public class EventoDeMascotaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "planta_id", nullable = false)
    private UUID plantaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoDeEvento tipo;

    @Column(name = "ocurrido_en", nullable = false)
    private Instant ocurridoEn;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    @Column(name = "detalle", nullable = false, length = 1000)
    private String detalle;

    protected EventoDeMascotaEntity() { }   // requerido por JPA

    EventoDeMascotaEntity(UUID id, UUID plantaId, TipoDeEvento tipo, Instant ocurridoEn, Instant registradoEn,
                          String detalle) {
        this.id = id;
        this.plantaId = plantaId;
        this.tipo = tipo;
        this.ocurridoEn = ocurridoEn;
        this.registradoEn = registradoEn;
        this.detalle = detalle;
    }

    UUID getId() { return id; }

    UUID getPlantaId() { return plantaId; }

    TipoDeEvento getTipo() { return tipo; }

    Instant getOcurridoEn() { return ocurridoEn; }

    String getDetalle() { return detalle; }
}
