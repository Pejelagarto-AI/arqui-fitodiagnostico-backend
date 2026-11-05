#include "sensores.h"
#include <esp_system.h>

namespace {
// poto: humedad 40-70, luz 300-1200, temperatura 18-30
const float LO[3] = {40, 300, 18};
const float HI[3] = {70, 1200, 30};
const float PISO[3] = {0, 0, -20};
const float TECHO[3] = {100, 150000, 60};
const char *NOMBRE[3] = {"humedad", "luz", "temperatura"};

float uniforme() { return (esp_random() >> 8) / 16777216.0f; }                 // [0,1)
float gauss(float sigma) { float s = 0; for (int i = 0; i < 12; i++) s += uniforme(); return (s - 6.0f) * sigma; }
float redondear(float v) { return roundf(v * 10.0f) / 10.0f; }
}

SensoresSimulados::SensoresSimulados() {
    for (int i = 0; i < 3; i++) actual_[i] = (LO[i] + HI[i]) / 2;
}

float SensoresSimulados::normal(int i) {
    float ancho = HI[i] - LO[i];
    float v = actual_[i] + gauss(ancho * 0.05f);
    v = min(max(v, LO[i] + ancho * 0.05f), HI[i] - ancho * 0.05f);
    actual_[i] = v;
    return v;
}

void SensoresSimulados::leer(Lectura &l, const char *&descripcion) {
    float v[3];
    for (int i = 0; i < 3; i++) v[i] = normal(i);
    descripcion = "dentro del optimo";
    if (uniforme() < 0.15f) {                      // una magnitud sale del optimo, pero sigue siendo fisicamente valida
        int i = esp_random() % 3;
        float ancho = HI[i] - LO[i];
        float d = ancho * (0.2f + 0.7f * uniforme());
        v[i] = (esp_random() & 1) ? max(PISO[i], LO[i] - d) : min(TECHO[i], HI[i] + d);
        static char buf[40];
        snprintf(buf, sizeof buf, "fuera de rango en %s", NOMBRE[i]);
        descripcion = buf;
    }
    l.humedad = redondear(v[0]);
    l.luz = redondear(v[1]);
    l.temperatura = redondear(v[2]);
}
