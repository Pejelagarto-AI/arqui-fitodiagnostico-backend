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


/* ---- salud de la matera (fase 10): cuando reportar al servidor que un sensor falla o se recupero ---- */

#define FITO_N_SENSORES 3                       /* humedad, luz, temperatura */
#define FITO_SALUD_LATIDO_MS 300000UL           /* mientras algo falla, un reporte cada 5 min como maximo */
#define FITO_SALUD_ENTRE_INTENTOS_MS 10000UL    /* entre dos intentos (cambios seguidos o un envio que fallo) */

/* Lo que el firmware recuerda de lo que ya le dijo al servidor. Se inicia con ceros. */
typedef struct {
    uint8_t enviado[FITO_N_SENSORES];   /* 1 = falla; el ultimo estado que el servidor confirmo (200) */
    int hay_enviado;                    /* 0 hasta el primer envio confirmado */
    uint32_t enviado_en_ms;             /* millis() de ese envio */
    int hay_intento;                    /* 0 hasta el primer intento, haya funcionado o no */
    uint32_t intento_en_ms;
} fito_salud_memoria;

/* ¿Toca reportar ahora? `actual[i]` es 1 si el sensor i fallo en la ultima lectura. Reglas:
 *  - Cambio (distinto de lo ultimo confirmado; sin confirmado, cualquier falla): se reporta, pero no antes de
 *    FITO_SALUD_ENTRE_INTENTOS_MS despues del intento anterior (un sensor que parpadea no inunda al servidor, y un
 *    envio fallido se reintenta cada tanto).
 *  - Sin cambio y con alguna falla: un latido cada FITO_SALUD_LATIDO_MS desde el ultimo confirmado, con el mismo
 *    freno entre intentos.
 *  - Todo bien y nada que corregir: nunca. Las diferencias de millis() son modulares: sirven tras el desborde. */
int fito_salud_toca(const fito_salud_memoria *m, const uint8_t actual[FITO_N_SENSORES], uint32_t ahora_ms);

/* Anota que se intento enviar (antes de la llamada HTTP, para frenar los reintentos aunque falle). */
void fito_salud_intento(fito_salud_memoria *m, uint32_t ahora_ms);

/* Anota que el servidor confirmo `actual` (HTTP 200). */
void fito_salud_confirmado(fito_salud_memoria *m, const uint8_t actual[FITO_N_SENSORES], uint32_t ahora_ms);

/* "hace 45 s", "hace 3 min", "hace 2 h" (y "hace 0 s" si segundos <= 0). `salida` >= 16 bytes. */
void fito_hace_cuanto(long segundos, char *salida, size_t n);

#ifdef __cplusplus
}
#endif
#endif
