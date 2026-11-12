package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import jakarta.persistence.*;

@Entity
@Table(name = "especie")
public class EspecieEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, unique = true)
    private String nombre;

    // columnDefinition explícito: la migración Flyway (V3) declara estas
    // columnas como NUMERIC(6,1); sin esto Hibernate valida un `double` contra
    // FLOAT por defecto y ddl-auto=validate (RO-07) rompe el arranque.
    @Column(name = "humedad_min", columnDefinition = "NUMERIC(6,1)") private double humedadMin;
    @Column(name = "humedad_max", columnDefinition = "NUMERIC(6,1)") private double humedadMax;
    @Column(name = "luz_min",     columnDefinition = "NUMERIC(6,1)") private double luzMin;
    @Column(name = "luz_max",     columnDefinition = "NUMERIC(6,1)") private double luzMax;
    @Column(name = "temp_min",    columnDefinition = "NUMERIC(6,1)") private double tempMin;
    @Column(name = "temp_max",    columnDefinition = "NUMERIC(6,1)") private double tempMax;

    // D10: el estado de activación vive en la fila; la migración V4 lo declara NOT NULL DEFAULT TRUE.
    @Column(name = "activa", nullable = false) private boolean activa = true;

    protected EspecieEntity() { }   // requerido por JPA; el mapeador (mismo paquete) también lo usa

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public double getHumedadMin() {
        return humedadMin;
    }

    public double getHumedadMax() {
        return humedadMax;
    }

    public double getLuzMin() {
        return luzMin;
    }

    public double getLuzMax() {
        return luzMax;
    }

    public double getTempMin() {
        return tempMin;
    }

    public double getTempMax() {
        return tempMax;
    }

    public boolean isActiva() {
        return activa;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }

    public void setHumedad(double minimo, double maximo) {
        this.humedadMin = minimo;
        this.humedadMax = maximo;
    }

    public void setLuz(double minimo, double maximo) {
        this.luzMin = minimo;
        this.luzMax = maximo;
    }

    public void setTemperatura(double minimo, double maximo) {
        this.tempMin = minimo;
        this.tempMax = maximo;
    }
}
