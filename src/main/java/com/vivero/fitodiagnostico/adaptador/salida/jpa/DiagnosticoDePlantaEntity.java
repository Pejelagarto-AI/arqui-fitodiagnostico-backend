package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoGlobal;
import com.vivero.fitodiagnostico.dominio.modelo.OrigenDeMedicion;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Fila de {@code diagnostico_planta} más sus parámetros y recomendaciones
 * (tablas hijas, ver V7). No sale de este paquete (RA-04).
 */
@Entity
@Table(name = "diagnostico_planta")
public class DiagnosticoDePlantaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "planta_id", nullable = false)
    private UUID plantaId;

    @Column(name = "origen", nullable = false)
    @Enumerated(EnumType.STRING)
    private OrigenDeMedicion origen;

    @Column(name = "registrado_en", nullable = false)
    private Instant registradoEn;

    @Column(name = "evaluado_en", nullable = false)
    private Instant evaluadoEn;

    @Column(name = "especie_nombre", nullable = false)
    private String especieNombre;

    @Column(name = "estado", nullable = false)
    @Enumerated(EnumType.STRING)
    private EstadoGlobal estado;

    // EAGER + SUBSELECT: con open-in-view desactivado un LAZY fallaría fuera de la transacción, y
    // SUBSELECT carga las colecciones de TODA una página en una consulta cada una (no una por fila
    // ni un producto cartesiano entre las dos colecciones).
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "diagnostico_parametro", joinColumns = @JoinColumn(name = "diagnostico_id"))
    @Fetch(FetchMode.SUBSELECT)
    private Set<ParametroEmbebido> parametros = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "diagnostico_recomendacion", joinColumns = @JoinColumn(name = "diagnostico_id"))
    @OrderColumn(name = "orden")
    @Column(name = "texto", nullable = false, length = 1000)
    @Fetch(FetchMode.SUBSELECT)
    private List<String> recomendaciones = new ArrayList<>();

    protected DiagnosticoDePlantaEntity() { }   // requerido por JPA; el mapeador (mismo paquete) también lo usa

    public UUID getId() {
        return id;
    }

    public UUID getPlantaId() {
        return plantaId;
    }

    public OrigenDeMedicion getOrigen() {
        return origen;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }

    public Instant getEvaluadoEn() {
        return evaluadoEn;
    }

    public String getEspecieNombre() {
        return especieNombre;
    }

    public EstadoGlobal getEstado() {
        return estado;
    }

    public Set<ParametroEmbebido> getParametros() {
        return parametros;
    }

    public List<String> getRecomendaciones() {
        return recomendaciones;
    }

    void setId(UUID id) {
        this.id = id;
    }

    void setPlantaId(UUID plantaId) {
        this.plantaId = plantaId;
    }

    void setOrigen(OrigenDeMedicion origen) {
        this.origen = origen;
    }

    void setRegistradoEn(Instant registradoEn) {
        this.registradoEn = registradoEn;
    }

    void setEvaluadoEn(Instant evaluadoEn) {
        this.evaluadoEn = evaluadoEn;
    }

    void setEspecieNombre(String especieNombre) {
        this.especieNombre = especieNombre;
    }

    void setEstado(EstadoGlobal estado) {
        this.estado = estado;
    }
}
