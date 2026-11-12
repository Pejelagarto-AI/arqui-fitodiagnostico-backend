#include "cola.h"
#include "registro.h"

namespace {
Lectura items[config::CAPACIDAD_COLA];
size_t inicio = 0, cuenta = 0;
}

namespace cola {
size_t tamano() { return cuenta; }

void empujar(const Lectura &l) {
    if (cuenta == config::CAPACIDAD_COLA) {
        registrar("cola llena (%u): se descarta la lectura mas vieja", (unsigned)config::CAPACIDAD_COLA);
        inicio = (inicio + 1) % config::CAPACIDAD_COLA;
        cuenta--;
    }
    items[(inicio + cuenta) % config::CAPACIDAD_COLA] = l;
    cuenta++;
}

const Lectura &frente() { return items[inicio]; }

void sacar() {
    if (!cuenta) return;
    inicio = (inicio + 1) % config::CAPACIDAD_COLA;
    cuenta--;
}
}
