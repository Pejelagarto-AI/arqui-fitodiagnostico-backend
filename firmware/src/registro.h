// Salida por serie con el estilo del simulador: "[2026-11-05T16:15:00Z] mensaje".
#pragma once
#include <Arduino.h>
void registrar(const char *formato, ...) __attribute__((format(printf, 1, 2)));
