#include "temperatura_dht22.h"
#include <Arduino.h>
#include "configuracion.h"

TemperaturaDht22::TemperaturaDht22(int pin) : dht_(pin, DHT22), pin_(pin) {}

bool TemperaturaDht22::leer(float &valor) {
    // begin() configura el pin: se hace en la primera lectura y no en el constructor, que corre antes de que
    // Arduino arranque (objeto global de la composicion).
    if (!iniciado_) {
        // Diagnostico de cableado: sin pull-up interno, la linea en reposo debe estar en ALTO (el modulo trae
        // su resistencia a VCC). En BAJO o flotando, el sensor no tiene alimentacion o el cable no llega al pin.
        pinMode(pin_, INPUT);
        delay(5);
        reposo_ = digitalRead(pin_);
        dht_.begin();
        iniciado_ = true;
    }
    uint32_t ahora = millis();
    if (!hayLectura_ || ahora - ultimaMs_ >= config::DHT_INTERVALO_MS) {
        ultimaMs_ = ahora;
        hayLectura_ = true;
        temperatura_ = dht_.readTemperature(false, true);   // Celsius; true = leer el chip ahora
        cruda_ = temperatura_;
        humedadAire_ = dht_.readHumidity(false);             // misma trama: no vuelve a leer el chip
        // Un DHT22 sano da -40 a 80 grados; fuera de eso (o NaN) es ruido en el cable.
        ultimoOk_ = !isnan(temperatura_) && temperatura_ > -40.5f && temperatura_ < 80.5f;
    }
    if (ultimoOk_) valor = temperatura_;
    return ultimoOk_;
}

void TemperaturaDht22::describirCrudo(char *buf, size_t n) {
    if (!hayLectura_) snprintf(buf, n, "sin leer");
    else if (!ultimoOk_ && isnan(cruda_)) snprintf(buf, n, "dht22 sin respuesta (linea en reposo %s)", reposo_ == 1 ? "ALTO" : "BAJO");
    else if (!ultimoOk_) snprintf(buf, n, "dht22 valor imposible %.1f C (un DHT11 leido como DHT22 da ~600)", cruda_);
    else if (isnan(humedadAire_)) snprintf(buf, n, "dht22");
    else snprintf(buf, n, "dht22 hr aire %.0f %%", humedadAire_);
}
