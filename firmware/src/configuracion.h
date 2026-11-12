// Constantes del firmware. Los valores de compilacion (FITO_*) vienen de include/config_local.h, generado desde local.ini.
#pragma once
#include <Arduino.h>

#include "config_local.h"   // lo genera extra_script.py desde local.ini

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

// Potenciometros (sensores = potenciometros en local.ini). Pines del ADC1: el ADC2 no lee con el Wi-Fi encendido.
#if CONFIG_IDF_TARGET_ESP32S3
constexpr int PIN_POT_HUMEDAD = 4, PIN_POT_LUZ = 5, PIN_POT_TEMPERATURA = 6;
#elif CONFIG_IDF_TARGET_ESP32C3
constexpr int PIN_POT_HUMEDAD = 0, PIN_POT_LUZ = 1, PIN_POT_TEMPERATURA = 3;
#else   // ESP32 clasico (esp32dev)
constexpr int PIN_POT_HUMEDAD = 34, PIN_POT_LUZ = 35, PIN_POT_TEMPERATURA = 32;
#endif
// El ADC no es lineal en las puntas: por debajo de POT_MV_MIN es el minimo de la escala y por encima de POT_MV_MAX
// el maximo, asi la perilla alcanza los dos extremos.
constexpr int POT_MV_MIN = 100;
constexpr int POT_MV_MAX = 3100;
constexpr int POT_MUESTRAS = 16;
// Escalas fijas, como las de un sensor real; el servidor decide si el valor esta en el optimo de la especie.
constexpr float ESCALA_HUMEDAD_MIN = 0, ESCALA_HUMEDAD_MAX = 100;        // %
constexpr float ESCALA_LUZ_MIN = 0, ESCALA_LUZ_MAX = 2000;               // lux
constexpr float ESCALA_TEMPERATURA_MIN = 0, ESCALA_TEMPERATURA_MAX = 50; // grados Celsius
}
