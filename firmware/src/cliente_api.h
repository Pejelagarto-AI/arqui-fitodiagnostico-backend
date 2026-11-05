// Cliente HTTP de la API del backend (mismas llamadas y cuerpos que el simulador).
#pragma once
#include <Arduino.h>
#include "sensores.h"

namespace api {
constexpr int SIN_RED = -1;

void iniciar(const String &servidor);

// POST /dispositivos/registro firmado con la clave de flota. 201 o 200 = ok, 401 clave mala, 409 serial con otro secreto.
int registro();

// Reto + credencial. Devuelve el codigo HTTP final (200 = token y expira rellenados; 409 = sin reclamar; 404 = el
// servidor no conoce el serial; 401 = secreto rechazado; SIN_RED).
int pedirCredencial(String &token, time_t &expira);

// Lo que el servidor dijo de la especie en la ultima respuesta 201: nombre y rangos optimos
// (indices 0 humedad, 1 luz, 2 temperatura). `hay` es false hasta la primera respuesta completa.
struct Rangos { bool hay; float minimo[3]; float maximo[3]; };
const String &especie();
const Rangos &rangos();

// POST /dispositivos/{serial}/lecturas con Bearer. `estado` = diagnostico.estado si fue 201.
int enviarLectura(const String &token, const Lectura &l, String &estado);
}
