// Wi-Fi: credenciales guardadas, valores de compilacion (demo) o portal cautivo con WiFiManager (flujo real).
#pragma once
#include <Arduino.h>

namespace red {
enum class Estado { Conectando, Portal, Conectada, SinServidor };

void iniciar();                // decide de donde sacar el Wi-Fi y empieza a conectar (sin bloquear)
void ciclo();                  // llamar en cada vuelta del loop
Estado estado();
bool conectada();
String servidor();             // IP o nombre del portatil: el guardado o el de compilacion
String ssidDelPortal();        // "Fito-YUBUD6"
void reconectar();             // tras perder la red
}
