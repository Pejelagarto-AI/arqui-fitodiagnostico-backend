// Puerto de salida del firmware: de donde salen las lecturas. La maquina de estados (main.cpp) solo conoce esta
// interfaz. Adaptadores: SensoresSimulados (caminata aleatoria) y SensoresCompuestos (tres SensorDeMagnitud:
// potenciometros hoy, DHT22, sonda capacitiva o BH1750 despues). Cual se usa lo decide la composicion en main.cpp.
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
    // Devuelve false si esta vez no hay lectura completa (un sensor real fallo); `descripcion` dice por que.
    virtual bool leer(Lectura &l, const char *&descripcion) = 0;
    // Rangos optimos de la especie que devuelve el servidor: 0 humedad, 1 luz, 2 temperatura.
    // Por defecto se ignoran; cada adaptador decide si le sirven.
    virtual void adoptarRangos(const float minimo[3], const float maximo[3]) { (void)minimo; (void)maximo; }
};

// Caminata aleatoria alrededor del rango optimo del poto (como simulador/simulador_esp32.py), con una lectura
// fuera de rango de vez en cuando. Nunca produce lecturas invalidas.
class SensoresSimulados : public Sensores {
public:
    SensoresSimulados();
    bool leer(Lectura &l, const char *&descripcion) override;   // nunca falla: siempre true
    // Recentra la caminata en el medio del rango optimo de la especie.
    void adoptarRangos(const float minimo[3], const float maximo[3]) override;

private:
    float actual_[3];
    float lo_[3];
    float hi_[3];
    float normal(int i);
};

