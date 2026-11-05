#include "sensores.h"
#include <esp_system.h>

namespace {
// Hasta la primera respuesta del servidor: poto, humedad 40-70, luz 300-1200, temperatura 18-30
const float LO_POTO[3] = {40, 300, 18};
const float HI_POTO[3] = {70, 1200, 30};
const float PISO[3] = {0, 0, -20};
const float TECHO[3] = {100, 150000, 60};
const char *NOMBRE[3] = {"humedad", "luz", "temperatura"};

float uniforme() { return (esp_random() >> 8) / 16777216.0f; }                 // [0,1)
float gauss(float sigma) { float s = 0; for (int i = 0; i < 12; i++) s += uniforme(); return (s - 6.0f) * sigma; }
float redondear(float v) { return roundf(v * 10.0f) / 10.0f; }
}

SensoresSimulados::SensoresSimulados() {
    for (int i = 0; i < 3; i++) { lo_[i] = LO_POTO[i]; hi_[i] = HI_POTO[i]; actual_[i] = (lo_[i] + hi_[i]) / 2; }
}

void SensoresSimulados::adoptarRangos(const float minimo[3], const float maximo[3]) {
    for (int i = 0; i < 3; i++) {
        if (!(maximo[i] > minimo[i])) continue;                       // rango absurdo: se ignora
        if (minimo[i] == lo_[i] && maximo[i] == hi_[i]) continue;
        lo_[i] = minimo[i]; hi_[i] = maximo[i];
        actual_[i] = (lo_[i] + hi_[i]) / 2;                          // se recentra en el medio del rango nuevo
    }
}

float SensoresSimulados::normal(int i) {
    float ancho = hi_[i] - lo_[i];
    float medio = (hi_[i] + lo_[i]) / 2;
    float v = actual_[i] + (medio - actual_[i]) * 0.2f + gauss(ancho * 0.05f);   // camina cerca del medio del optimo
    v = min(max(v, lo_[i] + ancho * 0.05f), hi_[i] - ancho * 0.05f);
    actual_[i] = v;
    return v;
}

void SensoresSimulados::leer(Lectura &l, const char *&descripcion) {
    float v[3];
    for (int i = 0; i < 3; i++) v[i] = normal(i);
    descripcion = "dentro del optimo";
    if (uniforme() < 0.12f) {                      // una magnitud sale del optimo, pero sigue siendo fisicamente valida
        int i = esp_random() % 3;
        float ancho = hi_[i] - lo_[i];
        float d = ancho * (0.2f + 0.7f * uniforme());
        bool abajo = (esp_random() & 1) != 0;
        if (abajo && lo_[i] - d < PISO[i]) abajo = false;            // no hay debajo: sale por arriba
        if (!abajo && hi_[i] + d > TECHO[i]) abajo = lo_[i] - d >= PISO[i];
        v[i] = abajo ? max(PISO[i], lo_[i] - d) : min(TECHO[i], hi_[i] + d);
        static char buf[40];
        snprintf(buf, sizeof buf, "fuera de rango en %s", NOMBRE[i]);
        descripcion = buf;
    }
    l.humedad = redondear(v[0]);
    l.luz = redondear(v[1]);
    l.temperatura = redondear(v[2]);
}
