#include "sensores_compuestos.h"
#include <math.h>
#include <stdio.h>

namespace {
const char *NOMBRE[3] = {"humedad", "luz", "temperatura"};
float redondear(float v) { return roundf(v * 10.0f) / 10.0f; }
}

SensoresCompuestos::SensoresCompuestos(SensorDeMagnitud &humedad, SensorDeMagnitud &luz, SensorDeMagnitud &temperatura)
    : magnitudes_{&humedad, &luz, &temperatura}, lo_{0, 0, 0}, hi_{0, 0, 0} {}

void SensoresCompuestos::adoptarRangos(const float minimo[3], const float maximo[3]) {
    for (int i = 0; i < 3; i++) {
        if (!(maximo[i] > minimo[i])) return;   // rango absurdo: no se describe nada
    }
    for (int i = 0; i < 3; i++) { lo_[i] = minimo[i]; hi_[i] = maximo[i]; }
    hayRangos_ = true;
}

bool SensoresCompuestos::leer(Lectura &l, const char *&descripcion) {
    static char buf[40];
    float v[3];
    for (int i = 0; i < 3; i++) {
        if (!magnitudes_[i]->leer(v[i])) {
            snprintf(buf, sizeof buf, "falla el sensor de %s", NOMBRE[i]);
            descripcion = buf;
            return false;                        // el servidor exige las tres magnitudes: no se envia media lectura
        }
        v[i] = redondear(v[i]);
    }
    l.humedad = v[0];
    l.luz = v[1];
    l.temperatura = v[2];

    descripcion = hayRangos_ ? "dentro del optimo" : "sin rangos todavia";
    if (hayRangos_) {
        for (int i = 0; i < 3; i++) {
            if (v[i] < lo_[i] || v[i] > hi_[i]) {
                snprintf(buf, sizeof buf, "fuera de rango en %s", NOMBRE[i]);
                descripcion = buf;
                break;
            }
        }
    }
    return true;
}
