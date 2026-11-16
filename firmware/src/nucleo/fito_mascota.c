#include "fito_mascota.h"
#include <string.h>

static int buscar(const char *texto, const char *const *tabla, int n) {
    if (!texto) return -1;
    for (int i = 0; i < n; i++) if (!strcmp(texto, tabla[i])) return i;
    return -1;
}

int fito_animo_desde_texto(const char *texto) {
    static const char *const T[FITO_N_ANIMOS] = {"FELIZ", "CANSADA", "SEDIENTA", "AHOGADA", "CON_FRIO", "CON_CALOR",
                                                 "A_OSCURAS", "DESLUMBRADA", "DESMAYADA", "DORMIDA", "SIN_DATOS"};
    return buscar(texto, T, FITO_N_ANIMOS);
}

int fito_accesorio_desde_texto(const char *texto) {
    static const char *const T[FITO_N_ACCESORIOS] = {"NINGUNO", "SOMBRERO", "MONO", "GAFAS"};
    return buscar(texto, T, FITO_N_ACCESORIOS);
}

int fito_evento_desde_texto(const char *texto) {
    static const char *const T[FITO_N_EVENTOS] = {"REGADA", "SALIO_AL_SOL", "DESMAYO", "REVIVIO", "ACARICIADA", "RACHA",
                                                  "RIEGO_DECLARADO", "RIEGO_CONFIRMADO", "RIEGO_DESMENTIDO"};
    return buscar(texto, T, FITO_N_EVENTOS);
}

void fito_ev_iniciar(fito_cola_ev *q, uint32_t ahora_ms) {
    memset(q, 0, sizeof *q);
    q->ciclo_desde_ms = ahora_ms;
}

int fito_ev_empujar(fito_cola_ev *q, int tipo) {
    if (tipo < 0 || tipo >= FITO_N_EVENTOS || tipo == FITO_EV_RIEGO_DECLARADO) return 0;
    if (q->n == FITO_COLA_EV_MAX) {                         /* llena: se va el mas antiguo de los que esperan */
        q->cabeza = (uint8_t)((q->cabeza + 1) % FITO_COLA_EV_MAX);
        q->n--;
        q->descartados++;
    }
    q->tipo[(q->cabeza + q->n) % FITO_COLA_EV_MAX] = (uint8_t)tipo;
    q->n++;
    return 1;
}

int fito_ev_avanzar(fito_cola_ev *q, uint32_t ahora_ms, int *tipo, uint32_t *t_ms) {
    if (q->activo && (uint32_t)(ahora_ms - q->desde_ms) >= FITO_FRASE_EVENTO_MS) {
        q->activo = 0;
        q->ciclo_desde_ms = ahora_ms;
    }
    if (!q->activo && q->n > 0) {
        q->actual = q->tipo[q->cabeza];
        q->cabeza = (uint8_t)((q->cabeza + 1) % FITO_COLA_EV_MAX);
        q->n--;
        q->activo = 1;
        q->desde_ms = ahora_ms;
    }
    if (!q->activo) return 0;
    if (tipo) *tipo = q->actual;
    if (t_ms) *t_ms = (uint32_t)(ahora_ms - q->desde_ms);
    return 1;
}

int fito_fila(const fito_cola_ev *q, uint32_t ahora_ms, int hay_animo) {
    if (q->activo) return FITO_FRASE_EVENTO;
    if (!hay_animo) return FITO_FILA_ESTADO;
    return ((uint32_t)(ahora_ms - q->ciclo_desde_ms) / FITO_FRASE_ALTERNA_MS) % 2u ? FITO_FRASE_ANIMO : FITO_FILA_ESTADO;
}

const int8_t FITO_DESMAYO_DY[FITO_DESMAYO_CUADROS] = {0, 0, 1, 1, 2, 2};

int fito_cuadro_ciclico(uint32_t t_ms, uint32_t cuadro_ms, int cuadros, int vueltas) {
    if (t_ms >= cuadro_ms * (uint32_t)cuadros * (uint32_t)vueltas) return -1;
    return (int)((t_ms / cuadro_ms) % (uint32_t)cuadros);
}
int fito_rayos_cuadro(uint32_t t) { return fito_cuadro_ciclico(t, FITO_RAYOS_CUADRO_MS, FITO_RAYOS_CUADROS, FITO_RAYOS_VUELTAS); }
int fito_corazon_cuadro(uint32_t t) { return fito_cuadro_ciclico(t, FITO_CORAZON_CUADRO_MS, FITO_CORAZON_CUADROS, FITO_CORAZON_VUELTAS); }
int fito_destellos_cuadro(uint32_t t) { return fito_cuadro_ciclico(t, FITO_DESTELLOS_CUADRO_MS, FITO_DESTELLOS_CUADROS, FITO_DESTELLOS_VUELTAS); }
int fito_estrella_cuadro(uint32_t t) { return fito_cuadro_ciclico(t, FITO_ESTRELLA_CUADRO_MS, FITO_ESTRELLA_CUADROS, FITO_ESTRELLA_VUELTAS); }

int fito_desmayo_dy(uint32_t t_ms) {
    return t_ms >= FITO_DESMAYO_MS ? FITO_DESMAYADA_DY : FITO_DESMAYO_DY[t_ms / FITO_DESMAYO_CUADRO_MS];
}

uint32_t fito_ev_animacion_ms(int tipo) {
    switch (tipo) {
    case FITO_EV_SALIO_AL_SOL: return FITO_RAYOS_MS;
    case FITO_EV_ACARICIADA: return FITO_CORAZON_MS;
    case FITO_EV_REVIVIO: return FITO_DESTELLOS_MS;
    case FITO_EV_RACHA: return FITO_ESTRELLA_MS;
    case FITO_EV_DESMAYO: return FITO_DESMAYO_MS;
    default: return 0;
    }
}

int fito_barra_px(int vitalidad, int ancho) {
    if (vitalidad < 0) vitalidad = 0;
    if (vitalidad > 100) vitalidad = 100;
    return (vitalidad * ancho + 50) / 100;
}
