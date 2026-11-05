// Sensores detras de una interfaz: hoy SensoresSimulados; SensoresReales (DHT22, sonda capacitiva, BH1750) despues.
#pragma once
#include <Arduino.h>
#include <time.h>

struct Lectura {
    time_t cuando;
    float humedad;
    float luz;
    float temperatura;
    uint32_t n;        // numero de lectura desde el arranque (para el registro por serie)
    bool fuera;        // fuera del rango optimo (solo informativo)
};

class Sensores {
public:
    virtual ~Sensores() {}
    // Llena `l` (sin la hora) y `descripcion` ("dentro del optimo" o "fuera de rango en humedad").
    virtual void leer(Lectura &l, const char *&descripcion) = 0;
};

// Caminata aleatoria alrededor del rango optimo del poto (como simulador/simulador_esp32.py), con una lectura
// fuera de rango de vez en cuando. Nunca produce lecturas invalidas.
class SensoresSimulados : public Sensores {
public:
    SensoresSimulados();
    void leer(Lectura &l, const char *&descripcion) override;
    // Rangos optimos de la especie (los que devuelve el servidor): 0 humedad, 1 luz, 2 temperatura. Recentra la caminata.
    void adoptarRangos(const float minimo[3], const float maximo[3]);

private:
    float actual_[3];
    float lo_[3];
    float hi_[3];
    float normal(int i);
};

// Hueco para el hardware real: implementar leer() con DHT22 (temperatura), sonda capacitiva (humedad) y BH1750 (luz).
// class SensoresReales : public Sensores { ... };
