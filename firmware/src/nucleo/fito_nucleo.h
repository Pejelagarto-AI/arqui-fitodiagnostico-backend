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

/* Un punto de una tabla de calibracion: a `mv` milivoltios en la entrada le corresponde `valor` en la magnitud. */
typedef struct {
    int mv;
    float valor;
} fito_punto_cal;

#define FITO_MAX_PUNTOS_CAL 8

/* Lleva milivoltios a la magnitud con una tabla de `n` puntos ORDENADOS por mv estrictamente ascendente,
 * interpolando en linea recta entre los dos puntos vecinos. Los valores pueden bajar (curva descendente: la LDR y
 * la sonda dan menos voltaje con mas luz o mas agua). Por debajo del primer punto devuelve el valor del primero y
 * por encima del ultimo el del ultimo (recorte en los extremos). n == 1 devuelve ese valor; n <= 0 devuelve 0.
 * La usa SensorAnalogico; extra_script.py valida y ordena las tablas de local.ini. */
float fito_interpolar_cal(int mv, const fito_punto_cal *tabla, int n);

#ifdef __cplusplus
}
#endif
#endif
