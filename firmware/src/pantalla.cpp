#include "pantalla.h"
#include "configuracion.h"
#include "registro.h"
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <Wire.h>
#include <qrcode.h>

namespace {
Adafruit_SSD1306 oled(128, 64, &Wire, -1);
bool lista = false;

constexpr int VERSION_QR = 3;       // 29 modulos x 2 px = 58 px: cabe en los 64 px de alto con 3 px de margen
constexpr int MODULO_PX = 2;
constexpr size_t CAPACIDAD_QR_L = 53;
constexpr size_t CAPACIDAD_QR_M = 42;

void limpiar() { oled.clearDisplay(); oled.setTextColor(SSD1306_WHITE); oled.setTextSize(1); oled.setCursor(0, 0); }
void listo() { if (lista) oled.display(); }
}

namespace pantalla {
bool iniciar() {
    Wire.begin(config::PIN_SDA, config::PIN_SCL);
    bool hay3c = false, hay3d = false;
    Wire.beginTransmission(0x3C); hay3c = Wire.endTransmission() == 0;
    Wire.beginTransmission(0x3D); hay3d = Wire.endTransmission() == 0;
    registrar("escaneo I2C: 0x3C %s, 0x3D %s", hay3c ? "responde" : "no", hay3d ? "responde" : "no");
    uint8_t direccion = hay3c ? 0x3C : (hay3d ? 0x3D : 0);
    if (!direccion) {
        registrar("la OLED no responde: revise cableado (SDA=GPIO%d, SCL=GPIO%d, VCC=3V3). Sigo sin pantalla.",
                  config::PIN_SDA, config::PIN_SCL);
        return false;
    }
    lista = oled.begin(SSD1306_SWITCHCAPVCC, direccion);
    if (lista) registrar("OLED lista en 0x%02X", direccion);
    return lista;
}

void arranque(const char *serial) {
    limpiar();
    oled.setTextSize(2); oled.println("Fito");
    oled.setTextSize(1); oled.println("Fitodiagnostico");
    oled.println();
    oled.println(serial);
    oled.println("Iniciando...");
    listo();
}

void mensaje(const char *l1, const char *l2, const char *l3, const char *l4) {
    limpiar();
    oled.println(l1); oled.println(l2); oled.println(l3); oled.println(l4);
    listo();
}

void portal(const char *redWifi) {
    limpiar();
    oled.println("Conectate al");
    oled.println("Wi-Fi:");
    oled.setTextSize(2); oled.println(redWifi);
    oled.setTextSize(1);
    oled.println("y pon la red y el");
    oled.println("servidor");
    listo();
}

void qr(const String &enlace, const char *codigo, uint8_t puntos) {
    limpiar();
    const size_t largo = enlace.length();
    const uint8_t ecc = largo <= CAPACIDAD_QR_M ? ECC_MEDIUM : ECC_LOW;
    if (largo <= CAPACIDAD_QR_L) {
        QRCode qrc;
        uint8_t *buffer = (uint8_t *)malloc(qrcode_getBufferSize(VERSION_QR));
        if (buffer && qrcode_initText(&qrc, buffer, VERSION_QR, ecc, enlace.c_str()) == 0) {
            const int lado = qrc.size * MODULO_PX;          // 58
            const int margen = (64 - lado) / 2;             // 3
            oled.fillRect(0, 0, 64, 64, SSD1306_WHITE);     // cuadro encendido, modulos apagados
            for (int y = 0; y < qrc.size; y++)
                for (int x = 0; x < qrc.size; x++)
                    if (qrcode_getModule(&qrc, x, y))
                        oled.fillRect(margen + x * MODULO_PX, margen + y * MODULO_PX, MODULO_PX, MODULO_PX, SSD1306_BLACK);
            free(buffer);
            oled.setCursor(68, 0);  oled.print("Escanea");
            oled.setCursor(68, 12); oled.print("fito-");
            char a[4], b[4];
            strlcpy(a, codigo, 4);
            strlcpy(b, codigo + 3, 4);
            oled.setTextSize(2);
            oled.setCursor(68, 22); oled.print(a);
            oled.setCursor(68, 40); oled.print(b);
            oled.setTextSize(1);
            oled.setCursor(68, 57);
            oled.print("Esperando");
            for (uint8_t i = 0; i < puntos % 3 + 1; i++) oled.print('.');
            listo();
            return;
        }
        free(buffer);
    }
    // No cabe: serial en texto grande y aviso por serie (el QR de version 4 mide 66 px y no entra en 64).
    registrar("el enlace (%u bytes) no cabe en el QR version 3: se muestra el serial en texto", (unsigned)largo);
    oled.println("Matera nueva");
    oled.setTextSize(2); oled.println("fito-"); oled.println(codigo);
    oled.setTextSize(1); oled.println("Esperando...");
    listo();
}

void vinculada() {
    limpiar();
    oled.setTextSize(2); oled.println("Vinculada");
    oled.setTextSize(1); oled.println();
    // visto
    oled.drawLine(50, 40, 58, 50, SSD1306_WHITE); oled.drawLine(58, 50, 78, 28, SSD1306_WHITE);
    oled.drawLine(50, 41, 58, 51, SSD1306_WHITE); oled.drawLine(58, 51, 78, 29, SSD1306_WHITE);
    oled.setCursor(0, 56); oled.print("Enviando lecturas");
    listo();
}

void lecturas(const char *serial, const Lectura &l, const char *estado, bool punto) {
    limpiar();
    oled.println(serial);
    oled.printf("Humedad %5.1f %%\n", l.humedad);
    oled.printf("Luz     %5.0f lx\n", l.luz);
    oled.printf("Temp    %5.1f C\n", l.temperatura);
    oled.println();
    oled.println(estado && estado[0] ? estado : "-");
    if (punto) oled.fillCircle(122, 5, 3, SSD1306_WHITE);
    listo();
}

void sinRed(size_t guardadas) {
    limpiar();
    oled.setTextSize(2); oled.println("Sin red");
    oled.setTextSize(1); oled.println();
    oled.printf("(%u guardadas)\n", (unsigned)guardadas);
    oled.println("Reintentando...");
    listo();
}

void error(const char *l1, const char *l2, const char *l3) {
    limpiar();
    oled.println("ERROR"); oled.println();
    oled.println(l1); oled.println(l2); oled.println(l3);
    listo();
}

void reinicio(int segundos) {
    limpiar();
    oled.println("Borrando Wi-Fi...");
    oled.println("Suelta para cancelar");
    oled.setTextSize(3); oled.setCursor(52, 30); oled.print(segundos);
    listo();
}
}
