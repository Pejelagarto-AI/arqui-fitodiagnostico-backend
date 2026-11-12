/* Arnes de las pruebas sin placa: imprime lo que produce el nucleo del firmware para las entradas dadas.
 *   arnes serial <mac-hex12>
 *   arnes secreto <32 bytes en hex>
 *   arnes flota <clave> <serial> <secreto>
 *   arnes reto <secreto> <reto>
 *   arnes sha256 <texto> / hmac <clave> <texto>
 *   arnes escala <mv> <mv_min> <mv_max> <v_min> <v_max>
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
    if (argc == 7 && !strcmp(argv[1], "escala")) {
        printf("%.3f\n", fito_escalar_mv(atoi(argv[2]), atoi(argv[3]), atoi(argv[4]), (float)atof(argv[5]), (float)atof(argv[6])));
        return 0;
    }
    fprintf(stderr, "uso incorrecto\n");
    return 2;
}
