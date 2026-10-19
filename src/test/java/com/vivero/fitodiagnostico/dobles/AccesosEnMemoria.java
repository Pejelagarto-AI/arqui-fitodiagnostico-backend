package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.modelo.AccesoCompartido;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioAccesos;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Doble en memoria de {@link RepositorioAccesos}: un par (planta, invitado) existe una sola vez. */
public class AccesosEnMemoria implements RepositorioAccesos {

    private final Set<AccesoCompartido> accesos = new HashSet<>();

    @Override
    public Set<UUID> invitadosDe(UUID plantaId) {
        Set<UUID> invitados = new HashSet<>();
        accesos.stream().filter(a -> a.plantaId().equals(plantaId)).forEach(a -> invitados.add(a.invitadoId()));
        return invitados;
    }

    @Override
    public List<AccesoCompartido> accesosDe(UUID plantaId) {
        return accesos.stream().filter(a -> a.plantaId().equals(plantaId))
                .sorted(Comparator.comparing(AccesoCompartido::otorgadoEn)).toList();
    }

    @Override
    public void otorgar(AccesoCompartido acceso) {
        boolean yaExiste = accesos.stream().anyMatch(
                a -> a.plantaId().equals(acceso.plantaId()) && a.invitadoId().equals(acceso.invitadoId()));
        if (!yaExiste) {
            accesos.add(acceso);
        }
    }

    @Override
    public void revocar(UUID plantaId, UUID invitadoId) {
        accesos.removeIf(a -> a.plantaId().equals(plantaId) && a.invitadoId().equals(invitadoId));
    }

    @Override
    public void revocarTodosDe(UUID plantaId) {
        accesos.removeIf(a -> a.plantaId().equals(plantaId));
    }

    @Override
    public void revocarTodosDelInvitado(UUID invitadoId) {
        accesos.removeIf(a -> a.invitadoId().equals(invitadoId));
    }

    public int cuantos() {
        return accesos.size();
    }
}
