package com.vivero.fitodiagnostico.dobles;

import com.vivero.fitodiagnostico.salud.AvisadorDeSalud;
import com.vivero.fitodiagnostico.salud.RepositorioDeSalud;
import com.vivero.fitodiagnostico.salud.SaludDelDispositivo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Doble en memoria del repositorio de salud y del avisador (graba lo que se avisó). */
public class SaludEnMemoria implements RepositorioDeSalud, AvisadorDeSalud {

    private final Map<String, SaludDelDispositivo> filas = new LinkedHashMap<>();
    public final List<Aviso> avisos = new ArrayList<>();

    @Override
    public void guardar(SaludDelDispositivo salud) {
        filas.put(salud.serial(), salud);
    }

    @Override
    public Optional<SaludDelDispositivo> de(String serial) {
        return Optional.ofNullable(filas.get(serial));
    }

    @Override
    public List<SaludDelDispositivo> de(Collection<String> seriales) {
        return seriales.stream().map(filas::get).filter(java.util.Objects::nonNull).toList();
    }

    @Override
    public void eliminar(String serial) {
        filas.remove(serial);
    }

    @Override
    public void avisar(Aviso aviso) {
        avisos.add(aviso);
    }
}
