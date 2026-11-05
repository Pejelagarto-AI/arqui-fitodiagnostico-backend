/* Primitivas de hash que necesita el nucleo. En el chip las da mbedTLS (hash_mbedtls.c); en las pruebas
 * sin placa, una implementacion portable (test/hash_portable.c). El nucleo no sabe cual es. */
#ifndef FITO_HASH_H
#define FITO_HASH_H
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

void fito_sha256(const uint8_t *datos, size_t largo, uint8_t salida[32]);
void fito_hmac_sha256(const uint8_t *clave, size_t largo_clave, const uint8_t *mensaje, size_t largo_mensaje,
                      uint8_t salida[32]);

#ifdef __cplusplus
}
#endif
#endif
