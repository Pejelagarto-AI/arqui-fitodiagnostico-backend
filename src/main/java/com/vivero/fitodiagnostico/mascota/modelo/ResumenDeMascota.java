package com.vivero.fitodiagnostico.mascota.modelo;

/**
 * Lo mínimo para dibujar la mascota: el tablero de plantas y la OLED (fase 13). Es un recorte de
 * {@link VistaDeMascota} sin eventos ni acción pendiente, para armarlo por lote.
 */
public record ResumenDeMascota(int vitalidad, Animo animo, int racha, Accesorio accesorio) { }
