#include "registro.h"
#include "reloj.h"
#include <stdarg.h>

void registrar(const char *formato, ...) {
    char cuerpo[200];
    va_list args;
    va_start(args, formato);
    vsnprintf(cuerpo, sizeof cuerpo, formato, args);
    va_end(args);
    char hora[24];
    if (reloj::hayHora()) reloj::iso(time(nullptr), hora, sizeof hora);
    else strcpy(hora, "sin hora");
    Serial.printf("[%s] %s\n", hora, cuerpo);
}
