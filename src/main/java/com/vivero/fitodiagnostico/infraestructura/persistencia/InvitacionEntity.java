package com.vivero.fitodiagnostico.infraestructura.persistencia;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoInvitacion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Fila de {@code invitacion} (V8). No sale de este paquete (RA-04). */
@Entity
@Table(name = "invitacion")
public class InvitacionEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "planta_id", nullable = false)
    private UUID plantaId;

    @Column(name = "huella_token", nullable = false, unique = true)
    private String huellaToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoInvitacion estado;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "correo_destino")
    private String correoDestino;

    @Column(name = "aceptada_en")
    private Instant aceptadaEn;

    protected InvitacionEntity() { }   // requerido por JPA

    InvitacionEntity(UUID id, UUID plantaId, String huellaToken, EstadoInvitacion estado, Instant creadaEn,
                     Instant expiraEn, String correoDestino, Instant aceptadaEn) {
        this.id = id;
        this.plantaId = plantaId;
        this.huellaToken = huellaToken;
        this.estado = estado;
        this.creadaEn = creadaEn;
        this.expiraEn = expiraEn;
        this.correoDestino = correoDestino;
        this.aceptadaEn = aceptadaEn;
    }

    UUID getId() { return id; }

    UUID getPlantaId() { return plantaId; }

    String getHuellaToken() { return huellaToken; }

    EstadoInvitacion getEstado() { return estado; }

    Instant getCreadaEn() { return creadaEn; }

    Instant getExpiraEn() { return expiraEn; }

    String getCorreoDestino() { return correoDestino; }

    Instant getAceptadaEn() { return aceptadaEn; }
}
