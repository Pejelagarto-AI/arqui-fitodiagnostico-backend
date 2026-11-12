/* Logica pura del firmware (sin Arduino): identidad derivada de la MAC, base64 y las dos firmas.
 * Es la traduccion literal de serial_desde_mac, firma_de_flota y responder_al_reto de
 * simulador/simulador_esp32.py. Se prueba sin placa con test/probar_nucleo.py. */
#ifndef FITO_NUCLEO_H
#define FITO_NUCLEO_H
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define FITO_LARGO_SERIAL 11            /* "fito-" + 6 caracteres */
#define FITO_LARGO_FIRMA_B64 45         /* base64 de 32 bytes con relleno: 44 + NUL */
#define FITO_LARGO_SECRETO_B64 44       /* base64url sin relleno de 32 bytes: 43 + NUL */

/* "fito-" + 6 caracteres del alfabeto sin 0/O/1/I, derivados de sha256(mac de 6 bytes). `salida` >= 12 bytes. */
void fito_serial_desde_mac(const uint8_t mac[6], char *salida);

/* Base64 de `largo` bytes. url=0: alfabeto estandar con relleno '='. url=1: base64url sin relleno.
 * Devuelve la longitud escrita (sin el NUL); `salida` necesita 4*((largo+2)/3)+1 bytes. */
size_t fito_base64(const uint8_t *datos, size_t largo, int url, char *salida);

/* base64url sin relleno de 32 bytes (el secreto del chip). `salida` >= FITO_LARGO_SECRETO_B64. */
void fito_secreto_desde_bytes(const uint8_t bytes[32], char *salida);

/* base64( HMAC-SHA256( clave de flota, serial + "\n" + secreto ) ). `salida` >= FITO_LARGO_FIRMA_B64. */
void fito_firma_de_flota(const char *clave, const char *serial, const char *secreto, char *salida);

/* base64( HMAC-SHA256( clave = secreto como texto, mensaje = reto como texto ) ). `salida` >= FITO_LARGO_FIRMA_B64. */
void fito_respuesta_al_reto(const char *secreto, const char *reto, char *salida);

/* Recta que lleva milivoltios a la magnitud: mv_min -> v_min y mv_max -> v_max. Fuera de [mv_min, mv_max] se
 * recorta al extremo. Si mv_max <= mv_min devuelve v_min. La usa el adaptador del potenciometro. */
float fito_escalar_mv(int mv, int mv_min, int mv_max, float v_min, float v_max);

#ifdef __cplusplus
}
#endif
#endif
