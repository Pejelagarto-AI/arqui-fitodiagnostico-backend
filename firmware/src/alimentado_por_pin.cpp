#include "alimentado_por_pin.h"
#include <Arduino.h>

AlimentadoPorPin::AlimentadoPorPin(SensorDeMagnitud &sensor, int pin, uint32_t msEstabilizar)
    : sensor_(sensor), pin_(pin), ms_(msEstabilizar) {}

bool AlimentadoPorPin::leer(float &valor) {
    if (!iniciado_) { pinMode(pin_, OUTPUT); iniciado_ = true; }   // no en el constructor: corre antes de Arduino
    digitalWrite(pin_, HIGH);
    delay(ms_);                    // unos milisegundos: el modulo carga su condensador y el comparador se asienta
    bool ok = sensor_.leer(valor);
    digitalWrite(pin_, LOW);
    return ok;
}

void AlimentadoPorPin::describirCrudo(char *buf, size_t n) { sensor_.describirCrudo(buf, n); }
