package com.vivero.fitodiagnostico.dominio.puerto.salida;

import com.vivero.fitodiagnostico.dominio.modelo.TokenDeDispositivo;

import java.util.UUID;

/**
 * Puerto de salida: "dame una credencial para que este dispositivo reporte lecturas de esta
 * planta". Es el gemelo de {@link EmisorDeTokens} (que es para personas) con un alcance
 * distinto: el token que devuelve solo sirve para reportar lecturas y solo de esa planta.
 * Quién puede pedirlo (el dueño) lo decide la aplicación; cómo se firma, el adaptador.
 */
public interface EmisorTokenDispositivoPort {

    TokenDeDispositivo emitir(String dispositivoId, UUID plantaId);
}
