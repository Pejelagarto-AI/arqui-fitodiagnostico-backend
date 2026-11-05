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
}
