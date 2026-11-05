#include "boton.h"
#include "configuracion.h"

namespace {
uint32_t desde = 0;
bool presionado = false;
bool disparado = false;
}

namespace boton {
void iniciar() { pinMode(config::PIN_BOTON, INPUT_PULLUP); }

uint32_t ciclo(bool &cumplido) {
    cumplido = false;
    bool abajo = digitalRead(config::PIN_BOTON) == LOW;
    if (!abajo) { presionado = false; disparado = false; return 0; }
    if (!presionado) { presionado = true; desde = millis(); }
    uint32_t ms = millis() - desde;
    if (ms >= config::BOTON_REINICIO_MS && !disparado) { disparado = true; cumplido = true; }
    return ms;
}
}
