/* SHA-256 y HMAC-SHA256 con mbedTLS (el que trae el ESP32). */
#include "fito_hash.h"
#include "mbedtls/md.h"

void fito_sha256(const uint8_t *datos, size_t largo, uint8_t salida[32]) {
    mbedtls_md(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), datos, largo, salida);
}

void fito_hmac_sha256(const uint8_t *clave, size_t largo_clave, const uint8_t *mensaje, size_t largo_mensaje,
                      uint8_t salida[32]) {
    mbedtls_md_hmac(mbedtls_md_info_from_type(MBEDTLS_MD_SHA256), clave, largo_clave, mensaje, largo_mensaje, salida);
}
