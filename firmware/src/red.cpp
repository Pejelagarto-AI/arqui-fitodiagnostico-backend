#include "red.h"
#include "configuracion.h"
#include "identidad.h"
#include "registro.h"
#include <WiFi.h>
#include <WiFiManager.h>

namespace {
red::Estado estado_ = red::Estado::Conectando;
uint32_t inicioIntento = 0;
WiFiManager *gestor = nullptr;
WiFiManagerParameter *paramServidor = nullptr;
char nombrePortal[20];
bool usoDefectos = false;

void alGuardarParametros() {
    if (paramServidor && strlen(paramServidor->getValue()) > 0) identidad::guardarServidor(paramServidor->getValue());
}

void empezarPortal() {
    registrar("sin Wi-Fi utilizable: portal cautivo, red abierta %s", nombrePortal);
    WiFi.disconnect(true, true);
    WiFi.mode(WIFI_AP_STA);
    gestor = new WiFiManager();
    gestor->setConfigPortalBlocking(false);
    gestor->setSaveParamsCallback(alGuardarParametros);
    gestor->setTitle("Matera Fito");
    gestor->setCountry("CO");
    paramServidor = new WiFiManagerParameter("servidor", "Servidor (IP del portatil)", identidad::servidor().c_str(), 40);
    gestor->addParameter(paramServidor);
    gestor->startConfigPortal(nombrePortal);
    estado_ = red::Estado::Portal;
}

void yaConectada() {
    identidad::guardarRed(WiFi.SSID(), WiFi.psk());
    if (paramServidor && strlen(paramServidor->getValue()) > 0) identidad::guardarServidor(paramServidor->getValue());
    if (gestor) { delete gestor; gestor = nullptr; }
    if (paramServidor) { delete paramServidor; paramServidor = nullptr; }
    WiFi.mode(WIFI_STA);
    registrar("Wi-Fi conectado a %s, IP %s, servidor %s", WiFi.SSID().c_str(), WiFi.localIP().toString().c_str(),
              red::servidor().c_str());
    estado_ = red::servidor().length() ? red::Estado::Conectada : red::Estado::SinServidor;
}
}

namespace red {
String servidor() {
    String s = identidad::servidor();
    if (!s.length()) s = FITO_SERVIDOR;
    return s;
}

String ssidDelPortal() { return String(nombrePortal); }

void iniciar() {
    snprintf(nombrePortal, sizeof nombrePortal, "Fito-%s", identidad::codigo());
    String ssid = identidad::wifiSsid();
    String clave = identidad::wifiClave();
    if (!ssid.length() && strlen(FITO_WIFI_SSID) > 0) {
        ssid = FITO_WIFI_SSID;
        clave = FITO_WIFI_CLAVE;
        usoDefectos = true;
        registrar("Wi-Fi por defecto de compilacion: %s", ssid.c_str());
    }
    if (!ssid.length()) { empezarPortal(); return; }
    WiFi.persistent(false);
    WiFi.mode(WIFI_STA);
    WiFi.setSleep(false);
    WiFi.begin(ssid.c_str(), clave.c_str());
    inicioIntento = millis();
    estado_ = Estado::Conectando;
    registrar("conectando a %s (hasta %u s)...", ssid.c_str(), (unsigned)(config::ESPERA_WIFI_MS / 1000));
}

void ciclo() {
    switch (estado_) {
    case Estado::Conectando:
        if (WiFi.status() == WL_CONNECTED) {
            if (usoDefectos) {
                identidad::guardarRed(FITO_WIFI_SSID, FITO_WIFI_CLAVE);
                if (strlen(FITO_SERVIDOR) > 0) identidad::guardarServidor(FITO_SERVIDOR);
            }
            yaConectada();
        } else if (millis() - inicioIntento > config::ESPERA_WIFI_MS) {
            registrar("no conecto en %u s", (unsigned)(config::ESPERA_WIFI_MS / 1000));
            // El Wi-Fi guardado en NVS (de otra prueba, otra red) no esta: antes del portal se prueba el de
            // compilacion (local.ini), si es otro. Si conecta, queda guardado junto con el servidor de local.ini.
            if (!usoDefectos && strlen(FITO_WIFI_SSID) > 0 && identidad::wifiSsid() != FITO_WIFI_SSID) {
                usoDefectos = true;
                WiFi.disconnect();
                WiFi.begin(FITO_WIFI_SSID, FITO_WIFI_CLAVE);
                inicioIntento = millis();
                registrar("se prueba el Wi-Fi de compilacion: %s (hasta %u s)...", FITO_WIFI_SSID,
                          (unsigned)(config::ESPERA_WIFI_MS / 1000));
                break;
            }
            empezarPortal();
        }
        break;
    case Estado::Portal:
        if (gestor) gestor->process();
        if (WiFi.status() == WL_CONNECTED && WiFi.SSID().length() && WiFi.SSID() != nombrePortal) yaConectada();
        break;
    default:
        break;
    }
}

Estado estado() { return estado_; }
bool conectada() { return WiFi.status() == WL_CONNECTED; }

void reconectar() {
    WiFi.disconnect();
    WiFi.begin(identidad::wifiSsid().c_str(), identidad::wifiClave().c_str());
}
}
