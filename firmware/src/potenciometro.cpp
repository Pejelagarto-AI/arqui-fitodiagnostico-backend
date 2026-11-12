#include "potenciometro.h"
#include <Arduino.h>
#include "configuracion.h"
#include "nucleo/fito_nucleo.h"

Potenciometro::Potenciometro(int pin, float minimo, float maximo) : pin_(pin), minimo_(minimo), maximo_(maximo) {}

bool Potenciometro::leer(float &valor) {
    // Promedio de varias muestras: el ADC del ESP32 tiene ruido de unas decenas de milivoltios.
    uint32_t suma = 0;
    for (int i = 0; i < config::POT_MUESTRAS; i++) suma += analogReadMilliVolts(pin_);
    int mv = (int)(suma / config::POT_MUESTRAS);
    ultimoMv_ = mv;
    valor = fito_escalar_mv(mv, config::POT_MV_MIN, config::POT_MV_MAX, minimo_, maximo_);
    return true;
}

void Potenciometro::describirCrudo(char *buf, size_t n) {
    if (ultimoMv_ < 0) snprintf(buf, n, "sin leer");
    else snprintf(buf, n, "%d mV", ultimoMv_);
}
