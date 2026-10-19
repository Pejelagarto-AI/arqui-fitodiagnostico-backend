package com.vivero.fitodiagnostico.dominio.puerto.salida;

import com.vivero.fitodiagnostico.dominio.modelo.TokenDeSesion;
import com.vivero.fitodiagnostico.dominio.modelo.Usuario;

/**
 * Puerto de salida: "dame una prueba de sesión para este usuario" (RF-A2).
 * Existe para que {@code ServicioCuentas} no nombre ninguna librería de
 * tokens. La validación del token en cada petición no pasa por aquí: es un
 * filtro de seguridad del borde HTTP, no un caso de uso.
 */
public interface EmisorDeTokens {

    TokenDeSesion emitir(Usuario usuario);
}
