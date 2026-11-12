// Adaptador de SensorDeMagnitud para cualquier sensor que entregue un voltaje en un pin del ADC1: un
// potenciometro, la sonda resistiva de humedad o la LDR. Solo cambia la curva de conversion, que es un dato (una
// tabla de calibracion) y no codigo: pasar del potenciometro al sensor real es cambiar la tabla.
#pragma once
#include "sensor_de_magnitud.h"
#include "nucleo/fito_nucleo.h"

class SensorAnalogico : public SensorDeMagnitud {
public:
    // `tabla`: `n` pares mV -> valor, ordenados por mV ascendente (ver fito_interpolar_cal). La tabla no se copia:
    // debe vivir mientras viva el sensor (en la composicion son constantes globales).
    SensorAnalogico(int pin, const fito_punto_cal *tabla, int n);
    bool leer(float &valor) override;   // un voltaje siempre se puede leer: siempre true
    void describirCrudo(char *buf, size_t n) override;   // "1234 mV"

private:
    int pin_;
    const fito_punto_cal *tabla_;
    int n_;
    int ultimoMv_ = -1;
};
