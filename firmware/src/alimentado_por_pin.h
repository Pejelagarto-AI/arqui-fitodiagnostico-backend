// Decorador de SensorDeMagnitud: alimenta el sensor desde un pin solo mientras se lee. La sonda resistiva de
// humedad se corroe por electrolisis si queda con corriente todo el tiempo; asi pasa apagada casi siempre.
// Envuelve cualquier SensorDeMagnitud sin modificarlo (el SensorAnalogico de la sonda no sabe nada de esto).
//
// Si el VCC del modulo va al riel de 3V3 en lugar del pin, todo sigue funcionando: el pin cambia sin efecto y la
// sonda queda alimentada siempre (y se corroe antes).
#pragma once
#include <stdint.h>
#include "sensor_de_magnitud.h"

class AlimentadoPorPin : public SensorDeMagnitud {
public:
    // `pin` alimenta el modulo (VCC); `msEstabilizar` es lo que se espera tras encenderlo antes de leer.
    AlimentadoPorPin(SensorDeMagnitud &sensor, int pin, uint32_t msEstabilizar);
    bool leer(float &valor) override;                        // enciende, espera, lee el sensor envuelto y apaga
    void describirCrudo(char *buf, size_t n) override;       // el del sensor envuelto

private:
    SensorDeMagnitud &sensor_;
    int pin_;
    uint32_t ms_;
    bool iniciado_ = false;
};
