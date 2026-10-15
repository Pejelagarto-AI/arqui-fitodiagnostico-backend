package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.modelo.EstadoInvitacion;
import com.vivero.fitodiagnostico.dominio.modelo.Invitacion;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioInvitaciones;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble en memoria de {@link RepositorioInvitaciones}. */
public class InvitacionesEnMemoria implements RepositorioInvitaciones {

    private final Map<UUID, Invitacion> porId = new LinkedHashMap<>();

    @Override
    public void guardar(Invitacion invitacion) {
        porId.put(invitacion.id(), invitacion);
    }

    @Override
    public Optional<Invitacion> porHuella(String huellaDelToken) {
        return porId.values().stream().filter(i -> i.huellaDelToken().equals(huellaDelToken)).findFirst();
    }

    @Override
    public Optional<Invitacion> buscar(UUID id) {
        return Optional.ofNullable(porId.get(id));
    }

    @Override
    public List<Invitacion> pendientesDe(UUID plantaId) {
        return porId.values().stream()
                .filter(i -> i.plantaId().equals(plantaId) && i.estado() == EstadoInvitacion.PENDIENTE)
                .sorted(Comparator.comparing(Invitacion::creadaEn).reversed()).toList();
    }

    @Override
    public void eliminarDe(UUID plantaId) {
        porId.values().removeIf(i -> i.plantaId().equals(plantaId));
    }

    public int cuantas() {
        return porId.size();
    }

    public List<Invitacion> todas() {
        return List.copyOf(porId.values());
    }
}
