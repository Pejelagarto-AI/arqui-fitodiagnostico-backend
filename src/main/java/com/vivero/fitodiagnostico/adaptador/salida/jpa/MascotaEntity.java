package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Fila de {@code mascota} (V16). No sale de este paquete (RA-04). */
@Entity
@Table(name = "mascota")
public class MascotaEntity {

    @Id
    @Column(name = "planta_id")
    private UUID plantaId;

    @Column(name = "vitalidad", nullable = false)
    private double vitalidad;

    @Column(name = "desmayada", nullable = false)
    private boolean desmayada;

    @Enumerated(EnumType.STRING)
    @Column(name = "ultimo_estado", length = 20)
    private EstadoGlobal ultimoEstado;

    @Column(name = "ultima_lectura_en")
    private Instant ultimaLecturaEn;

    @Column(name = "actualizada_en", nullable = false)
    private Instant actualizadaEn;

    @Column(name = "saludable_desde")
    private Instant saludableDesde;

    @Column(name = "racha_dias", nullable = false)
    private int rachaDias;

    @Column(name = "racha_desde")
    private Instant rachaDesde;

    @Column(name = "ultima_regada_en")
    private Instant ultimaRegadaEn;

    @Column(name = "ultimo_sol_en")
    private Instant ultimoSolEn;

    protected MascotaEntity() { }   // requerido por JPA

    MascotaEntity(UUID plantaId) {
        this.plantaId = plantaId;
    }

    void volcar(double vitalidad, boolean desmayada, EstadoGlobal ultimoEstado, Instant ultimaLecturaEn,
                Instant actualizadaEn, Instant saludableDesde, int rachaDias, Instant rachaDesde,
                Instant ultimaRegadaEn, Instant ultimoSolEn) {
        this.vitalidad = vitalidad;
        this.desmayada = desmayada;
        this.ultimoEstado = ultimoEstado;
        this.ultimaLecturaEn = ultimaLecturaEn;
        this.actualizadaEn = actualizadaEn;
        this.saludableDesde = saludableDesde;
        this.rachaDias = rachaDias;
        this.rachaDesde = rachaDesde;
        this.ultimaRegadaEn = ultimaRegadaEn;
        this.ultimoSolEn = ultimoSolEn;
    }

    UUID getPlantaId() { return plantaId; }

    double getVitalidad() { return vitalidad; }

    boolean isDesmayada() { return desmayada; }

    EstadoGlobal getUltimoEstado() { return ultimoEstado; }

    Instant getUltimaLecturaEn() { return ultimaLecturaEn; }

    Instant getActualizadaEn() { return actualizadaEn; }

    Instant getSaludableDesde() { return saludableDesde; }

    int getRachaDias() { return rachaDias; }

    Instant getRachaDesde() { return rachaDesde; }

    Instant getUltimaRegadaEn() { return ultimaRegadaEn; }

    Instant getUltimoSolEn() { return ultimoSolEn; }
}
