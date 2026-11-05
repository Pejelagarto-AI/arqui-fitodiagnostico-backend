#include "reloj.h"
#include <sys/time.h>

namespace {
constexpr time_t HORA_MINIMA_VALIDA = 1700000000;  // nov 2023

// Dias desde 1970-01-01 de una fecha civil (algoritmo de Howard Hinnant); evita depender de timegm.
int64_t diasDesdeEpoch(int a, unsigned m, unsigned d) {
    a -= m <= 2;
    const int64_t era = (a >= 0 ? a : a - 399) / 400;
    const unsigned aa = (unsigned)(a - era * 400);
    const unsigned doy = (153 * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
    const unsigned doe = aa * 365 + aa / 4 - aa / 100 + doy;
    return era * 146097 + (int64_t)doe - 719468;
}
}

namespace reloj {
void iniciarNtp() { configTime(0, 0, "pool.ntp.org", "time.google.com", "time.cloudflare.com"); }

bool hayHora() { return time(nullptr) > HORA_MINIMA_VALIDA; }

bool desdeCabeceraDate(const char *date) {
    static const char *MESES[] = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    char mes[4] = {0};
    int dia, anio, h, mi, s;
    if (!date || sscanf(date, "%*3s, %d %3s %d %d:%d:%d", &dia, mes, &anio, &h, &mi, &s) != 6) return false;
    int m = 0;
    for (int i = 0; i < 12; i++) if (!strcmp(mes, MESES[i])) m = i + 1;
    if (!m) return false;
    struct timeval tv = {(time_t)(diasDesdeEpoch(anio, m, dia) * 86400 + h * 3600 + mi * 60 + s), 0};
    if (tv.tv_sec < HORA_MINIMA_VALIDA) return false;
    settimeofday(&tv, nullptr);
    return true;
}

bool parsearIso(const char *texto, time_t *epoch) {
    int a, m, d, h, mi, s;
    if (!texto || sscanf(texto, "%d-%d-%dT%d:%d:%d", &a, &m, &d, &h, &mi, &s) != 6) return false;
    *epoch = (time_t)(diasDesdeEpoch(a, m, d) * 86400 + h * 3600 + mi * 60 + s);
    return true;
}

void iso(time_t t, char *salida, size_t n) {
    struct tm tmv;
    gmtime_r(&t, &tmv);
    strftime(salida, n, "%Y-%m-%dT%H:%M:%SZ", &tmv);
}
}
