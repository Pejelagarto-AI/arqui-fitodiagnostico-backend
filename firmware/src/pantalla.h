// OLED SSD1306 128x64 I2C. Cada funcion pinta una pantalla completa. Sin tildes: la fuente de Adafruit GFX es CP437.
// El usuario ve el CODIGO de 6 caracteres (YUBUD6) solo en el reclamo; en lecturas y vinculada ve el NOMBRE (apodo, UTF-8,
// se pasa a ASCII aqui). Nunca el prefijo "fito-" del serial.
#pragma once
#include <Arduino.h>
#include "sensores.h"

namespace pantalla {
bool iniciar();                                         // escanea 0x3C y 0x3D; false si no responde ninguna
// Cada 2 s: si la OLED falta, la busca y la inicializa; si estaba lista, comprueba que siga respondiendo (ACK).
// Devuelve true cuando acaba de aparecer: el llamador debe redibujar la pantalla de la fase actual.
bool reintentarSiFalta(uint32_t ahoraMs);
void arranque(const char *serial);
void mensaje(const char *l1, const char *l2 = "", const char *l3 = "", const char *l4 = "");
void portal(const char *redWifi);                       // QR de Wi-Fi a la izquierda + "Conecta tu celular a" + la red
// QR a la izquierda (64x64) y el codigo a la derecha. Si el enlace no cabe en la version 3, solo el codigo.
void qr(const String &enlace, const char *codigo, uint8_t puntos);
void animarEspera(uint32_t ahoraMs);                    // puntos de espera de la pantalla de reclamo (sin repintar el QR)
void empezarExito(uint32_t ahoraMs, const char *nombre);
bool animarExito(uint32_t ahoraMs);                     // true mientras la animacion siga; no bloquea
void vinculada(const char *nombre);                     // pantalla final estatica: visto + "Vinculada"
// `especie` decide el sprite (carnivora si contiene "atrapamoscas" o "carniv"); `estado` es el del servidor.
void lecturas(const char *nombre, const Lectura &l, const char *estado, bool punto, const char *especie);
void animarLecturas(uint32_t ahoraMs);                  // alterna cuadros de la planta; solo repinta si cambia el cuadro
void mordisco(uint32_t ahoraMs);                        // la carnivora muerde (respuesta ok del servidor)
void sinRed(size_t guardadas);
void error(const char *l1, const char *l2 = "", const char *l3 = "");
void reinicio(int segundos);
}
