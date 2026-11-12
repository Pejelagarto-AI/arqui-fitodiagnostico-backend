package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Especie;

import java.util.List;

/** Puerto de entrada del administrador: todas las especies, activas o no (RF-C2). */
public interface ConsultarCatalogoCompletoPort {

    List<Especie> listarTodas();
}
