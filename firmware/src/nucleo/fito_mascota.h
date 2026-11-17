/* Logica pura de la mascota completa (fase 14): textos del servidor a numeros, la cola de eventos que se reproducen de a
 * uno, que fila de texto toca mostrar y los cuadros de las animaciones nuevas. Sin Arduino: se prueba sin placa con
 * test/probar_nucleo.py. Todas las constantes de tiempo (ms), cuadros y px de este archivo son las que el front calca
 * (ver docs/fase14/IMPLEMENTACION.md). */
#ifndef FITO_MASCOTA_H
#define FITO_MASCOTA_H
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* ---- animo, accesorio y evento: los textos de la clave "mascota" de la 201 ---- */

/* Orden de `animo` en frases.h (frases.json). */
#define FITO_ANIMO_FELIZ 0
#define FITO_ANIMO_CANSADA 1
#define FITO_ANIMO_SEDIENTA 2
#define FITO_ANIMO_AHOGADA 3
#define FITO_ANIMO_CON_FRIO 4
#define FITO_ANIMO_CON_CALOR 5
#define FITO_ANIMO_A_OSCURAS 6
#define FITO_ANIMO_DESLUMBRADA 7
#define FITO_ANIMO_DESMAYADA 8
#define FITO_ANIMO_DORMIDA 9
#define FITO_ANIMO_SIN_DATOS 10
#define FITO_N_ANIMOS 11

#define FITO_ACC_NINGUNO 0
#define FITO_ACC_SOMBRERO 1
#define FITO_ACC_MONO 2
#define FITO_ACC_GAFAS 3
#define FITO_N_ACCESORIOS 4

/* Orden de `evento` en frases.h. RIEGO_DECLARADO no tiene frase: la OLED lo ignora (D192). */
#define FITO_EV_REGADA 0
#define FITO_EV_SALIO_AL_SOL 1
#define FITO_EV_DESMAYO 2
#define FITO_EV_REVIVIO 3
#define FITO_EV_ACARICIADA 4
#define FITO_EV_RACHA 5
#define FITO_EV_RIEGO_DECLARADO 6
#define FITO_EV_RIEGO_CONFIRMADO 7
#define FITO_EV_RIEGO_DESMENTIDO 8
#define FITO_N_EVENTOS 9

/* Texto -> numero. Devuelven -1 si el texto no se conoce (un servidor nuevo con un valor que este firmware no sabe). */
int fito_animo_desde_texto(const char *texto);
int fito_accesorio_desde_texto(const char *texto);
int fito_evento_desde_texto(const char *texto);

/* ---- la fila de abajo de la pantalla de lecturas ---- */

#define FITO_FRASE_ALTERNA_MS 3000u     /* sin evento, la fila alterna cada 3 s: estado del servidor / frase del animo */
#define FITO_FRASE_EVENTO_MS 3000u      /* la frase de un evento se muestra 3 s y vuelve al ciclo */
#define FITO_COLA_EV_MAX 10             /* el servidor manda como maximo 10 eventos por lectura */

#define FITO_FILA_ESTADO 0
#define FITO_FRASE_ANIMO 1
#define FITO_FRASE_EVENTO 2

/* Cola de eventos de la mascota: se reproducen de a uno, en orden, cada uno ocupa FITO_FRASE_EVENTO_MS (la animacion
 * mas larga, 1800 ms, cabe dentro). Se inicia con fito_ev_iniciar(). */
typedef struct {
    uint8_t tipo[FITO_COLA_EV_MAX];
    uint8_t cabeza;                     /* indice del siguiente en salir */
    uint8_t n;                          /* esperando */
    uint8_t activo;                     /* 1 mientras se reproduce uno */
    uint8_t actual;                     /* su tipo */
    uint32_t desde_ms;                  /* cuando empezo */
    uint32_t ciclo_desde_ms;            /* donde empieza el ciclo estado/frase (al terminar el ultimo evento) */
    uint32_t descartados;               /* eventos viejos que se perdieron por cola llena */
} fito_cola_ev;

void fito_ev_iniciar(fito_cola_ev *q, uint32_t ahora_ms);

/* Encola. 1 = aceptado. 0 = ignorado: tipo fuera de 0..8 (-1 por desconocido) o RIEGO_DECLARADO. Con la cola llena se
 * pierde el mas antiguo de los que esperan (el que esta sonando no se corta) y se cuenta en `descartados`. */
int fito_ev_empujar(fito_cola_ev *q, int tipo);

/* Avanza la cola a `ahora_ms`: termina el evento que cumplio FITO_FRASE_EVENTO_MS y arranca el siguiente en ese
 * mismo instante (no recupera tiempo perdido por un bucle lento). Devuelve 1 si hay un evento sonando; entonces
 * `*tipo` es su tipo y `*t_ms` los ms desde que empezo (NULL si no se quieren). */
int fito_ev_avanzar(fito_cola_ev *q, uint32_t ahora_ms, int *tipo, uint32_t *t_ms);

/* Que fila de texto toca: FITO_FRASE_EVENTO mientras suena un evento; si no, alterna FILA_ESTADO (los primeros 3 s del
 * ciclo) y FRASE_ANIMO. Sin `hay_animo` (servidor viejo, sin clave "mascota") siempre FILA_ESTADO. */
int fito_fila(const fito_cola_ev *q, uint32_t ahora_ms, int hay_animo);

/* ---- animaciones por evento: ms por cuadro, cuadros por vuelta, vueltas (sprites en arte/) ---- */

#define FITO_RAYOS_CUADRO_MS 250u       /* SALIO_AL_SOL: rayos_0..2, 2 vueltas = 1500 ms */
#define FITO_RAYOS_CUADROS 3
#define FITO_RAYOS_VUELTAS 2
#define FITO_RAYOS_MS (FITO_RAYOS_CUADRO_MS * FITO_RAYOS_CUADROS * FITO_RAYOS_VUELTAS)

#define FITO_CORAZON_CUADRO_MS 300u     /* ACARICIADA: corazon_0..1 (chico, grande), 3 latidos = 1800 ms, mas el saludo */
#define FITO_CORAZON_CUADROS 2
#define FITO_CORAZON_VUELTAS 3
#define FITO_CORAZON_MS (FITO_CORAZON_CUADRO_MS * FITO_CORAZON_CUADROS * FITO_CORAZON_VUELTAS)

#define FITO_DESTELLOS_CUADRO_MS 200u   /* REVIVIO: destellos_0..2, 2 vueltas = 1200 ms, mas el saludo */
#define FITO_DESTELLOS_CUADROS 3
#define FITO_DESTELLOS_VUELTAS 2
#define FITO_DESTELLOS_MS (FITO_DESTELLOS_CUADRO_MS * FITO_DESTELLOS_CUADROS * FITO_DESTELLOS_VUELTAS)

#define FITO_ESTRELLA_CUADRO_MS 300u    /* RACHA: estrella_0..2, 2 vueltas = 1800 ms */
#define FITO_ESTRELLA_CUADROS 3
#define FITO_ESTRELLA_VUELTAS 2
#define FITO_ESTRELLA_MS (FITO_ESTRELLA_CUADRO_MS * FITO_ESTRELLA_CUADROS * FITO_ESTRELLA_VUELTAS)

/* DESMAYO: 6 cuadros de 100 ms con la pose critica y los ojos en "x"; el cuerpo baja 0,0,1,1,2,2 px. Despues, mientras
 * el animo siga DESMAYADA, queda en FITO_DESMAYADA_DY (2 px abajo), sin balanceo. */
#define FITO_DESMAYO_CUADRO_MS 100u
#define FITO_DESMAYO_CUADROS 6
#define FITO_DESMAYO_MS (FITO_DESMAYO_CUADRO_MS * FITO_DESMAYO_CUADROS)
#define FITO_DESMAYADA_DY 2
extern const int8_t FITO_DESMAYO_DY[FITO_DESMAYO_CUADROS];

/* Cuadro de una animacion ciclica a `t_ms` de empezada (0..cuadros-1), o -1 si ya termino. */
int fito_cuadro_ciclico(uint32_t t_ms, uint32_t cuadro_ms, int cuadros, int vueltas);
int fito_rayos_cuadro(uint32_t t_ms);
int fito_corazon_cuadro(uint32_t t_ms);
int fito_destellos_cuadro(uint32_t t_ms);
int fito_estrella_cuadro(uint32_t t_ms);
/* px que baja el cuerpo `t_ms` despues de empezado el DESMAYO (0 al principio, FITO_DESMAYADA_DY al final y despues). */
int fito_desmayo_dy(uint32_t t_ms);

/* Cuanto dura la parte animada de cada evento (0 = sin animacion propia: solo frase). La mascota dormida de noche se
 * despierta ese tiempo y vuelve a dormir. REGADA tampoco despierta: su gota ya existia y es de la suculenta. */
uint32_t fito_ev_animacion_ms(int tipo);

/* Barra de vitalidad: px rellenos de `ancho` para 0..100 (se recorta fuera de rango). */
int fito_barra_px(int vitalidad, int ancho);

#ifdef __cplusplus
}
#endif
#endif
