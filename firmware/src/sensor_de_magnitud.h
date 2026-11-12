// Puerto de salida de UNA magnitud (humedad, luz o temperatura). Cada dispositivo fisico la implementa con su
// propio adaptador: hoy un potenciometro; despues una sonda capacitiva (humedad), un BH1750 (luz) o un DHT22.
// Un dispositivo que mide dos magnitudes (el DHT22: temperatura y humedad) se expone como dos objetos que
// comparten el mismo chip, para que cada magnitud se pueda cambiar por separado.
#pragma once

class SensorDeMagnitud {
public:
    virtual ~SensorDeMagnitud() {}
    // Devuelve false si esta vez no se pudo medir (un sensor real puede fallar o no responder a tiempo).
    // `valor` queda en la unidad que espera el servidor: % de humedad, lux o grados Celsius.
    virtual bool leer(float &valor) = 0;
};
