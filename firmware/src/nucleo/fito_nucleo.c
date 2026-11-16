#include "fito_nucleo.h"
#include "fito_hash.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static const char ALFABETO_SERIAL[] = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; /* 32 simbolos: 256 % 32 == 0, sin sesgo */

void fito_serial_desde_mac(const uint8_t mac[6], char *salida) {
    uint8_t resumen[32];
    fito_sha256(mac, 6, resumen);
    memcpy(salida, "fito-", 5);
    for (int i = 0; i < 6; i++) salida[5 + i] = ALFABETO_SERIAL[resumen[i] % 32];
    salida[11] = '\0';
}

size_t fito_base64(const uint8_t *datos, size_t largo, int url, char *salida) {
    const char *tabla = url ? "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
                            : "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    size_t n = 0;
    size_t i = 0;
    while (i + 2 < largo) {
        uint32_t v = ((uint32_t)datos[i] << 16) | ((uint32_t)datos[i + 1] << 8) | datos[i + 2];
        salida[n++] = tabla[(v >> 18) & 63];
        salida[n++] = tabla[(v >> 12) & 63];
        salida[n++] = tabla[(v >> 6) & 63];
        salida[n++] = tabla[v & 63];
        i += 3;
    }
    size_t resto = largo - i;
    if (resto == 1) {
        uint32_t v = (uint32_t)datos[i] << 16;
        salida[n++] = tabla[(v >> 18) & 63];
        salida[n++] = tabla[(v >> 12) & 63];
        if (!url) { salida[n++] = '='; salida[n++] = '='; }
    } else if (resto == 2) {
        uint32_t v = ((uint32_t)datos[i] << 16) | ((uint32_t)datos[i + 1] << 8);
        salida[n++] = tabla[(v >> 18) & 63];
        salida[n++] = tabla[(v >> 12) & 63];
        salida[n++] = tabla[(v >> 6) & 63];
        if (!url) salida[n++] = '=';
    }
    salida[n] = '\0';
    return n;
}

void fito_secreto_desde_bytes(const uint8_t bytes[32], char *salida) {
    fito_base64(bytes, 32, 1, salida);
}

void fito_firma_de_flota(const char *clave, const char *serial, const char *secreto, char *salida) {
    size_t ls = strlen(serial), lc = strlen(secreto);
    uint8_t *mensaje = (uint8_t *)malloc(ls + 1 + lc);
    uint8_t firma[32];
    if (!mensaje) { salida[0] = '\0'; return; }
    memcpy(mensaje, serial, ls);
    mensaje[ls] = '\n';
    memcpy(mensaje + ls + 1, secreto, lc);
    fito_hmac_sha256((const uint8_t *)clave, strlen(clave), mensaje, ls + 1 + lc, firma);
    free(mensaje);
    fito_base64(firma, 32, 0, salida);
}

void fito_respuesta_al_reto(const char *secreto, const char *reto, char *salida) {
    uint8_t firma[32];
    fito_hmac_sha256((const uint8_t *)secreto, strlen(secreto), (const uint8_t *)reto, strlen(reto), firma);
    fito_base64(firma, 32, 0, salida);
}

float fito_interpolar_cal(int mv, const fito_punto_cal *tabla, int n) {
    if (n <= 0) return 0.0f;
    if (mv <= tabla[0].mv) return tabla[0].valor;
    if (mv >= tabla[n - 1].mv) return tabla[n - 1].valor;
    for (int i = 1; i < n; i++) {
        if (mv <= tabla[i].mv) {
            const fito_punto_cal *a = &tabla[i - 1], *b = &tabla[i];
            if (b->mv <= a->mv) return b->valor;            /* tabla mal ordenada: no se divide por cero */
            return a->valor + (b->valor - a->valor) * (float)(mv - a->mv) / (float)(b->mv - a->mv);
        }
    }
    return tabla[n - 1].valor;
}

static int alguna_falla(const uint8_t v[FITO_N_SENSORES]) {
    for (int i = 0; i < FITO_N_SENSORES; i++) if (v[i]) return 1;
    return 0;
}

int fito_salud_toca(const fito_salud_memoria *m, const uint8_t actual[FITO_N_SENSORES], uint32_t ahora_ms) {
    int cambio = m->hay_enviado ? memcmp(m->enviado, actual, FITO_N_SENSORES) != 0 : alguna_falla(actual);
    int frenado = m->hay_intento && (uint32_t)(ahora_ms - m->intento_en_ms) < FITO_SALUD_ENTRE_INTENTOS_MS;
    if (cambio) return !frenado;
    if (!alguna_falla(actual)) return 0;
    return m->hay_enviado && (uint32_t)(ahora_ms - m->enviado_en_ms) >= FITO_SALUD_LATIDO_MS && !frenado;
}

void fito_salud_intento(fito_salud_memoria *m, uint32_t ahora_ms) {
    m->hay_intento = 1;
    m->intento_en_ms = ahora_ms;
}

void fito_salud_confirmado(fito_salud_memoria *m, const uint8_t actual[FITO_N_SENSORES], uint32_t ahora_ms) {
    memcpy(m->enviado, actual, FITO_N_SENSORES);
    m->hay_enviado = 1;
    m->enviado_en_ms = ahora_ms;
}

void fito_hace_cuanto(long segundos, char *salida, size_t n) {
    if (segundos < 0) segundos = 0;
    if (segundos < 60) snprintf(salida, n, "hace %ld s", segundos);
    else if (segundos < 3600) snprintf(salida, n, "hace %ld min", segundos / 60);
    else snprintf(salida, n, "hace %ld h", segundos / 3600);
}

/* ---- la mascota duerme y saluda (fase 11) ---- */

const int8_t FITO_SALUDO_DY[FITO_SALUDO_CUADROS] = {-2, -4, -5, -5, -4, -2, 0, 1, 0, 0, 0, 0};
const float FITO_SALUDO_OJOS[FITO_SALUDO_CUADROS] = {1.0f, 1.0f, 0.5f, 0.0f, 0.5f, 1.0f, 0.5f, 0.0f, 0.5f, 1.0f, 1.0f, 1.0f};

int fito_saludo_cuadro(uint32_t t_ms) {
    return t_ms < FITO_SALUDO_MS ? (int)(t_ms / FITO_SALUDO_CUADRO_MS) : -1;
}

int fito_respira_dy(uint32_t ahora_ms) {
    const uint32_t fase = ahora_ms % FITO_RESPIRA_MS;
    return fase >= FITO_RESPIRA_SUBE_DESDE_MS && fase < FITO_RESPIRA_SUBE_HASTA_MS ? -1 : 0;
}

int fito_zzz_cuadro(uint32_t ahora_ms) {
    return (int)((ahora_ms / FITO_ZZZ_CUADRO_MS) % 3u);
}

int fito_pulsacion_corta(uint32_t sostenido_ms, uint32_t tope_ms) {
    return sostenido_ms >= FITO_BOTON_REBOTE_MS && sostenido_ms < tope_ms;
}
