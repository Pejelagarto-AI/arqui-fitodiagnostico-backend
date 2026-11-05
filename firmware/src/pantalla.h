// OLED SSD1306 128x64 I2C. Cada funcion pinta una pantalla completa. Sin tildes: la fuente de Adafruit GFX es CP437.
#pragma once
#include <Arduino.h>
#include "sensores.h"

namespace pantalla {
bool iniciar();                                         // escanea 0x3C y 0x3D; false si no responde ninguna
void arranque(const char *serial);
void mensaje(const char *l1, const char *l2 = "", const char *l3 = "", const char *l4 = "");
void portal(const char *redWifi);                       // "Conectate al Wi-Fi Fito-XXXXXX"
// QR a la izquierda (64x64) y el serial a la derecha. Si el enlace no cabe en la version 3, serial grande.
void qr(const String &enlace, const char *codigo, uint8_t puntos);
void vinculada();
void lecturas(const char *serial, const Lectura &l, const char *estado, bool punto);
void sinRed(size_t guardadas);
void error(const char *l1, const char *l2 = "", const char *l3 = "");
void reinicio(int segundos);
}
