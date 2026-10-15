package com.vivero.fitodiagnostico.infraestructura.persistencia;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/** Fila de {@code acceso_compartido}. No sale de este paquete (RA-04). */
@Entity
@Table(name = "acceso_compartido")
public class AccesoEntity {

    @EmbeddedId
    private AccesoId id;

    @Column(name = "otorgado_en", nullable = false)
    private Instant otorgadoEn;

    protected AccesoEntity() { }   // requerido por JPA

    AccesoEntity(AccesoId id, Instant otorgadoEn) {
        this.id = id;
        this.otorgadoEn = otorgadoEn;
    }

    public AccesoId getId() {
        return id;
    }

    public Instant getOtorgadoEn() {
        return otorgadoEn;
    }
}
