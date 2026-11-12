#include "temperatura_dht22.h"
#include <Arduino.h>
#include "configuracion.h"

TemperaturaDht22::TemperaturaDht22(int pin) : dht_(pin, DHT22) {}

bool TemperaturaDht22::leer(float &valor) {
    // begin() configura el pin: se hace en la primera lectura y no en el constructor, que corre antes de que
    // Arduino arranque (objeto global de la composicion).
    if (!iniciado_) { dht_.begin(); iniciado_ = true; }
    uint32_t ahora = millis();
    if (!hayLectura_ || ahora - ultimaMs_ >= config::DHT_INTERVALO_MS) {
        ultimaMs_ = ahora;
        hayLectura_ = true;
        temperatura_ = dht_.readTemperature(false, true);   // Celsius; true = leer el chip ahora
        humedadAire_ = dht_.readHumidity(false);             // misma trama: no vuelve a leer el chip
        // Un DHT22 sano da -40 a 80 grados; fuera de eso (o NaN) es ruido en el cable.
        ultimoOk_ = !isnan(temperatura_) && temperatura_ > -40.5f && temperatura_ < 80.5f;
    }
    if (ultimoOk_) valor = temperatura_;
    return ultimoOk_;
}

void TemperaturaDht22::describirCrudo(char *buf, size_t n) {
    if (!hayLectura_) snprintf(buf, n, "sin leer");
    else if (!ultimoOk_) snprintf(buf, n, "dht22 sin respuesta");
    else if (isnan(humedadAire_)) snprintf(buf, n, "dht22");
    else snprintf(buf, n, "dht22 hr aire %.0f %%", humedadAire_);
}
