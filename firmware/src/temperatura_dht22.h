// Adaptador de SensorDeMagnitud para la temperatura con un DHT22 (AM2302): protocolo digital de un cable, con la
// libreria "DHT sensor library" de Adafruit. Unica clase de sensor nueva de los sensores reales: la sonda y la LDR
// son SensorAnalogico con otra tabla.
//
// El DHT22 tambien mide la humedad DEL AIRE. No se envia como humedad: la que pide el servidor es la del
// sustrato (la sonda). Solo aparece en el modo calibracion, como dato crudo.
#pragma once
#include <DHT.h>
#include "sensor_de_magnitud.h"

class TemperaturaDht22 : public SensorDeMagnitud {
public:
    explicit TemperaturaDht22(int pin);
    // false si el chip no responde (cable suelto, sin alimentacion, checksum malo): esa lectura se descarta.
    // Entre dos lecturas al chip pasan al menos 2 s (lo exige el DHT22); antes se repite el ultimo resultado.
    bool leer(float &valor) override;
    void describirCrudo(char *buf, size_t n) override;   // "hr aire 55 %" o "sin respuesta"

private:
    DHT dht_;
    bool iniciado_ = false;
    bool hayLectura_ = false;
    uint32_t ultimaMs_ = 0;
    bool ultimoOk_ = false;
    float temperatura_ = NAN;
    float humedadAire_ = NAN;
};
