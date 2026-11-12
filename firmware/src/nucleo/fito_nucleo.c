#include "fito_nucleo.h"
#include "fito_hash.h"
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

float fito_escalar_mv(int mv, int mv_min, int mv_max, float v_min, float v_max) {
    if (mv_max <= mv_min || mv <= mv_min) return v_min;
    if (mv >= mv_max) return v_max;
    return v_min + (v_max - v_min) * (float)(mv - mv_min) / (float)(mv_max - mv_min);
}
