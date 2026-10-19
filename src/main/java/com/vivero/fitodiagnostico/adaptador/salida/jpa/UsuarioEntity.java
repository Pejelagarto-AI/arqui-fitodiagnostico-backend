package com.vivero.fitodiagnostico.adaptador.salida.jpa;

import com.vivero.fitodiagnostico.dominio.modelo.Rol;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Fila de {@code usuario} más sus filas de {@code usuario_rol}. No sale de este paquete (RA-04). */
@Entity
@Table(name = "usuario")
public class UsuarioEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "correo", nullable = false, unique = true)
    private String correo;

    @Column(name = "hash_contrasena", nullable = false)
    private String hashContrasena;

    // EAGER: son uno o dos valores y el mapeador los necesita siempre; con
    // open-in-view desactivado, LAZY fallaría fuera de la transacción.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "usuario_rol", joinColumns = @JoinColumn(name = "usuario_id"))
    @Column(name = "rol", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Rol> roles = new HashSet<>();

    protected UsuarioEntity() { }   // requerido por JPA; el mapeador (mismo paquete) también lo usa

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public String getHashContrasena() {
        return hashContrasena;
    }

    public Set<Rol> getRoles() {
        return roles;
    }

    void setId(UUID id) {
        this.id = id;
    }

    void setNombre(String nombre) {
        this.nombre = nombre;
    }

    void setCorreo(String correo) {
        this.correo = correo;
    }

    void setHashContrasena(String hashContrasena) {
        this.hashContrasena = hashContrasena;
    }
}
