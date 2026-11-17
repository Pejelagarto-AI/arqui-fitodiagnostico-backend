// Lo que el servidor dice de la mascota en la 201 de la lectura (clave "mascota", fase 14). La llena cliente_api y la
// consume pantalla. Los numeros son los de nucleo/fito_mascota.h (FITO_ANIMO_*, FITO_ACC_*, FITO_EV_*).
#pragma once
#include <stdint.h>
#include "nucleo/fito_mascota.h"

struct EstadoMascota {
    bool hay = false;              // false = el servidor no mando "mascota" (servidor viejo): todo sigue como antes
    uint8_t vitalidad = 0;         // 0..100
    int8_t animo = -1;             // FITO_ANIMO_*; -1 si el texto no se conoce
    int8_t accesorio = 0;          // FITO_ACC_*
    uint8_t nEventos = 0;          // del mas antiguo al mas reciente, como llegan
    int8_t eventos[FITO_COLA_EV_MAX] = {};   // FITO_EV_*; -1 = tipo desconocido (se ignora al encolar)
};
