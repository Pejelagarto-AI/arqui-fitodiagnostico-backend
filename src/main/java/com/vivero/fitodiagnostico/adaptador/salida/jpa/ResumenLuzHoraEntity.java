package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

/** Fila de {@code resumen_luz_hora} (V22). No sale de este paquete (RA-04). */
@Entity
@Table(name = "resumen_luz_hora")
public class ResumenLuzHoraEntity {

    @EmbeddedId
    private ResumenLuzHoraId id;

    @Column(name = "lecturas", nullable = false)
    private int lecturas;

    @Column(name = "segundos_observados", nullable = false)
    private double segundosObservados;

    @Column(name = "segundos_con_luz", nullable = false)
    private double segundosConLuz;

    @Column(name = "lux_segundos", nullable = false)
    private double luxSegundos;

    @Column(name = "segundos_oscuridad_final", nullable = false)
    private double segundosEnOscuridadFinal;

    @Column(name = "primer_tramo_oscuro", nullable = false)
    private double primerTramoOscuro;

    @Column(name = "ultima_lectura_en")
    private Instant ultimaLecturaEn;

    @Column(name = "calculado_en", nullable = false)
    private Instant calculadoEn;

    protected ResumenLuzHoraEntity() { }   // requerido por JPA

    ResumenLuzHoraEntity(ResumenDeLuzPorHora r, Instant calculadoEn) {
        this.id = new ResumenLuzHoraId(r.plantaId(), r.hora());
        this.lecturas = r.lecturas();
        this.segundosObservados = r.segundosObservados();
        this.segundosConLuz = r.segundosConLuz();
        this.luxSegundos = r.luxSegundos();
        this.segundosEnOscuridadFinal = r.segundosEnOscuridadFinal();
        this.primerTramoOscuro = r.primerTramoOscuro();
        this.ultimaLecturaEn = r.ultimaLecturaEn();
        this.calculadoEn = calculadoEn;
    }

    ResumenDeLuzPorHora aResumen() {
        return new ResumenDeLuzPorHora(id.plantaId(), id.hora(), lecturas, segundosObservados, segundosConLuz,
                luxSegundos, segundosEnOscuridadFinal, primerTramoOscuro, ultimaLecturaEn);
    }
}
