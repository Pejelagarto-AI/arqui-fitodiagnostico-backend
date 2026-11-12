package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Fila de {@code planta}. No sale de este paquete (RA-04). */
@Entity
@Table(name = "planta")
public class PlantaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "dueno_id", nullable = false)
    private UUID duenoId;

    @Column(name = "apodo", nullable = false, length = 60)
    private String apodo;

    // EAGER: el mapeador necesita siempre los rangos de la especie para armar la Planta, y con
    // open-in-view desactivado un LAZY fallaría fuera de la transacción. Es una fila por planta.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "especie_id", nullable = false)
    private EspecieEntity especie;

    @Column(name = "registrada_en", nullable = false)
    private Instant registradaEn;

    protected PlantaEntity() { }   // requerido por JPA; el mapeador (mismo paquete) también lo usa

    public UUID getId() {
        return id;
    }

    public UUID getDuenoId() {
        return duenoId;
    }

    public String getApodo() {
        return apodo;
    }

    public EspecieEntity getEspecie() {
        return especie;
    }

    public Instant getRegistradaEn() {
        return registradaEn;
    }

    void setId(UUID id) {
        this.id = id;
    }

    void setDuenoId(UUID duenoId) {
        this.duenoId = duenoId;
    }

    void setApodo(String apodo) {
        this.apodo = apodo;
    }

    void setEspecie(EspecieEntity especie) {
        this.especie = especie;
    }

    void setRegistradaEn(Instant registradaEn) {
        this.registradaEn = registradaEn;
    }
}
