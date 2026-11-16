#include "boton.h"
#include "configuracion.h"

namespace {
uint32_t desde = 0;
bool presionado = false;
bool disparado = false;
}

namespace boton {
void iniciar() { pinMode(config::PIN_BOTON, INPUT_PULLUP); }

uint32_t ciclo(bool &cumplido, bool &corta) {
    cumplido = false;
    corta = false;
    bool abajo = digitalRead(config::PIN_BOTON) == LOW;
    if (!abajo) {
        if (presionado && !disparado) corta = fito_pulsacion_corta(millis() - desde, config::BOTON_CORTA_MS);
        presionado = false; disparado = false; return 0;
    }
    if (!presionado) { presionado = true; desde = millis(); }
    uint32_t ms = millis() - desde;
    if (ms >= config::BOTON_REINICIO_MS && !disparado) { disparado = true; cumplido = true; }
    return ms;
}
}
