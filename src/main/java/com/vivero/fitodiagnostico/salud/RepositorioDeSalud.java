package com.vivero.fitodiagnostico.salud;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Puerto de salida: dónde se guarda la salud de cada dispositivo (una fila por serial). */
public interface RepositorioDeSalud {

    void guardar(SaludDelDispositivo salud);

    Optional<SaludDelDispositivo> de(String serial);

    List<SaludDelDispositivo> de(Collection<String> seriales);
}
