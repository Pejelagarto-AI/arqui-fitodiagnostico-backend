#include "pantalla.h"
#include "configuracion.h"
#include "registro.h"
#include "sprites.h"
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <Wire.h>
#include <qrcode.h>
#include <ctype.h>

namespace {
Adafruit_SSD1306 oled(128, 64, &Wire, -1);
bool lista = false;
uint8_t dirOled = 0;
int pinSda = config::PIN_SDA, pinScl = config::PIN_SCL;
bool vistaAlgunaVez = false;           // alguna vez respondio: los pines ya son los buenos
bool pinesInvertidos = false;
uint32_t proximaComprobacion = 0;

enum class Modo { Otro, Reclamo, Lecturas, Exito };
Modo modo = Modo::Otro;

constexpr int VERSION_QR = 3;       // 29 modulos x 2 px = 58 px: cabe en los 64 px de alto con 3 px de margen
constexpr int MODULO_PX = 2;
constexpr size_t CAPACIDAD_QR_L = 53;
constexpr size_t CAPACIDAD_QR_M = 42;

void limpiar() { modo = Modo::Otro; oled.clearDisplay(); oled.setTextColor(SSD1306_WHITE); oled.setTextSize(1); oled.setCursor(0, 0); }
void listo() { if (lista) oled.display(); }
void texto(int x, int y, const char *t, int tam = 1) { oled.setTextSize(tam); oled.setCursor(x, y); oled.print(t); oled.setTextSize(1); }
void bitmap(int x, int y, const uint8_t *b, int w, int h) { oled.drawBitmap(x, y, b, w, h, SSD1306_WHITE); }

// Icono 8x8 de sprites.h ampliado `k` veces (para los avisos de pantalla completa).
void iconoGrande(int x, int y, const uint8_t *b, int k) {
    for (int fy = 0; fy < 8; fy++) {
        uint8_t fila = pgm_read_byte(b + fy);
        for (int fx = 0; fx < 8; fx++)
            if (fila & (0x80 >> fx)) oled.fillRect(x + fx * k, y + fy * k, k, k, SSD1306_WHITE);
    }
}

// QR en negro sobre un cuadro blanco de `anchoBlanco` x 64. false si el texto no cabe en la version 3.
bool dibujarQr(const char *txt, int anchoBlanco, int margenX, int margenY) {
    const size_t largo = strlen(txt);
    if (largo > CAPACIDAD_QR_L) return false;
    const uint8_t ecc = largo <= CAPACIDAD_QR_M ? ECC_MEDIUM : ECC_LOW;
    QRCode qrc;
    uint8_t *buffer = (uint8_t *)malloc(qrcode_getBufferSize(VERSION_QR));
    if (!buffer) return false;
    if (qrcode_initText(&qrc, buffer, VERSION_QR, ecc, txt) != 0) { free(buffer); return false; }
    oled.fillRect(0, 0, anchoBlanco, 64, SSD1306_WHITE);        // cuadro encendido, modulos apagados
    for (int y = 0; y < qrc.size; y++)
        for (int x = 0; x < qrc.size; x++)
            if (qrcode_getModule(&qrc, x, y))
                oled.fillRect(margenX + x * MODULO_PX, margenY + y * MODULO_PX, MODULO_PX, MODULO_PX, SSD1306_BLACK);
    free(buffer);
    return true;
}

// ---- sprites de la planta ----
const uint8_t *const PLANTA[3][2] = {
    {sprites::planta_saludable_0, sprites::planta_saludable_1},
    {sprites::planta_riesgo_0, sprites::planta_riesgo_1},
    {sprites::planta_critico_0, sprites::planta_critico_1}};
const uint8_t *const CARNI_TRISTE[2][2] = {
    {sprites::carni_riesgo_0, sprites::carni_riesgo_1},
    {sprites::carni_critico_0, sprites::carni_critico_1}};
const uint8_t *const EXITO[8] = {sprites::exito_0, sprites::exito_1, sprites::exito_2, sprites::exito_3,
                                 sprites::exito_4, sprites::exito_5, sprites::exito_6, sprites::exito_7};

// ---- estado de la pantalla de lecturas (para repintar sin que main reenvie todo) ----
Lectura lLect = {};
char lCodigo[8] = "";
char lEstado[16] = "";
bool lPunto = false;
bool lCarnivora = false;
int lNivel = 0;                         // 0 saludable, 1 en riesgo, 2 critico
int lUltimoCuadro = -1;
bool lSucia = true;
uint32_t mordiscoDesde = 0;
bool mordiendo = false;
uint32_t proximoMordisco = 0;
constexpr uint32_t MS_CUADRO = 600;

bool contieneSinMayusculas(const char *pajar, const char *aguja) {
    const size_t n = strlen(aguja);
    for (; *pajar; pajar++) {
        size_t i = 0;
        while (i < n && pajar[i] && tolower((unsigned char)pajar[i]) == aguja[i]) i++;
        if (i == n) return true;
    }
    return false;
}

// Cuadro del mordisco: abierta, media, cerrada, media, abierta (540 ms).
const uint8_t *cuadroDeMordisco(uint32_t t) {
    if (t < 120) return sprites::carni_abierta_0;
    if (t < 240) return sprites::carni_media;
    if (t < 420) return sprites::carni_cerrada;
    if (t < 540) return sprites::carni_media;
    return sprites::carni_abierta_0;
}

// Identificador del cuadro a mostrar (y su bitmap). Cambia solo cuando hay que repintar.
int elegirCuadro(uint32_t ahora, const uint8_t *&bmp) {
    const int alterna = (ahora / MS_CUADRO) % 2;
    if (!lCarnivora) { bmp = PLANTA[lNivel][alterna]; return lNivel * 2 + alterna; }
    if (lNivel > 0) { bmp = CARNI_TRISTE[lNivel - 1][alterna]; return 10 + lNivel * 2 + alterna; }
    if (!mordiendo && (int32_t)(ahora - proximoMordisco) >= 0) {       // mordisco espontaneo de vez en cuando
        mordiendo = true; mordiscoDesde = ahora;
        proximoMordisco = ahora + 6000 + (ahora % 4000);
    }
    if (mordiendo) {
        uint32_t t = ahora - mordiscoDesde;
        if (t >= 540) mordiendo = false;
        else { bmp = cuadroDeMordisco(t); return 100 + (int)(t / 60); }
    }
    bmp = alterna ? sprites::carni_abierta_1 : sprites::carni_abierta_0;
    return 20 + alterna;
}

void pintarLecturas(uint32_t ahora) {
    const uint8_t *bmp = nullptr;
    int cuadro = elegirCuadro(ahora, bmp);
    limpiar();
    modo = Modo::Lecturas;
    lUltimoCuadro = cuadro * 2 + (lPunto ? 1 : 0);
    lSucia = false;
    bitmap(0, 0, bmp, 48, 64);
    char v[12];
    struct Fila { const uint8_t *icono; const char *unidad; } filas[3] = {
        {sprites::icono_gota, "%"}, {sprites::icono_sol, "lx"}, {sprites::icono_termometro, "C"}};
    snprintf(v, sizeof v, "%d", (int)roundf(lLect.humedad));
    for (int i = 0; i < 3; i++) {
        if (i == 1) snprintf(v, sizeof v, "%d", (int)min(99999.0f, max(0.0f, roundf(lLect.luz))));
        if (i == 2) snprintf(v, sizeof v, "%.1f", lLect.temperatura);
        const int y = i * 15;
        bitmap(50, y + 3, filas[i].icono, 8, 8);
        texto(61, y, v, 2);
        const int largo = strlen(v);
        if (largo < 5) texto(61 + 12 * largo + 1, y + 7, filas[i].unidad);
    }
    texto(50, 47, lCodigo);
    texto(50, 56, lEstado);
    if (lPunto) oled.fillCircle(123, 59, 3, SSD1306_WHITE);
    listo();
}

// ---- animacion de exito ----
uint32_t exitoDesde = 0;
char exitoCodigo[8] = "";
int exitoUltimo = -1;
constexpr uint32_t EXITO_MS_CUADRO = 125;                    // 8 cuadros por segundo
constexpr uint32_t EXITO_FIN_CUADROS = 8 * EXITO_MS_CUADRO;  // 1000
constexpr uint32_t EXITO_FIN_DESLIZ = EXITO_FIN_CUADROS + 400;
constexpr uint32_t EXITO_TOTAL = EXITO_FIN_DESLIZ + 1800;

// Puntos de espera de la pantalla de reclamo.
int puntosHasta = 0;
int puntosUltimo = -1;
void pintarPuntos(uint32_t ahora) {
    const int activo = (ahora / 400) % 3;
    oled.fillRect(66, 52, 62, 12, SSD1306_BLACK);
    for (int i = 0; i < 3; i++) {
        const int d = i == activo ? 3 : 2;
        oled.fillRect(70 + i * 8, 58 - d / 2, d, d, SSD1306_WHITE);
    }
}
}

namespace pantalla {
// Busca la OLED en las dos direcciones habituales con el par de pines dado. 0 = no responde.
static uint8_t buscarOled(int sda, int scl) {
    Wire.end();
    Wire.begin(sda, scl);
    Wire.setTimeOut(10);               // con mal contacto el bus no debe colgar el bucle
    for (uint8_t direccion : {0x3C, 0x3D}) {
        Wire.beginTransmission(direccion);
        if (Wire.endTransmission() == 0) return direccion;
    }
    return 0;
}

bool iniciar() {
    uint8_t direccion = buscarOled(config::PIN_SDA, config::PIN_SCL);
    registrar("escaneo I2C (SDA=GPIO%d, SCL=GPIO%d): %s", config::PIN_SDA, config::PIN_SCL,
              direccion == 0x3C ? "0x3C responde" : direccion == 0x3D ? "0x3D responde" : "0x3C no, 0x3D no");
    if (!direccion) {
        // Error de cableado mas comun: SDA y SCL cruzados. Si asi responde, se sigue con los pines invertidos.
        direccion = buscarOled(config::PIN_SCL, config::PIN_SDA);
        if (direccion) {
            pinSda = config::PIN_SCL; pinScl = config::PIN_SDA; pinesInvertidos = true;
            registrar("la OLED responde con SDA y SCL invertidos (SDA=GPIO%d, SCL=GPIO%d): sigo asi",
                      config::PIN_SCL, config::PIN_SDA);
        }
    }
    if (!direccion) {
        Wire.end();
        Wire.begin(config::PIN_SDA, config::PIN_SCL);
        Wire.setTimeOut(10);
        int vistos = 0;
        for (uint8_t d = 0x08; d < 0x78; d++) {
            Wire.beginTransmission(d);
            if (Wire.endTransmission() == 0) { registrar("  hay un dispositivo I2C en 0x%02X (no es una OLED SSD1306 conocida)", d); vistos++; }
        }
        registrar("la OLED no responde%s: revise cableado (SDA=GPIO%d, SCL=GPIO%d, VCC=3V3, GND). Sigo sin pantalla y reintento cada 2 s.",
                  vistos ? "" : " y el bus I2C esta vacio", config::PIN_SDA, config::PIN_SCL);
        return false;
    }
    lista = oled.begin(SSD1306_SWITCHCAPVCC, direccion);
    if (lista) { dirOled = direccion; vistaAlgunaVez = true; registrar("OLED lista en 0x%02X", direccion); }
    return lista;
}

bool reintentarSiFalta(uint32_t ahoraMs) {
    if ((int32_t)(ahoraMs - proximaComprobacion) < 0) return false;
    proximaComprobacion = ahoraMs + 2000;
    if (lista) {
        Wire.beginTransmission(dirOled);
        if (Wire.endTransmission() == 0) return false;
        lista = false;
        registrar("la OLED dejo de responder (0x%02X): se reintenta cada 2 s", dirOled);
        return false;
    }
    // Sin OLED: se prueban los pines buenos; si nunca respondio, se alternan los normales y los invertidos.
    int sda = pinSda, scl = pinScl;
    if (!vistaAlgunaVez) {
        pinesInvertidos = !pinesInvertidos;
        sda = pinesInvertidos ? config::PIN_SCL : config::PIN_SDA;
        scl = pinesInvertidos ? config::PIN_SDA : config::PIN_SCL;
    }
    uint8_t direccion = buscarOled(sda, scl);
    if (!direccion) return false;
    // Pudo perder la alimentacion: se reinicializa del todo.
    if (!oled.begin(SSD1306_SWITCHCAPVCC, direccion)) return false;
    pinSda = sda; pinScl = scl; dirOled = direccion; vistaAlgunaVez = true; lista = true;
    registrar("OLED lista en 0x%02X (detectada en caliente)", direccion);
    return true;
}

void arranque(const char *serial) {
    if (!lista) return;   // sin OLED no hay bufer: dibujar escribiria en una direccion nula
    limpiar();
    oled.setTextSize(2); oled.println("Fito");
    oled.setTextSize(1); oled.println("Fitodiagnostico");
    oled.println();
    oled.println(serial);
    oled.println("Iniciando...");
    listo();
}

void mensaje(const char *l1, const char *l2, const char *l3, const char *l4) {
    if (!lista) return;   // sin OLED no hay bufer: dibujar escribiria en una direccion nula
    limpiar();
    oled.println(l1); oled.println(l2); oled.println(l3); oled.println(l4);
    listo();
}

void portal(const char *redWifi) {
    if (!lista) return;   // sin OLED no hay bufer: dibujar escribiria en una direccion nula
    limpiar();
    char wifi[64];
    snprintf(wifi, sizeof wifi, "WIFI:S:%s;T:nopass;;", redWifi);
    // Cuadro blanco de 62 px (el QR mide 58) para que el nombre de la red (11 caracteres) quepa a la derecha desde x=63.
    const bool conQr = dibujarQr(wifi, 62, 2, 3);
    const int x = conQr ? 63 : 0;
    texto(x, conQr ? 4 : 0, "Conecta tu");
    texto(x, conQr ? 14 : 10, "celular a:");
    if (strlen(redWifi) * 6 + x <= 128) texto(x, conQr ? 34 : 24, redWifi);
    else { oled.setCursor(x, conQr ? 34 : 24); oled.println(redWifi); }
    if (!conQr) registrar("el QR de Wi-Fi no cabe en la version 3: solo texto");
    listo();
}

void qr(const String &enlace, const char *codigo, uint8_t puntos) {
    if (!lista) return;   // sin OLED no hay bufer: dibujar escribiria en una direccion nula
    (void)puntos;
    limpiar();
    const bool conQr = dibujarQr(enlace.c_str(), 64, 3, 3);
    if (!conQr) registrar("el enlace (%u bytes) no cabe en el QR version 3: se muestra solo el codigo", (unsigned)enlace.length());
    const int x = conQr ? 68 : 36;
    char a[4], b[4];
    strlcpy(a, codigo, 4);
    strlcpy(b, codigo + 3, 4);
    texto(x, 0, "Codigo");
    texto(x, 12, a, 2);
    texto(x, 30, b, 2);
    modo = Modo::Reclamo;
    pintarPuntos(millis());
    puntosUltimo = (millis() / 400) % 3;
    listo();
}

void animarEspera(uint32_t ahoraMs) {
    if (!lista || modo != Modo::Reclamo) return;
    const int activo = (ahoraMs / 400) % 3;
    if (activo == puntosUltimo) return;
    puntosUltimo = activo;
    pintarPuntos(ahoraMs);
    listo();
}

void empezarExito(uint32_t ahoraMs, const char *codigo) {
    exitoDesde = ahoraMs;
    exitoUltimo = -1;
    strlcpy(exitoCodigo, codigo, sizeof exitoCodigo);
}

bool animarExito(uint32_t ahoraMs) {
    const uint32_t t = ahoraMs - exitoDesde;
    if (t >= EXITO_TOTAL) return false;
    if (!lista) return true;
    int clave;
    int x = 32;
    if (t < EXITO_FIN_CUADROS) clave = t / EXITO_MS_CUADRO;
    else if (t < EXITO_FIN_DESLIZ) {
        x = 32 - (int)((t - EXITO_FIN_CUADROS) * 30 / (EXITO_FIN_DESLIZ - EXITO_FIN_CUADROS));
        clave = 100 + x;
    } else clave = 200;
    if (clave == exitoUltimo && modo == Modo::Exito) return true;
    exitoUltimo = clave;
    if (clave == 200) { vinculada(exitoCodigo); modo = Modo::Exito; return true; }
    limpiar();
    modo = Modo::Exito;
    bitmap(x, 0, EXITO[t < EXITO_FIN_CUADROS ? clave : 7], 64, 64);
    listo();
    return true;
}

void vinculada(const char *codigo) {
    if (!lista) return;   // sin OLED no hay bufer: dibujar escribiria en una direccion nula
    limpiar();
    bitmap(2, 0, sprites::exito_7, 64, 64);
    texto(70, 22, "Vinculada");
    texto(70, 36, codigo);
    listo();
}

void lecturas(const char *codigo, const Lectura &l, const char *estado, bool punto, const char *especie) {
    if (!lista) return;   // sin OLED no hay bufer: dibujar escribiria en una direccion nula
    lLect = l;
    lPunto = punto;
    strlcpy(lCodigo, codigo, sizeof lCodigo);
    lCarnivora = especie && (contieneSinMayusculas(especie, "atrapamoscas") || contieneSinMayusculas(especie, "carniv"));
    lNivel = 0;
    const char *etiqueta = "-";
    if (estado && estado[0]) {
        if (contieneSinMayusculas(estado, "crit")) { lNivel = 2; etiqueta = "CRITICO"; }
        else if (contieneSinMayusculas(estado, "riesgo")) { lNivel = 1; etiqueta = "EN RIESGO"; }
        else etiqueta = "SALUDABLE";
    }
    strlcpy(lEstado, etiqueta, sizeof lEstado);
    if (proximoMordisco == 0) proximoMordisco = millis() + 4000;
    pintarLecturas(millis());
}

void animarLecturas(uint32_t ahoraMs) {
    if (!lista || modo != Modo::Lecturas) return;
    const uint8_t *bmp;
    const int cuadro = elegirCuadro(ahoraMs, bmp) * 2 + (lPunto ? 1 : 0);
    if (cuadro == lUltimoCuadro && !lSucia) return;
    pintarLecturas(ahoraMs);
}

void mordisco(uint32_t ahoraMs) {
    if (!lCarnivora || lNivel > 0) return;
    mordiendo = true;
    mordiscoDesde = ahoraMs;
    proximoMordisco = ahoraMs + 6000 + (ahoraMs % 4000);
}

void sinRed(size_t guardadas) {
    if (!lista) return;   // sin OLED no hay bufer: dibujar escribiria en una direccion nula
    limpiar();
    iconoGrande(0, 0, sprites::icono_sinred, 2);
    texto(22, 1, "Sin red", 2);
    oled.setCursor(0, 24);
    oled.printf("(%u guardadas)\n", (unsigned)guardadas);
    oled.println("Reintentando...");
    listo();
}

void error(const char *l1, const char *l2, const char *l3) {
    if (!lista) return;   // sin OLED no hay bufer: dibujar escribiria en una direccion nula
    limpiar();
    iconoGrande(0, 0, sprites::icono_sinred, 1);
    texto(12, 0, "ERROR");
    oled.setCursor(0, 16);
    oled.println(l1); oled.println(l2); oled.println(l3);
    listo();
}

void reinicio(int segundos) {
    if (!lista) return;   // sin OLED no hay bufer: dibujar escribiria en una direccion nula
    limpiar();
    oled.println("Borrando Wi-Fi...");
    oled.println("Suelta para cancelar");
    oled.setTextSize(3); oled.setCursor(52, 30); oled.print(segundos);
    listo();
}
}
