// Constantes del firmware. Los valores de compilacion (FITO_*) vienen de include/config_local.h, generado desde local.ini.
#pragma once
#include <Arduino.h>

#include "config_local.h"   // lo genera extra_script.py desde local.ini
#include "nucleo/fito_nucleo.h"

namespace config {
constexpr int PIN_SDA = 21;
constexpr int PIN_SCL = 22;
constexpr int PIN_BOTON = 0;                    // BOOT
constexpr uint32_t ESPERA_WIFI_MS = 20000;
constexpr uint32_t ESPERA_NTP_MS = 5000;
constexpr uint32_t INTERVALO_LECTURA_MS = 5000;
constexpr uint32_t ESPERA_RECLAMO_MS = 5000;
constexpr uint32_t ESPERA_REINTENTO_MS = 10000;
constexpr uint32_t MARGEN_RENOVACION_S = 120;
constexpr uint32_t BOTON_REINICIO_MS = 5000;
constexpr uint16_t TIMEOUT_HTTP_MS = 4000;
constexpr size_t CAPACIDAD_COLA = 120;
constexpr int MAX_ENVIOS_POR_CICLO = 6;         // al vaciar la cola, para no bloquear boton ni pantalla

// Entradas de los sensores. Pines del ADC1: el ADC2 no lee con el Wi-Fi encendido. La temperatura es un
// potenciometro (analogico) o el DHT22 (digital) en el mismo pin. PIN_ALIMENTACION_HUMEDAD da el VCC de la sonda
// solo mientras se lee (AlimentadoPorPin).
#if CONFIG_IDF_TARGET_ESP32S3
constexpr int PIN_HUMEDAD = 4, PIN_LUZ = 5, PIN_TEMPERATURA = 6, PIN_ALIMENTACION_HUMEDAD = 7;
#elif CONFIG_IDF_TARGET_ESP32C3
constexpr int PIN_HUMEDAD = 0, PIN_LUZ = 1, PIN_TEMPERATURA = 3, PIN_ALIMENTACION_HUMEDAD = 4;
#else   // ESP32 clasico (esp32dev)
constexpr int PIN_HUMEDAD = 34, PIN_LUZ = 35, PIN_TEMPERATURA = 32, PIN_ALIMENTACION_HUMEDAD = 33;
#endif
constexpr int MUESTRAS_ADC = 16;                 // cada lectura analogica promedia estas muestras
constexpr uint32_t DHT_INTERVALO_MS = 2000;      // el DHT22 no admite lecturas mas seguidas
constexpr uint32_t ALIMENTACION_MS = 30;         // espera tras encender la sonda antes de leer

// Potenciometros (sensores = potenciometros): escalas fijas, como las de un sensor real; el servidor decide si el
// valor esta en el optimo de la especie. Son tablas de dos puntos: el ADC no es lineal en las puntas, asi que
// por debajo de 100 mV es el minimo de la escala y por encima de 3100 mV el maximo, y la perilla alcanza los dos.
constexpr fito_punto_cal POT_HUMEDAD[] = {{100, 0}, {3100, 100}};        // %
constexpr fito_punto_cal POT_LUZ[] = {{100, 0}, {3100, 2000}};           // lux
constexpr fito_punto_cal POT_TEMPERATURA[] = {{100, 0}, {3100, 50}};     // grados Celsius

// Sensores reales analogicos (sensores = reales): la tabla viene de cal_humedad y cal_luz en local.ini, porque
// cada sensor fisico sale distinto. Valores por defecto en platformio.ini (provisionales hasta calibrar).
constexpr fito_punto_cal CAL_HUMEDAD[] = FITO_CAL_HUMEDAD;
constexpr fito_punto_cal CAL_LUZ[] = FITO_CAL_LUZ;
}
