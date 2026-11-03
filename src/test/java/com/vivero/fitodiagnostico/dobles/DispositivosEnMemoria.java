package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.dominio.modelo.Dispositivo;
import com.vivero.fitodiagnostico.dominio.puerto.salida.RepositorioDispositivos;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Doble en memoria de {@link RepositorioDispositivos}. Guarda el MISMO objeto que recibe (como no hay
 * base, no hay copia): las pruebas que necesitan ver el estado persistido lo piden con {@link #porSerial}.
 */
public class DispositivosEnMemoria implements RepositorioDispositivos {

    private final Map<String, Dispositivo> porSerial = new LinkedHashMap<>();

    @Override
    public void guardar(Dispositivo dispositivo) {
        porSerial.put(dispositivo.serial(), dispositivo);
    }

    @Override
    public Optional<Dispositivo> porSerial(String serial) {
        return Optional.ofNullable(porSerial.get(serial));
    }

    @Override
    public List<Dispositivo> porPlanta(UUID plantaId) {
        return porSerial.values().stream().filter(d -> d.plantaId().filter(plantaId::equals).isPresent())
                .sorted(Comparator.comparing(Dispositivo::creadoEn)).toList();
    }

    @Override
    public List<Dispositivo> todos() {
        return List.copyOf(porSerial.values());
    }

    public int cuantos() {
        return porSerial.size();
    }
}
