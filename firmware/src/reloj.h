// Hora: NTP con respaldo en la cabecera Date de la primera respuesta HTTP del backend (D91).
#pragma once
#include <Arduino.h>
#include <time.h>

namespace reloj {
void iniciarNtp();
bool hayHora();                                   // el reloj ya esta en hora (posterior a 2023)
bool desdeCabeceraDate(const char *date);         // "Thu, 05 Nov 2026 16:15:00 GMT"
bool parsearIso(const char *texto, time_t *epoch); // "2026-11-05T16:15:00.123456789Z"
void iso(time_t t, char *salida, size_t n);        // "2026-11-05T16:15:00Z"
}
