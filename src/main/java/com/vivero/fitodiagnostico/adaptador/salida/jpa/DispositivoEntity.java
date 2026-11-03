package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Fila de {@code dispositivo} (V12). No sale de este paquete (RA-04). */
@Entity
@Table(name = "dispositivo")
public class DispositivoEntity {

    @Id
    @Column(name = "serial")
    private String serial;

    @Column(name = "secreto", nullable = false)
    private String secreto;

    @Column(name = "planta_id")
    private UUID plantaId;

    @Column(name = "reclamado_en")
    private Instant reclamadoEn;

    @Column(name = "ultima_credencial_en")
    private Instant ultimaCredencialEn;

    @Column(name = "reto")
    private String reto;

    @Column(name = "reto_expira")
    private Instant retoExpira;

    @Column(name = "creado_en", nullable = false)
    private Instant creadoEn;

    protected DispositivoEntity() { }   // requerido por JPA

    DispositivoEntity(String serial, String secreto, UUID plantaId, Instant reclamadoEn, Instant ultimaCredencialEn,
                      String reto, Instant retoExpira, Instant creadoEn) {
        this.serial = serial;
        this.secreto = secreto;
        this.plantaId = plantaId;
        this.reclamadoEn = reclamadoEn;
        this.ultimaCredencialEn = ultimaCredencialEn;
        this.reto = reto;
        this.retoExpira = retoExpira;
        this.creadoEn = creadoEn;
    }

    String getSerial() { return serial; }

    String getSecreto() { return secreto; }

    UUID getPlantaId() { return plantaId; }

    Instant getReclamadoEn() { return reclamadoEn; }

    Instant getUltimaCredencialEn() { return ultimaCredencialEn; }

    String getReto() { return reto; }

    Instant getRetoExpira() { return retoExpira; }

    Instant getCreadoEn() { return creadoEn; }
}
