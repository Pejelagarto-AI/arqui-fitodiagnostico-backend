// Identidad y estado persistente en NVS (Preferences). La identidad (MAC, serial, secreto) nunca la borra el boton.
#pragma once
#include <Arduino.h>

namespace identidad {
void iniciar();                       // lee o crea la identidad
const char *serial();                 // "fito-YUBUD6"
const char *codigo();                 // "YUBUD6" (sin el prefijo)
const char *secreto();
String macTexto();
bool registrado();
void marcarRegistrado(bool v);

String wifiSsid();
String wifiClave();
String servidor();
void guardarRed(const String &ssid, const String &clave);
void guardarServidor(const String &servidor);

String token();
time_t tokenExpira();                 // epoch, 0 si no hay
void guardarToken(const String &token, time_t expira);
void borrarToken();                   // tambien borra el apodo: desvinculada, ya no hay nombre que mostrar
String apodo();                       // nombre de la planta (UTF-8), "" si nunca hubo
void guardarApodo(const String &apodo);

void olvidarRedYToken();              // lo que hace el boton: Wi-Fi, servidor, token y registro de red; NO la identidad
}
