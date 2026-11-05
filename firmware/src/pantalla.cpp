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
Adafruit_SSD1306 oled(128, 64, &Wire, -1, 400000UL, 400000UL);   // I2C a 400 kHz tambien entre cuadros
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
// UTF-8 a ASCII para la fuente CP437: acentos y enie pasan a su letra base, lo demas fuera de ASCII imprimible se omite.
void aAscii(const char *utf8, char *salida, size_t n) {
    size_t k = 0;
    for (const uint8_t *c = (const uint8_t *)utf8; *c && k + 1 < n; c++) {
        if (*c >= 0x20 && *c < 0x7F) { salida[k++] = (char)*c; continue; }
        if (*c == 0xC3 && c[1]) {
            c++;
            char r = 0;
            switch (*c) {
            case 0xA1: case 0x81: r = *c == 0xA1 ? 'a' : 'A'; break;
            case 0xA9: case 0x89: r = *c == 0xA9 ? 'e' : 'E'; break;
            case 0xAD: case 0x8D: r = *c == 0xAD ? 'i' : 'I'; break;
            case 0xB3: case 0x93: r = *c == 0xB3 ? 'o' : 'O'; break;
            case 0xBA: case 0x9A: r = *c == 0xBA ? 'u' : 'U'; break;
            case 0xBC: case 0x9C: r = *c == 0xBC ? 'u' : 'U'; break;
            case 0xB1: case 0x91: r = *c == 0xB1 ? 'n' : 'N'; break;
            }
            if (r) salida[k++] = r;
        }                                                  // cualquier otro byte >= 0x80 o de control se omite
    }
    salida[k] = 0;
    while (k && salida[k - 1] == ' ') salida[--k] = 0;
}

// Nombre en una ventana de `ancho` px: si cabe, fijo; si no, marquesina (pausa de 1 s al inicio y al final).
GFXcanvas1 *ventanas[2] = {nullptr, nullptr};
void nombreEnVentana(int idx, int x, int y, int ancho, const char *nombre, uint32_t t) {
    if (!ventanas[idx]) ventanas[idx] = new GFXcanvas1(ancho, 8);
    GFXcanvas1 &cv = *ventanas[idx];
    const int w = strlen(nombre) * 6 - 1;
    int off = 0;
    if (w > ancho) {
        const uint32_t recorrido = (uint32_t)(w - ancho) * 1000 / 24;               // 24 px por segundo
        const uint32_t fase = t % (recorrido + 2000);
        off = fase < 1000 ? 0 : fase < 1000 + recorrido ? (int)((fase - 1000) * 24 / 1000) : w - ancho;
    }
    cv.fillScreen(0);
    cv.setTextColor(1); cv.setTextSize(1); cv.setCursor(-off, 0);
    cv.print(nombre);
    oled.drawBitmap(x, y, cv.getBuffer(), ancho, 8, SSD1306_WHITE);
}

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

// ---- sprites ----
const uint8_t *const PLANTA[3] = {sprites::planta_saludable_0, sprites::planta_riesgo_0, sprites::planta_critico_0};
const uint8_t *const CARNI_TRISTE[2] = {sprites::carni_riesgo_0, sprites::carni_critico_0};
const uint8_t *const CARNI_BOCA[3] = {sprites::carni_abierta_0, sprites::carni_media, sprites::carni_cerrada};
const int CARNI_G[3] = {14, 7, 0};                           // abertura de la boca de cada cuadro (la cara se ancla a ella)
const uint8_t *const EXITO[6] = {sprites::exito_0, sprites::exito_1, sprites::exito_2,
                                 sprites::exito_3, sprites::exito_4, sprites::exito_5};

constexpr int Y_MATERA = 46;                                  // desde aqui el sprite no se balancea
constexpr uint32_t MS_FOTOGRAMA = 50;                         // 20 cuadros por segundo
constexpr uint32_t PERIODO_BALANCEO_MS = 2800;
const float AMPLITUD[3] = {2.2f, 1.5f, 0.8f};                 // px de desplazamiento en la punta: la marchita casi no se mueve

// Mordisco de la carnivora: (hasta ms, cuadro de boca, desplazamiento vertical) con un pequeno rebote al final.
struct PasoMordisco { uint16_t hasta; uint8_t boca; int8_t dy; };
const PasoMordisco MORDISCO[] = {{90, 0, 0}, {170, 1, 0}, {290, 2, 2}, {380, 1, 1}, {460, 0, -1}};
constexpr uint32_t MORDISCO_MS = 460;

// ---- cara expresiva (parametros que se interpolan) ----
struct Cara { float abierto, inclina, boca, onda; };          // ojos 0..1, cejas 0..1, boca -1..1, ondulacion 0..1
const Cara CARA_NIVEL[3] = {{1.0f, 0.0f, 1.0f, 0.0f},         // saludable: ojos redondos y sonrisa
                            {0.55f, 0.6f, -0.5f, 0.7f},       // en riesgo: entrecerrados, cejas preocupadas, boca ondulada
                            {0.6f, 1.0f, -1.0f, 0.0f}};       // critico: ojos caidos, cejas inclinadas, boca hacia abajo
constexpr uint32_t TRANSICION_MS = 400;
Cara caraDesde = CARA_NIVEL[0], caraHasta = CARA_NIVEL[0];
uint32_t caraT0 = 0;
int caraNivel = -1;
uint32_t parpadeoDesde = 0, proximoParpadeo = 0;

float suave(float t) { t = t < 0 ? 0 : t > 1 ? 1 : t; return t * t * (3 - 2 * t); }
float mezcla(float a, float b, float k) { return a + (b - a) * k; }

Cara caraEn(uint32_t ahora) {
    const float k = suave((float)(ahora - caraT0) / TRANSICION_MS);
    return {mezcla(caraDesde.abierto, caraHasta.abierto, k), mezcla(caraDesde.inclina, caraHasta.inclina, k),
            mezcla(caraDesde.boca, caraHasta.boca, k), mezcla(caraDesde.onda, caraHasta.onda, k)};
}

void fijarNivel(int nivel, uint32_t ahora) {
    if (nivel == caraNivel) return;
    caraDesde = caraNivel < 0 ? CARA_NIVEL[nivel] : caraEn(ahora);   // arranca desde lo que se veia: sin saltos
    caraHasta = CARA_NIVEL[nivel];
    caraT0 = ahora;
    caraNivel = nivel;
}

// Parpadeo cada 3 a 5 s: medio cerrado, cerrado, medio cerrado (150 ms, 3 cuadros a 20 fps).
float factorParpadeo(uint32_t ahora) {
    if (!proximoParpadeo) proximoParpadeo = ahora + 3000 + random(2001);
    if ((int32_t)(ahora - proximoParpadeo) >= 0) { parpadeoDesde = ahora; proximoParpadeo = ahora + 3000 + random(2001); }
    if (!parpadeoDesde) return 1.0f;
    const uint32_t t = ahora - parpadeoDesde;
    return t < 50 ? 0.5f : t < 100 ? 0.0f : t < 150 ? 0.5f : 1.0f;
}

// Desplazamiento horizontal de la fila y: crece hacia arriba (la planta se dobla en vez de moverse rigida).
int desv(int y, float seno, float amp) { return y < Y_MATERA ? (int)lroundf(amp * seno * (Y_MATERA - y) / (float)Y_MATERA) : 0; }

void cuerpo(const uint8_t *bmp, float seno, float amp, int dy) {
    for (int y = 0; y < Y_MATERA; y++) oled.drawBitmap(desv(y, seno, amp), y + dy, bmp + y * 6, 48, 1, SSD1306_WHITE);
    oled.drawBitmap(0, Y_MATERA, bmp + Y_MATERA * 6, 48, 64 - Y_MATERA, SSD1306_WHITE);
}

// Boca en curva: boca>0 sonrisa, <0 ceno; `onda` la ondula. u va de -1 a 1 a lo ancho.
int yBoca(int x, float cx, float semi, int y0, float amplitud, const Cara &c) {
    const float u = (x - cx) / semi;
    return y0 - (int)lroundf(c.boca * amplitud * u * u) + (int)lroundf(c.onda * 0.9f * sinf(x * 2.3f));
}

// Cara de la matera comun: ojos 3x3 (2 o 1 de alto al entrecerrar), cejas y boca de 6 px.
void caraMatera(const Cara &c, float parpadeo, uint32_t ahora) {
    const float a = c.abierto * parpadeo;
    const int h = a >= 0.85f ? 3 : a >= 0.4f ? 2 : 1;
    for (int ex : {17, 28}) {
        oled.fillRect(ex, 58 - h, 3, h, SSD1306_BLACK);
        if (h == 3) { oled.drawPixel(ex, 55, SSD1306_WHITE); oled.drawPixel(ex + 2, 55, SSD1306_WHITE); }
    }
    if (c.inclina > 0.2f) {
        const int dentro = 53 - (int)lroundf(c.inclina), medio = 53 - (c.inclina > 0.5f ? 1 : 0);
        oled.drawPixel(17, 53, SSD1306_BLACK); oled.drawPixel(18, medio, SSD1306_BLACK); oled.drawPixel(19, dentro, SSD1306_BLACK);
        oled.drawPixel(30, 53, SSD1306_BLACK); oled.drawPixel(29, medio, SSD1306_BLACK); oled.drawPixel(28, dentro, SSD1306_BLACK);
    }
    for (int x = 21; x <= 26; x++) {
        int y = yBoca(x, 23.5f, 2.5f, 60, 1.2f, c);
        oled.drawPixel(x, y < 59 ? 59 : y > 62 ? 62 : y, SSD1306_BLACK);
    }
    if (caraNivel == 2) {                                         // lagrima ocasional
        const uint32_t fase = ahora % 4200;
        if (fase < 900) {
            const int y = 56 + (int)(fase * 5 / 900);
            oled.drawPixel(31, y, SSD1306_BLACK); oled.drawPixel(30, y + 1, SSD1306_BLACK);
            oled.drawPixel(31, y + 1, SSD1306_BLACK); oled.drawPixel(32, y + 1, SSD1306_BLACK); oled.drawPixel(31, y + 2, SSD1306_BLACK);
        }
    }
}

// Cara de la carnivora: ojos 5 de ancho (alto segun lo abiertos), cejas y, si esta triste, la boca.
void caraTrampa(const Cara &c, float parpadeo, uint32_t ahora, float seno, float amp, int yo, int dy, bool conBoca) {
    const float a = c.abierto * parpadeo;
    const int h = max(1, (int)lroundf(7 * a));
    const int cx = 24 + desv(yo, seno, amp);
    for (int lado = -1; lado <= 1; lado += 2) {
        const int ex = cx + 9 * lado;
        const int top = yo + dy - h / 2;
        oled.fillRoundRect(ex - 2, top, 5, h, h >= 4 ? 2 : h >= 2 ? 1 : 0, SSD1306_BLACK);
        if (h >= 5) oled.drawPixel(ex - 1, top + 1, SSD1306_WHITE);
        if (c.inclina > 0.2f) {                                   // el extremo de adentro mas alto: ceja triste
            const int fuera = yo + dy - 6 + (int)lroundf(c.inclina), dentro = yo + dy - 6 - (int)lroundf(2 * c.inclina);
            oled.drawLine(ex + 4 * lado, fuera, ex - 3 * lado, dentro, SSD1306_BLACK);
        }
    }
    if (conBoca) {
        for (int x = cx - 7; x <= cx + 7; x++) oled.drawPixel(x, yBoca(x, (float)cx, 7.0f, yo + 2 + dy, 4.0f, c), SSD1306_BLACK);
        if (caraNivel == 2) {
            const uint32_t fase = ahora % 4200;
            if (fase < 900) { const int y = yo + dy + 3 + (int)(fase * 7 / 900); oled.drawPixel(cx + 13, y, SSD1306_BLACK); oled.drawPixel(cx + 13, y + 1, SSD1306_BLACK); }
        }
    }
}

// ---- estado de la pantalla de lecturas (para repintar sin que main reenvie todo) ----
Lectura lLect = {};
char lNombre[64] = "";
char lEstado[16] = "";
bool lPunto = false;
bool lCarnivora = false;
int lNivel = 0;                         // 0 saludable, 1 en riesgo, 2 critico
uint32_t lUltimoDibujo = 0;
bool mordiendo = false;
uint32_t mordiscoDesde = 0;
uint32_t proximoMordisco = 0;

bool contieneSinMayusculas(const char *pajar, const char *aguja) {
    const size_t n = strlen(aguja);
    for (; *pajar; pajar++) {
        size_t i = 0;
        while (i < n && pajar[i] && tolower((unsigned char)pajar[i]) == aguja[i]) i++;
        if (i == n) return true;
    }
    return false;
}

void pintarLecturas(uint32_t ahora) {
    limpiar();
    modo = Modo::Lecturas;
    lUltimoDibujo = ahora;
    fijarNivel(lNivel, ahora);
    const Cara c = caraEn(ahora);
    const float parpadeo = factorParpadeo(ahora);
    const float seno = sinf(6.2831853f * (ahora % PERIODO_BALANCEO_MS) / PERIODO_BALANCEO_MS);
    const float amp = AMPLITUD[lNivel];
    if (!lCarnivora) {
        cuerpo(PLANTA[lNivel], seno, amp, 0);
        caraMatera(c, parpadeo, ahora);
    } else if (lNivel > 0) {
        cuerpo(CARNI_TRISTE[lNivel - 1], seno, amp, 0);
        caraTrampa(c, parpadeo, ahora, seno, amp, lNivel == 1 ? 25 : 30, 0, true);
    } else {
        if (!mordiendo && (int32_t)(ahora - proximoMordisco) >= 0) {          // mordisco espontaneo de vez en cuando
            mordiendo = true; mordiscoDesde = ahora;
            proximoMordisco = ahora + 6000 + (ahora % 4000);
        }
        int boca = 0, dy = 0;
        if (mordiendo) {
            const uint32_t t = ahora - mordiscoDesde;
            if (t >= MORDISCO_MS) mordiendo = false;
            else { int i = 0; while (t >= MORDISCO[i].hasta) i++; boca = MORDISCO[i].boca; dy = MORDISCO[i].dy; }
        }
        cuerpo(CARNI_BOCA[boca], seno, amp, dy);
        Cara cm = c;
        if (boca == 2) cm.abierto *= 0.3f;                                    // al morder entrecierra los ojos
        caraTrampa(cm, parpadeo, ahora, seno, amp, (32 - CARNI_G[boca]) / 2, dy, false);
    }
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
    nombreEnVentana(0, 50, 47, 78, lNombre, ahora);
    texto(50, 56, lEstado);
    if (lPunto) oled.fillCircle(123, 59, 3, SSD1306_WHITE);
    listo();
}

// ---- animacion de exito ----
uint32_t exitoDesde = 0;
char exitoNombre[64] = "";
uint32_t exitoUltimo = 0;
bool exitoPintado = false;
constexpr uint32_t EXITO_MS_CUADRO = 125;                    // 8 cuadros por segundo para el arte
constexpr uint32_t T_ANILLO = 6 * EXITO_MS_CUADRO;           // 750: despues del destello
constexpr uint32_t T_VISTO = T_ANILLO + 250;                 // el visto se traza de 1000 a 1450
constexpr uint32_t T_FIN_VISTO = T_VISTO + 450;
constexpr uint32_t T_DESLIZ = T_FIN_VISTO + 200;             // 1650 a 2050 se desliza a la izquierda
constexpr uint32_t T_FIN_DESLIZ = T_DESLIZ + 400;
constexpr uint32_t EXITO_TOTAL = T_FIN_DESLIZ + 1400;

void estrellita(int x, int y) {
    oled.drawFastHLine(x - 3, y, 7, SSD1306_WHITE); oled.drawFastVLine(x, y - 3, 7, SSD1306_WHITE);
}

// Anillo de 3 px y visto trazado hasta `p` (0..1), en un cuadro de 64x64 que empieza en xo.
void anilloYVisto(int xo, float p) {
    for (int r = 28; r <= 30; r++) oled.drawCircle(xo + 32, 32, r, SSD1306_WHITE);
    const float L1 = 15.6f, L2 = 29.4f;
    const float d = p * (L1 + L2);
    for (float s = 0; s <= d; s += 1.0f) {
        float x, y;
        if (s <= L1) { x = 17 + 11 * s / L1; y = 33 + 11 * s / L1; }
        else { const float k = (s - L1) / L2; x = 28 + 19 * k; y = 44 - 23 * k; }
        oled.fillCircle(xo + (int)x, (int)y, 2, SSD1306_WHITE);
    }
    if (p >= 1.0f) { estrellita(xo + 6, 56); estrellita(xo + 58, 6); }
}

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

// Mide un display() completo a 400 kHz: es el techo real de cuadros por segundo.
static void medirCuadro() {
    Wire.setClock(400000);
    oled.clearDisplay();
    const uint32_t t0 = micros();
    oled.display();
    const uint32_t us = max<uint32_t>(1, micros() - t0);
    registrar("OLED: %.1f ms por cuadro (tope %u cuadros/s)", us / 1000.0f, (unsigned)(1000000UL / us));
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
    if (lista) { dirOled = direccion; vistaAlgunaVez = true; registrar("OLED lista en 0x%02X", direccion); medirCuadro(); }
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
    Wire.setClock(400000);
    pinSda = sda; pinScl = scl; dirOled = direccion; vistaAlgunaVez = true; lista = true;
    registrar("OLED lista en 0x%02X (detectada en caliente)", direccion);
    medirCuadro();
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
    exitoPintado = false;
    aAscii(codigo, exitoNombre, sizeof exitoNombre);
}

bool animarExito(uint32_t ahoraMs) {
    const uint32_t t = ahoraMs - exitoDesde;
    if (t >= EXITO_TOTAL) return false;
    if (!lista) return true;
    if (exitoPintado && ahoraMs - exitoUltimo < MS_FOTOGRAMA) return true;     // 20 cuadros por segundo
    exitoPintado = true;
    exitoUltimo = ahoraMs;
    limpiar();
    modo = Modo::Exito;
    int xo = 32;
    if (t >= T_DESLIZ) xo = 32 - (int)(suave((float)(t - T_DESLIZ) / (T_FIN_DESLIZ - T_DESLIZ)) * 30);
    if (t < T_ANILLO) {
        bitmap(xo, 0, EXITO[t / EXITO_MS_CUADRO], 64, 64);
    } else {
        float p = t <= T_VISTO ? 0.0f : t >= T_FIN_VISTO ? 1.0f : (float)(t - T_VISTO) / (T_FIN_VISTO - T_VISTO);
        p = 1.0f - (1.0f - p) * (1.0f - p);                                       // arranca rapido y frena
        anilloYVisto(xo, p);
        if (t >= T_FIN_DESLIZ) { texto(70, 22, "Vinculada"); nombreEnVentana(1, 70, 36, 58, exitoNombre, t - T_FIN_DESLIZ); }
    }
    listo();
    return true;
}

void vinculada(const char *codigo) {
    if (!lista) return;   // sin OLED no hay bufer: dibujar escribiria en una direccion nula
    limpiar();
    anilloYVisto(2, 1.0f);
    char n[64];
    aAscii(codigo, n, sizeof n);
    texto(70, 22, "Vinculada");
    nombreEnVentana(1, 70, 36, 58, n, 0);
    listo();
}

void lecturas(const char *codigo, const Lectura &l, const char *estado, bool punto, const char *especie) {
    if (!lista) return;   // sin OLED no hay bufer: dibujar escribiria en una direccion nula
    lLect = l;
    lPunto = punto;
    aAscii(codigo, lNombre, sizeof lNombre);
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
    if (ahoraMs - lUltimoDibujo < MS_FOTOGRAMA) return;       // 20 cuadros por segundo, sin esperar
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
