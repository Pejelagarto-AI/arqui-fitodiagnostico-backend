// Puerto de salida de UNA magnitud (humedad, luz o temperatura). Cada dispositivo fisico la implementa con su
// propio adaptador: un potenciometro, la sonda resistiva o la LDR (SensorAnalogico) o el DHT22. Un dispositivo que
// mide dos magnitudes (el DHT22: temperatura y humedad del aire) se expone como objetos que comparten el chip,
// para que cada magnitud se pueda cambiar por separado.
#pragma once
#include <stddef.h>

class SensorDeMagnitud {
public:
    virtual ~SensorDeMagnitud() {}
    // Devuelve false si esta vez no se pudo medir (un sensor real puede fallar o no responder a tiempo).
    // `valor` queda en la unidad que espera el servidor: % de humedad, lux o grados Celsius.
    virtual bool leer(float &valor) = 0;
    // Lo que midio el sensor ANTES de convertir, para el modo calibracion (por ejemplo "2950 mV").
    // Describe la ultima llamada a leer(). Por defecto no hay nada que mostrar.
    virtual void describirCrudo(char *buf, size_t n) { if (n) buf[0] = '\0'; }
};
