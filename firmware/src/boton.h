// Boton BOOT (GPIO 0) sostenido 5 s: borra Wi-Fi, servidor y token (no la identidad) y reinicia. Una pulsacion corta
// (soltar antes de config::BOTON_CORTA_MS) hace saludar a la mascota (fase 11).
#pragma once
#include <Arduino.h>

namespace boton {
void iniciar();
// Sin bloquear. Devuelve los ms que lleva presionado (0 si esta suelto); true en `cumplido` al llegar a 5 s.
// `corta` es true en el ciclo en que se suelta una pulsacion corta (fito_pulsacion_corta: de 30 ms a BOTON_CORTA_MS).
uint32_t ciclo(bool &cumplido, bool &corta);
}
