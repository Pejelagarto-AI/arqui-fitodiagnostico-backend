package com.vivero.fitodiagnostico.dominio.puerto.entrada;

import com.vivero.fitodiagnostico.dominio.modelo.Usuario;

import java.util.UUID;

/** Puerto de entrada: los datos de una cuenta. Lo usan también los adaptadores que verifican identidad. */
public interface ConsultarCuentaPort {

    /** @throws com.vivero.fitodiagnostico.dominio.excepcion.UsuarioNoEncontradoException si no existe */
    Usuario consultar(UUID id);
}
