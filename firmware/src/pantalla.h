// OLED SSD1306 128x64 I2C. Cada funcion pinta una pantalla completa. Sin tildes: la fuente de Adafruit GFX es CP437.
// El usuario ve el CODIGO de 6 caracteres (YUBUD6) solo en el reclamo; en lecturas y vinculada ve el NOMBRE (apodo, UTF-8,
// se pasa a ASCII aqui). Nunca el prefijo "fito-" del serial.
#pragma once
#include <Arduino.h>
#include "sensores.h"
#include "mascota.h"

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
// `especie` decide la mascota (familiaDe en pantalla.cpp: carnivora, girasol, ... o la planta comun); `estado` es el del servidor.
// `noche` (fase 11): el diagnostico dijo periodo NOCHE; la mascota duerme y la fila de la luz dice "duerme".
void lecturas(const char *nombre, const Lectura &l, const char *estado, bool punto, const char *especie, bool noche);
// Fase 10: un sensor no responde. Pinta, sin esperar al servidor, "no te siento <sensor>" (o "no siento mis sensores" con
// dos o tres), la mascota preocupada y "hace X" desde la ultima lectura buena (`ultimaBuenaMs` es un millis()). Dura hasta
// la siguiente pantalla::lecturas(), que es una lectura completa. `falla[i]`: 0 humedad, 1 luz, 2 temperatura.
void sensoresFallan(const char *nombre, const char *especie, const bool falla[3], bool hayBuena, uint32_t ultimaBuenaMs);
void animarLecturas(uint32_t ahoraMs);                  // alterna cuadros de la planta; solo repinta si cambia el cuadro
void mordisco(uint32_t ahoraMs);                        // la carnivora muerde (respuesta ok del servidor)
// Fase 11: la mascota saluda (pulsacion corta de BOOT). Salto y parpadeo doble de 600 ms con la cara contenta, el
// bloque SALUDO del front. Solo en la pantalla de lecturas; no habla con el servidor.
void saludar(uint32_t ahoraMs);
// Fase 14: lo que el servidor dijo de la mascota en la 201 (animo, vitalidad, accesorio y los eventos nuevos, que se encolan
// y se reproducen de a uno). No pinta: la siguiente pantalla::lecturas() / animarLecturas() lo muestra. `m.hay` false = servidor viejo.
void mascota(const EstadoMascota &m, uint32_t ahoraMs);
void sinRed(size_t guardadas);
void error(const char *l1, const char *l2 = "", const char *l3 = "");
void reinicio(int segundos);
}
