#include "sensor_analogico.h"
#include <Arduino.h>
#include "configuracion.h"

SensorAnalogico::SensorAnalogico(int pin, const fito_punto_cal *tabla, int n) : pin_(pin), tabla_(tabla), n_(n) {}

bool SensorAnalogico::leer(float &valor) {
    // Promedio de varias muestras: el ADC del ESP32 tiene ruido de unas decenas de milivoltios.
    uint32_t suma = 0;
    for (int i = 0; i < config::MUESTRAS_ADC; i++) suma += analogReadMilliVolts(pin_);
    int mv = (int)(suma / config::MUESTRAS_ADC);
    ultimoMv_ = mv;
    valor = fito_interpolar_cal(mv, tabla_, n_);
    return true;
}

void SensorAnalogico::describirCrudo(char *buf, size_t n) {
    if (ultimoMv_ < 0) snprintf(buf, n, "sin leer");
    else snprintf(buf, n, "%d mV", ultimoMv_);
}
