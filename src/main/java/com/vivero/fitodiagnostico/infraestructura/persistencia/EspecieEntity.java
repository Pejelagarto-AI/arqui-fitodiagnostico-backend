package com.vivero.fitodiagnostico.infraestructura.persistencia;

import jakarta.persistence.*;

@Entity
@Table(name = "especie")
public class EspecieEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_cientifico", nullable = false, unique = true)
    private String nombreCientifico;

    @Column(name = "nombre_comun", nullable = false)
    private String nombreComun;

    // columnDefinition explícito: la migración Flyway (sección 7) declara estas
    // columnas como NUMERIC(4,1); sin esto Hibernate valida un `double` contra
    // FLOAT por defecto y ddl-auto=validate (RO-07) rompe el arranque.
    @Column(name = "temp_min", columnDefinition = "NUMERIC(4,1)") private double tempMin;
    @Column(name = "temp_max", columnDefinition = "NUMERIC(4,1)") private double tempMax;
    @Column(name = "hum_min",  columnDefinition = "NUMERIC(4,1)") private double humMin;
    @Column(name = "hum_max",  columnDefinition = "NUMERIC(4,1)") private double humMax;
    @Column(name = "luz_min")  private int    luzMin;
    @Column(name = "luz_max")  private int    luzMax;

    protected EspecieEntity() { }   // requerido por JPA

    public Long getId() {
        return id;
    }

    public String getNombreCientifico() {
        return nombreCientifico;
    }

    public String getNombreComun() {
        return nombreComun;
    }

    public double getTempMin() {
        return tempMin;
    }

    public double getTempMax() {
        return tempMax;
    }

    public double getHumMin() {
        return humMin;
    }

    public double getHumMax() {
        return humMax;
    }

    public int getLuzMin() {
        return luzMin;
    }

    public int getLuzMax() {
        return luzMax;
    }
}
