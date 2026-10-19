package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.modelo.SolicitudRestablecimiento;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioRestablecimientos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Doble en memoria de {@link RepositorioRestablecimientos}. */
public class RestablecimientosEnMemoria implements RepositorioRestablecimientos {

    private final Map<UUID, SolicitudRestablecimiento> porId = new LinkedHashMap<>();

    @Override
    public void guardar(SolicitudRestablecimiento solicitud) {
        porId.put(solicitud.id(), solicitud);
    }

    @Override
    public Optional<SolicitudRestablecimiento> porHuella(String huellaDelToken) {
        return porId.values().stream().filter(s -> s.huellaDelToken().equals(huellaDelToken)).findFirst();
    }

    @Override
    public void eliminarDe(UUID usuarioId) {
        porId.values().removeIf(s -> s.usuarioId().equals(usuarioId));
    }

    public int cuantas() {
        return porId.size();
    }

    public List<SolicitudRestablecimiento> todas() {
        return List.copyOf(porId.values());
    }
}
