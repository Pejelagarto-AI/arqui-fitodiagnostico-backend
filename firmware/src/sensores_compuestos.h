// Adaptador del puerto Sensores armado con tres puertos de magnitud (patron compuesto). No sabe que hay detras
// de cada magnitud: potenciometros, sensores reales o una mezcla. Cambiar un sensor es cambiar el objeto que se
// le pasa en la composicion (main.cpp), sin tocar esta clase ni la maquina de estados.
#pragma once
#include "sensor_de_magnitud.h"
#include "sensores.h"

class SensoresCompuestos : public Sensores {
public:
    SensoresCompuestos(SensorDeMagnitud &humedad, SensorDeMagnitud &luz, SensorDeMagnitud &temperatura);
    bool leer(Lectura &l, const char *&descripcion) override;
    // Solo para describir la lectura (dentro del optimo o fuera); el diagnostico lo sigue haciendo el servidor.
    void adoptarRangos(const float minimo[3], const float maximo[3]) override;

private:
    SensorDeMagnitud *magnitudes_[3];
    float lo_[3];
    float hi_[3];
    bool hayRangos_ = false;
};
