package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.mascota.modelo.EstadoDeAccion;
import com.vivero.fitodiagnostico.mascota.modelo.TipoDeAccion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Fila de {@code accion_mascota} (V18). */
@Entity
@Table(name = "accion_mascota")
public class AccionDeMascotaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "planta_id", nullable = false)
    private UUID plantaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoDeAccion tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoDeAccion estado;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "vence_en")
    private Instant venceEn;

    @Column(name = "resuelta_en")
    private Instant resueltaEn;

    @Column(name = "humedad_referencia")
    private Double humedadReferencia;

    protected AccionDeMascotaEntity() { }   // requerido por JPA

    AccionDeMascotaEntity(UUID id) {
        this.id = id;
    }

    void volcar(UUID plantaId, TipoDeAccion tipo, EstadoDeAccion estado, Instant creadaEn, Instant venceEn,
                Instant resueltaEn, Double humedadReferencia) {
        this.plantaId = plantaId;
        this.tipo = tipo;
        this.estado = estado;
        this.creadaEn = creadaEn;
        this.venceEn = venceEn;
        this.resueltaEn = resueltaEn;
        this.humedadReferencia = humedadReferencia;
    }

    UUID getId() { return id; }

    UUID getPlantaId() { return plantaId; }

    TipoDeAccion getTipo() { return tipo; }

    EstadoDeAccion getEstado() { return estado; }

    Instant getCreadaEn() { return creadaEn; }

    Instant getVenceEn() { return venceEn; }

    Instant getResueltaEn() { return resueltaEn; }

    Double getHumedadReferencia() { return humedadReferencia; }
}
