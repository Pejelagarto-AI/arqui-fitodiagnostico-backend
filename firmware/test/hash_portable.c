/* SHA-256 y HMAC-SHA256 portables, SOLO para las pruebas sin placa (en el chip se usa mbedTLS). */
#include "fito_hash.h"
#include <string.h>

static const uint32_t K[64] = {
 0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,
 0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
 0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,
 0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
 0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,
 0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2};
#define ROR(x,n) (((x) >> (n)) | ((x) << (32 - (n))))

static void bloque(uint32_t h[8], const uint8_t *p) {
    uint32_t w[64], a, b, c, d, e, f, g, hh;
    for (int i = 0; i < 16; i++) w[i] = ((uint32_t)p[4*i] << 24) | ((uint32_t)p[4*i+1] << 16) | ((uint32_t)p[4*i+2] << 8) | p[4*i+3];
    for (int i = 16; i < 64; i++) {
        uint32_t s0 = ROR(w[i-15],7) ^ ROR(w[i-15],18) ^ (w[i-15] >> 3);
        uint32_t s1 = ROR(w[i-2],17) ^ ROR(w[i-2],19) ^ (w[i-2] >> 10);
        w[i] = w[i-16] + s0 + w[i-7] + s1;
    }
    a=h[0]; b=h[1]; c=h[2]; d=h[3]; e=h[4]; f=h[5]; g=h[6]; hh=h[7];
    for (int i = 0; i < 64; i++) {
        uint32_t t1 = hh + (ROR(e,6) ^ ROR(e,11) ^ ROR(e,25)) + ((e & f) ^ (~e & g)) + K[i] + w[i];
        uint32_t t2 = (ROR(a,2) ^ ROR(a,13) ^ ROR(a,22)) + ((a & b) ^ (a & c) ^ (b & c));
        hh=g; g=f; f=e; e=d+t1; d=c; c=b; b=a; a=t1+t2;
    }
    h[0]+=a; h[1]+=b; h[2]+=c; h[3]+=d; h[4]+=e; h[5]+=f; h[6]+=g; h[7]+=hh;
}

void fito_sha256(const uint8_t *datos, size_t largo, uint8_t salida[32]) {
    uint32_t h[8] = {0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19};
    size_t i = 0;
    for (; i + 64 <= largo; i += 64) bloque(h, datos + i);
    uint8_t cola[128];
    size_t resto = largo - i;
    memset(cola, 0, sizeof cola);
    memcpy(cola, datos + i, resto);
    cola[resto] = 0x80;
    size_t total = (resto < 56) ? 64 : 128;
    uint64_t bits = (uint64_t)largo * 8;
    for (int k = 0; k < 8; k++) cola[total - 1 - k] = (uint8_t)(bits >> (8 * k));
    for (size_t o = 0; o < total; o += 64) bloque(h, cola + o);
    for (int k = 0; k < 8; k++) { salida[4*k] = h[k] >> 24; salida[4*k+1] = h[k] >> 16; salida[4*k+2] = h[k] >> 8; salida[4*k+3] = h[k]; }
}

void fito_hmac_sha256(const uint8_t *clave, size_t lc, const uint8_t *msg, size_t lm, uint8_t salida[32]) {
    uint8_t k[64], ipad[64], interno[32];
    memset(k, 0, 64);
    if (lc > 64) fito_sha256(clave, lc, k); else memcpy(k, clave, lc);
    uint8_t buf[64 + 1024];
    if (lm > 1024) { memset(salida, 0, 32); return; }
    for (int i = 0; i < 64; i++) { ipad[i] = k[i] ^ 0x36; }
    memcpy(buf, ipad, 64); memcpy(buf + 64, msg, lm);
    fito_sha256(buf, 64 + lm, interno);
    for (int i = 0; i < 64; i++) buf[i] = k[i] ^ 0x5c;
    memcpy(buf + 64, interno, 32);
    fito_sha256(buf, 96, salida);
}
