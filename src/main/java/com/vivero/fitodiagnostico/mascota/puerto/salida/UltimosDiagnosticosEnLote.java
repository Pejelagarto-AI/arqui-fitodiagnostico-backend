package com.vivero.fitodiagnostico.mascota.puerto.salida;

import com.vivero.fitodiagnostico.dominio.modelo.Diagnostico;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * El último diagnóstico de varias plantas en una consulta (fase 13, D185). Es un puerto de la mascota y no un método
 * nuevo de {@code RepositorioDiagnosticos} (del dominio, que esta fase no toca); lo implementa el mismo adaptador JPA.
 */
public interface UltimosDiagnosticosEnLote {

    /** Solo las plantas que alguna vez se midieron. */
    Map<UUID, Diagnostico> ultimosDe(Collection<UUID> plantaIds);
}
