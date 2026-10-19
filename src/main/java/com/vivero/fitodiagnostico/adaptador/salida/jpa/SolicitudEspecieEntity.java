package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoSolicitud;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Fila de {@code solicitud_especie} (V10). No sale de este paquete (RA-04). */
@Entity
@Table(name = "solicitud_especie")
public class SolicitudEspecieEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "solicitante_id", nullable = false)
    private UUID solicitanteId;

    @Column(name = "nombre_propuesto", nullable = false)
    private String nombrePropuesto;

    @Column(name = "justificacion")
    private String justificacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoSolicitud estado;

    @Column(name = "respuesta")
    private String respuesta;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "resuelta_en")
    private Instant resueltaEn;

    protected SolicitudEspecieEntity() { }   // requerido por JPA

    SolicitudEspecieEntity(UUID id, UUID solicitanteId, String nombrePropuesto, String justificacion,
                           EstadoSolicitud estado, String respuesta, Instant creadaEn, Instant resueltaEn) {
        this.id = id;
        this.solicitanteId = solicitanteId;
        this.nombrePropuesto = nombrePropuesto;
        this.justificacion = justificacion;
        this.estado = estado;
        this.respuesta = respuesta;
        this.creadaEn = creadaEn;
        this.resueltaEn = resueltaEn;
    }

    UUID getId() { return id; }

    UUID getSolicitanteId() { return solicitanteId; }

    String getNombrePropuesto() { return nombrePropuesto; }

    String getJustificacion() { return justificacion; }

    EstadoSolicitud getEstado() { return estado; }

    String getRespuesta() { return respuesta; }

    Instant getCreadaEn() { return creadaEn; }

    Instant getResueltaEn() { return resueltaEn; }
}
