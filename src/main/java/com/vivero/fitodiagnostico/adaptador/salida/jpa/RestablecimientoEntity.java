package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Fila de {@code restablecimiento} (V9). No sale de este paquete (RA-04). */
@Entity
@Table(name = "restablecimiento")
public class RestablecimientoEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "huella_token", nullable = false, unique = true)
    private String huellaToken;

    @Column(name = "huella_credencial", nullable = false)
    private String huellaCredencial;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "usado", nullable = false)
    private boolean usado;

    protected RestablecimientoEntity() { }   // requerido por JPA

    RestablecimientoEntity(UUID id, UUID usuarioId, String huellaToken, String huellaCredencial,
                           Instant creadaEn, Instant expiraEn, boolean usado) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.huellaToken = huellaToken;
        this.huellaCredencial = huellaCredencial;
        this.creadaEn = creadaEn;
        this.expiraEn = expiraEn;
        this.usado = usado;
    }

    UUID getId() { return id; }

    UUID getUsuarioId() { return usuarioId; }

    String getHuellaToken() { return huellaToken; }

    String getHuellaCredencial() { return huellaCredencial; }

    Instant getCreadaEn() { return creadaEn; }

    Instant getExpiraEn() { return expiraEn; }

    boolean isUsado() { return usado; }
}
