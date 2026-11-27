package com.vivero.fitodiagnostico.dominio.modelo;

/**
 * Si la planta está de día o de noche cuando se evaluó (D121). De noche la luz
 * no cuenta en contra del estado global: la planta duerme. Desde la fase 16 (D205) lo
 * decide {@code ReglaDeLuzAcumulada} con el reloj: de 06:00 a 18:00 hora de Bogotá es
 * {@link #DIA}, el resto {@link #NOCHE}. El diagnóstico suelto, sin hora de lectura,
 * dice {@link #DIA} (D207).
 */
public enum Periodo {
    DIA,
    NOCHE
}
