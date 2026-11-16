/* Arnes de las pruebas sin placa: imprime lo que produce el nucleo del firmware para las entradas dadas.
 *   arnes serial <mac-hex12>
 *   arnes secreto <32 bytes en hex>
 *   arnes flota <clave> <serial> <secreto>
 *   arnes reto <secreto> <reto>
 *   arnes sha256 <texto> / hmac <clave> <texto>
 *   arnes tabla <mv> <mv:valor> [<mv:valor> ...]   (puntos en orden ascendente de mv)
 *   arnes saludo <t_ms> / respira <ms> / zzz <ms> / pulsacion <ms> <tope_ms>   (fase 11)
 */
#include "fito_nucleo.h"
#include "fito_hash.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int hex(const char *s, uint8_t *out, size_t n) {
    if (strlen(s) != 2 * n) return 0;
    for (size_t i = 0; i < n; i++) { unsigned v; if (sscanf(s + 2 * i, "%2x", &v) != 1) return 0; out[i] = (uint8_t)v; }
    return 1;
}

int main(int argc, char **argv) {
    char salida[128];
    uint8_t b[32];
    if (argc == 3 && !strcmp(argv[1], "serial") && hex(argv[2], b, 6)) { fito_serial_desde_mac(b, salida); puts(salida); return 0; }
    if (argc == 3 && !strcmp(argv[1], "secreto") && hex(argv[2], b, 32)) { fito_secreto_desde_bytes(b, salida); puts(salida); return 0; }
    if (argc == 5 && !strcmp(argv[1], "flota")) { fito_firma_de_flota(argv[2], argv[3], argv[4], salida); puts(salida); return 0; }
    if (argc == 4 && !strcmp(argv[1], "reto")) { fito_respuesta_al_reto(argv[2], argv[3], salida); puts(salida); return 0; }
    if (argc == 3 && !strcmp(argv[1], "sha256")) { fito_sha256((const uint8_t *)argv[2], strlen(argv[2]), b); for (int i = 0; i < 32; i++) printf("%02x", b[i]); puts(""); return 0; }
    if (argc == 4 && !strcmp(argv[1], "hmac")) { fito_hmac_sha256((const uint8_t *)argv[2], strlen(argv[2]), (const uint8_t *)argv[3], strlen(argv[3]), b); for (int i = 0; i < 32; i++) printf("%02x", b[i]); puts(""); return 0; }
    if (argc >= 3 && !strcmp(argv[1], "tabla")) {
        fito_punto_cal t[FITO_MAX_PUNTOS_CAL];
        int n = 0;
        for (int i = 3; i < argc && n < FITO_MAX_PUNTOS_CAL; i++, n++) {
            if (sscanf(argv[i], "%d:%f", &t[n].mv, &t[n].valor) != 2) { fprintf(stderr, "punto invalido\n"); return 2; }
        }
        printf("%.3f\n", fito_interpolar_cal(atoi(argv[2]), t, n));
        return 0;
    }
    /* salud-guion: pasos "t=<ms>:<f|o>x3[:ok|fallo]" ... imprime 1/0 por paso (toca) y aplica el resultado:
     * "ok" = el servidor confirmo, "fallo" = el envio fallo; sin sufijo = no se envia aunque toque (solo se consulta).
     * Ej.: salud t=0:oof:ok t=5000:oof t=300000:oof:ok */
    if (argc >= 3 && !strcmp(argv[1], "salud")) {
        fito_salud_memoria m;
        memset(&m, 0, sizeof m);
        for (int i = 2; i < argc; i++) {
            unsigned long t;
            char f[8], res[8] = "";
            if (sscanf(argv[i], "%lu:%7[^:]:%7s", &t, f, res) < 2 || strlen(f) != FITO_N_SENSORES) { fprintf(stderr, "paso invalido\n"); return 2; }
            uint8_t actual[FITO_N_SENSORES];
            for (int k = 0; k < FITO_N_SENSORES; k++) actual[k] = f[k] == 'f';
            int toca = fito_salud_toca(&m, actual, (uint32_t)t);
            printf("%d%s", toca, i + 1 < argc ? " " : "\n");
            if (toca && res[0]) { fito_salud_intento(&m, (uint32_t)t); if (!strcmp(res, "ok")) fito_salud_confirmado(&m, actual, (uint32_t)t); }
        }
        return 0;
    }
    /* fase 11: saludo <t_ms> -> "cuadro dy ojos" (o "-1"); respira <ms>; zzz <ms>; pulsacion <ms> <tope> */
    if (argc == 3 && !strcmp(argv[1], "saludo")) {
        int k = fito_saludo_cuadro((uint32_t)strtoul(argv[2], NULL, 10));
        if (k < 0) puts("-1"); else printf("%d %d %.1f\n", k, FITO_SALUDO_DY[k], FITO_SALUDO_OJOS[k]);
        return 0;
    }
    if (argc == 3 && !strcmp(argv[1], "respira")) { printf("%d\n", fito_respira_dy((uint32_t)strtoul(argv[2], NULL, 10))); return 0; }
    if (argc == 3 && !strcmp(argv[1], "zzz")) { printf("%d\n", fito_zzz_cuadro((uint32_t)strtoul(argv[2], NULL, 10))); return 0; }
    if (argc == 4 && !strcmp(argv[1], "pulsacion")) {
        printf("%d\n", fito_pulsacion_corta((uint32_t)strtoul(argv[2], NULL, 10), (uint32_t)strtoul(argv[3], NULL, 10)));
        return 0;
    }
    if (argc == 3 && !strcmp(argv[1], "hace")) { fito_hace_cuanto(atol(argv[2]), salida, sizeof salida); puts(salida); return 0; }
    fprintf(stderr, "uso incorrecto\n");
    return 2;
}
