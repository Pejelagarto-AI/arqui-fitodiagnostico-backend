#include "cliente_api.h"
#include "configuracion.h"
#include "identidad.h"
#include "registro.h"
#include "reloj.h"
#include "nucleo/fito_nucleo.h"
#include <ArduinoJson.h>
#include <HTTPClient.h>
#include <WiFi.h>

namespace {
String base;

// POST con cuerpo ya serializado. Si aun no hay hora, toma la de la cabecera Date de la respuesta (D91).
int post(const String &ruta, const String &cuerpo, const char *token, String &respuesta) {
    if (WiFi.status() != WL_CONNECTED) return api::SIN_RED;
    HTTPClient http;
    http.setConnectTimeout(config::TIMEOUT_HTTP_MS);
    http.setTimeout(config::TIMEOUT_HTTP_MS);
    http.setReuse(false);
    static const char *cabeceras[] = {"Date"};
    http.collectHeaders(cabeceras, 1);
    if (!http.begin(base + ruta)) return api::SIN_RED;
    http.addHeader("Content-Type", "application/json");
    if (token && token[0]) http.addHeader("Authorization", String("Bearer ") + token);
    int codigo = http.POST(cuerpo);
    if (codigo <= 0) {
        registrar("sin respuesta del servidor (%s)", HTTPClient::errorToString(codigo).c_str());
        http.end();
        return api::SIN_RED;
    }
    respuesta = http.getString();
    if (!reloj::hayHora() && http.hasHeader("Date") && reloj::desdeCabeceraDate(http.header("Date").c_str()))
        registrar("hora tomada de la cabecera Date del servidor");
    http.end();
    return codigo;
}

String mensajeDeError(const String &cuerpo, String *codigoError = nullptr) {
    JsonDocument d;
    if (deserializeJson(d, cuerpo)) return cuerpo.substring(0, 80);
    if (codigoError) *codigoError = d["error"] | "";
    return String(d["error"] | "?") + ": " + String(d["mensaje"] | "");
}
}

namespace api {
void iniciar(const String &servidor) {
    base = "http://" + servidor + ":" + String(FITO_PUERTO_API) + "/api/v1";
    registrar("API en %s", base.c_str());
}

int registro() {
    char firma[FITO_LARGO_FIRMA_B64];
    fito_firma_de_flota(FITO_CLAVE_FLOTA, identidad::serial(), identidad::secreto(), firma);
    JsonDocument d;
    d["serial"] = identidad::serial();
    d["secreto"] = identidad::secreto();
    d["firma"] = firma;
    String cuerpo, resp;
    serializeJson(d, cuerpo);
    int codigo = post("/dispositivos/registro", cuerpo, nullptr, resp);
    if (codigo == 200 || codigo == 201) {
        JsonDocument r;
        deserializeJson(r, resp);
        registrar("registro HTTP %d: %s %s", codigo, (const char *)(r["serial"] | identidad::serial()), (const char *)(r["estado"] | ""));
    } else if (codigo != SIN_RED) {
        registrar("registro HTTP %d %s", codigo, mensajeDeError(resp).c_str());
    }
    return codigo;
}

int pedirCredencial(String &token, time_t &expira) {
    String ruta = String("/dispositivos/") + identidad::serial();
    String resp;
    int codigo = post(ruta + "/reto", "", nullptr, resp);
    if (codigo != 200) {
        if (codigo != SIN_RED) registrar("reto HTTP %d %s", codigo, mensajeDeError(resp).c_str());
        return codigo;
    }
    JsonDocument r;
    if (deserializeJson(r, resp) || !r["reto"].is<const char *>()) { registrar("reto ilegible"); return 500; }
    char respuesta[FITO_LARGO_FIRMA_B64];
    fito_respuesta_al_reto(identidad::secreto(), r["reto"], respuesta);
    JsonDocument c;
    c["respuesta"] = respuesta;
    String cuerpo;
    serializeJson(c, cuerpo);
    codigo = post(ruta + "/credencial", cuerpo, nullptr, resp);
    if (codigo == 200) {
        JsonDocument t;
        if (deserializeJson(t, resp) || !t["token"].is<const char *>()) { registrar("credencial ilegible"); return 500; }
        token = (const char *)t["token"];
        const char *vence = t["expira"] | "";
        if (!reloj::parsearIso(vence, &expira)) expira = time(nullptr) + 3600;
        registrar("credencial obtenida para la planta %.8s (vence %s)", (const char *)(t["plantaId"] | "?"), vence);
    } else if (codigo == 409) {
        registrar("esperando reclamo del serial %s...", identidad::serial());
    } else if (codigo != SIN_RED) {
        registrar("credencial HTTP %d %s", codigo, mensajeDeError(resp).c_str());
    }
    return codigo;
}

int enviarLectura(const String &token, const Lectura &l, String &estado) {
    char marca[24];
    reloj::iso(l.cuando, marca, sizeof marca);
    JsonDocument d;
    d["marcaDeTiempo"] = marca;
    d["humedad"] = roundf(l.humedad * 10) / 10;
    d["luz"] = roundf(l.luz * 10) / 10;
    d["temperatura"] = roundf(l.temperatura * 10) / 10;
    String cuerpo, resp;
    serializeJson(d, cuerpo);
    int codigo = post(String("/dispositivos/") + identidad::serial() + "/lecturas", cuerpo, token.c_str(), resp);
    estado = "";
    if (codigo == 201) {
        JsonDocument r;
        if (!deserializeJson(r, resp)) estado = (const char *)(r["diagnostico"]["estado"] | "");
    } else if (codigo != SIN_RED) {
        registrar("lectura HTTP %d %s", codigo, mensajeDeError(resp).c_str());
    }
    return codigo;
}
}
