package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.salud.EstadoDeSalud;
import com.vivero.fitodiagnostico.salud.EstadoDeSensor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Fila de {@code dispositivo_salud} (V20 y V21). No sale de este paquete (RA-04). */
@Entity
@Table(name = "dispositivo_salud")
public class DispositivoSaludEntity {

    @Id
    @Column(name = "serial")
    private String serial;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoDeSalud estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_humedad", nullable = false)
    private EstadoDeSensor humedad;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_luz", nullable = false)
    private EstadoDeSensor luz;

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_temperatura", nullable = false)
    private EstadoDeSensor temperatura;

    @Column(name = "detalle", length = 200)
    private String detalle;

    @Column(name = "reportado_en")
    private Instant reportadoEn;

    @Column(name = "ultima_lectura_en")
    private Instant ultimaLecturaEn;

    @Column(name = "cambio_en", nullable = false)
    private Instant cambioEn;

    protected DispositivoSaludEntity() { }   // requerido por JPA

    DispositivoSaludEntity(String serial, EstadoDeSalud estado, EstadoDeSensor humedad, EstadoDeSensor luz,
                           EstadoDeSensor temperatura, String detalle, Instant reportadoEn, Instant ultimaLecturaEn,
                           Instant cambioEn) {
        this.serial = serial;
        this.estado = estado;
        this.humedad = humedad;
        this.luz = luz;
        this.temperatura = temperatura;
        this.detalle = detalle;
        this.reportadoEn = reportadoEn;
        this.ultimaLecturaEn = ultimaLecturaEn;
        this.cambioEn = cambioEn;
    }

    String getSerial() { return serial; }
    EstadoDeSalud getEstado() { return estado; }
    EstadoDeSensor getHumedad() { return humedad; }
    EstadoDeSensor getLuz() { return luz; }
    EstadoDeSensor getTemperatura() { return temperatura; }
    String getDetalle() { return detalle; }
    Instant getReportadoEn() { return reportadoEn; }
    Instant getUltimaLecturaEn() { return ultimaLecturaEn; }
    Instant getCambioEn() { return cambioEn; }
}
