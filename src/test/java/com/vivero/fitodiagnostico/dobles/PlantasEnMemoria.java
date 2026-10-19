package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.modelo.Planta;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioPlantas;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble en memoria de {@link RepositorioPlantas}. {@code compartidasCon} consulta
 * el doble de accesos que se le da, como hace el adaptador real con su join.
 */
public class PlantasEnMemoria implements RepositorioPlantas {

    private final Map<UUID, Planta> porId = new LinkedHashMap<>();
    private final AccesosEnMemoria accesos;

    public PlantasEnMemoria(AccesosEnMemoria accesos) {
        this.accesos = accesos;
    }

    @Override
    public Optional<Planta> buscar(UUID id) {
        return Optional.ofNullable(porId.get(id));
    }

    @Override
    public List<Planta> deDueno(UUID duenoId) {
        return porId.values().stream().filter(p -> p.duenoId().equals(duenoId))
                .sorted(Comparator.comparing(Planta::registradaEn).reversed()).toList();
    }

    @Override
    public List<Planta> compartidasCon(UUID usuarioId) {
        return porId.values().stream().filter(p -> accesos.invitadosDe(p.id()).contains(usuarioId))
                .sorted(Comparator.comparing(Planta::registradaEn).reversed()).toList();
    }

    @Override
    public void guardar(Planta planta) {
        porId.put(planta.id(), planta);
    }

    @Override
    public void eliminar(UUID id) {
        porId.remove(id);
    }

    public int cuantas() {
        return porId.size();
    }
}
