package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.modelo.SolicitudEspecie;
import com.vivero.fitodiagnostico.dominio.puerto.RepositorioSolicitudes;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble en memoria de {@link RepositorioSolicitudes}. */
public class SolicitudesEnMemoria implements RepositorioSolicitudes {

    private final Map<UUID, SolicitudEspecie> porId = new LinkedHashMap<>();

    @Override
    public void guardar(SolicitudEspecie solicitud) {
        porId.put(solicitud.id(), solicitud);
    }

    @Override
    public Optional<SolicitudEspecie> buscar(UUID id) {
        return Optional.ofNullable(porId.get(id));
    }

    @Override
    public List<SolicitudEspecie> pendientes() {
        return porId.values().stream().filter(SolicitudEspecie::estaPendiente)
                .sorted(Comparator.comparing(SolicitudEspecie::creadaEn)).toList();
    }

    @Override
    public List<SolicitudEspecie> deSolicitante(UUID solicitanteId) {
        return porId.values().stream().filter(s -> s.solicitanteId().equals(solicitanteId))
                .sorted(Comparator.comparing(SolicitudEspecie::creadaEn).reversed()).toList();
    }

    @Override
    public void eliminarDe(UUID solicitanteId) {
        porId.values().removeIf(s -> s.solicitanteId().equals(solicitanteId));
    }

    public int cuantas() {
        return porId.size();
    }
}
