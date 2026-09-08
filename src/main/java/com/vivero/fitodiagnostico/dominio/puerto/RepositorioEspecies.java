package com.vivero.fitodiagnostico.dominio.puerto;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;
import java.util.Optional;

/** Puerto de salida. Lo declara el dominio; lo implementa la infraestructura. */
public interface RepositorioEspecies {

    Optional<Especie> buscarPorNombreCientifico(String nombreCientifico);
}
