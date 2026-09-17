package com.vivero.fitodiagnostico.infraestructura.persistencia;

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

    protected EspecieEntity() { }   // requerido por JPA

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
}
