#include "identidad.h"
#include "registro.h"
#include "nucleo/fito_nucleo.h"
#include <Preferences.h>
#include <esp_system.h>
#include <esp_mac.h>

namespace {
Preferences nvs;
char serial_[FITO_LARGO_SERIAL + 1];
char secreto_[FITO_LARGO_SECRETO_B64 + 1];
uint8_t mac_[6];
}

namespace identidad {
void iniciar() {
    nvs.begin("fito", false);
    esp_read_mac(mac_, ESP_MAC_WIFI_STA);
    String guardado = nvs.getString("serial", "");
    String secretoGuardado = nvs.getString("secreto", "");
    if (guardado.length() == 11 && secretoGuardado.length() >= 40) {
        strlcpy(serial_, guardado.c_str(), sizeof serial_);
        strlcpy(secreto_, secretoGuardado.c_str(), sizeof secreto_);
        registrar("identidad %s (NVS, %s)", serial_, nvs.getBool("registrado", false) ? "ya registrada" : "sin registrar");
        return;
    }
    // Primer arranque: serial desde la MAC, secreto de 32 bytes del RNG de hardware.
    fito_serial_desde_mac(mac_, serial_);
    uint8_t azar[32];
    esp_fill_random(azar, sizeof azar);
    fito_secreto_desde_bytes(azar, secreto_);
    nvs.putString("serial", serial_);
    nvs.putString("secreto", secreto_);
    nvs.putBool("registrado", false);
    registrar("identidad automatica %s (MAC %s, nueva, sin registrar)", serial_, macTexto().c_str());
}

const char *serial() { return serial_; }
const char *codigo() { return serial_ + 5; }
const char *secreto() { return secreto_; }

String macTexto() {
    char b[18];
    snprintf(b, sizeof b, "%02x:%02x:%02x:%02x:%02x:%02x", mac_[0], mac_[1], mac_[2], mac_[3], mac_[4], mac_[5]);
    return String(b);
}

bool registrado() { return nvs.getBool("registrado", false); }
void marcarRegistrado(bool v) { nvs.putBool("registrado", v); }

String wifiSsid() { return nvs.getString("ssid", ""); }
String wifiClave() { return nvs.getString("clave", ""); }
String servidor() { return nvs.getString("servidor", ""); }
void guardarRed(const String &ssid, const String &clave) {
    nvs.putString("ssid", ssid);
    nvs.putString("clave", clave);
}
void guardarServidor(const String &s) { nvs.putString("servidor", s); }

String token() { return nvs.getString("token", ""); }
time_t tokenExpira() { return (time_t)nvs.getULong("expira", 0); }
void guardarToken(const String &t, time_t expira) {
    nvs.putString("token", t);
    nvs.putULong("expira", (uint32_t)expira);
}
String apodo() { return nvs.getString("apodo", ""); }
void guardarApodo(const String &a) { nvs.putString("apodo", a); }
void borrarToken() {
    nvs.remove("apodo");
    nvs.remove("token");
    nvs.remove("expira");
}

void olvidarRedYToken() {
    nvs.remove("ssid");
    nvs.remove("clave");
    nvs.remove("servidor");
    borrarToken();
}
}
