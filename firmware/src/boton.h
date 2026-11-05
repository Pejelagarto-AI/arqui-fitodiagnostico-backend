// Boton BOOT (GPIO 0) sostenido 5 s: borra Wi-Fi, servidor y token (no la identidad) y reinicia.
#pragma once
#include <Arduino.h>

namespace boton {
void iniciar();
// Sin bloquear. Devuelve los ms que lleva presionado (0 si esta suelto); true en `cumplido` al llegar a 5 s.
uint32_t ciclo(bool &cumplido);
}
