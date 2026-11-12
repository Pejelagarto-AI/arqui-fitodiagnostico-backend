// Cola en RAM de lecturas sin entregar (D92): 120, con su hora original; si se llena descarta la mas vieja.
#pragma once
#include "configuracion.h"
#include "sensores.h"

namespace cola {
size_t tamano();
void empujar(const Lectura &l);   // si esta llena descarta la mas vieja
const Lectura &frente();
void sacar();
}
