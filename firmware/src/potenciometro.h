// Adaptador de SensorDeMagnitud con un potenciometro en un pin del ADC1: hace de sensor mientras no hay
// sensores reales. La perilla recorre una escala fija, como lo haria un sensor de verdad: quien decide si el
// valor esta bien o mal es el servidor, no el potenciometro.
#pragma once
#include "sensor_de_magnitud.h"

class Potenciometro : public SensorDeMagnitud {
public:
    // `minimo` en un extremo de la perilla y `maximo` en el otro, en la unidad de la magnitud.
    Potenciometro(int pin, float minimo, float maximo);
    bool leer(float &valor) override;   // un potenciometro siempre responde: siempre true

private:
    int pin_;
    float minimo_;
    float maximo_;
};
