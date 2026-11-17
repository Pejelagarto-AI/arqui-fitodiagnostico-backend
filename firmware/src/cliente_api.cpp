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
String especieActual;
api::Rangos rangosActuales = {};

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

// "apodo" de primer nivel (200 de la credencial y 201 de las lecturas). Si falta o viene vacio se conserva el ultimo
// conocido (el de NVS); solo se escribe en la flash cuando cambia.
void tomarApodo(JsonDocument &r) {
    const char *nuevo = r["apodo"] | "";
    if (!nuevo[0]) return;
    String actual = identidad::apodo();
    if (actual != nuevo) { identidad::guardarApodo(nuevo); registrar("apodo de la planta: %s", nuevo); }
}

String mensajeDeError(const String &cuerpo, String *codigoError = nullptr) {
    JsonDocument d;
    if (deserializeJson(d, cuerpo)) return cuerpo.substring(0, 80);
    if (codigoError) *codigoError = d["error"] | "";
    return String(d["error"] | "?") + ": " + String(d["mensaje"] | "");
}
}

namespace api {
const String &especie() { return especieActual; }
const Rangos &rangos() { return rangosActuales; }

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
        tomarApodo(t);
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

// Que se queda del 201 de la lectura. Sin filtro el documento guarda todo (parametros, recomendaciones, historial de la
// mascota con sus horas): con el filtro solo lo que se usa, y los 10 eventos de la mascota (fase 14) caben holgados.
static JsonDocument filtroDeLectura() {
    JsonDocument f;
    f["apodo"] = true;
    f["diagnostico"]["estado"] = true;
    f["diagnostico"]["periodo"] = true;
    f["diagnostico"]["especie"] = true;
    f["diagnostico"]["parametros"][0]["nombre"] = true;
    f["diagnostico"]["parametros"][0]["rangoOptimo"] = true;
    f["mascota"]["vitalidad"] = true;
    f["mascota"]["animo"] = true;
    f["mascota"]["accesorio"] = true;
    f["mascota"]["eventos"][0]["tipo"] = true;
    return f;
}

// "mascota" de la 201 (fase 14). Si la clave no viene (servidor viejo) queda hay = false.
static void tomarMascota(JsonObject r, EstadoMascota &m) {
    m = EstadoMascota();
    JsonObject mj = r["mascota"];
    if (mj.isNull()) return;
    m.hay = true;
    m.vitalidad = (uint8_t)constrain((int)(mj["vitalidad"] | 0), 0, 100);
    m.animo = (int8_t)fito_animo_desde_texto(mj["animo"] | "");
    m.accesorio = (int8_t)fito_accesorio_desde_texto(mj["accesorio"] | "NINGUNO");
    for (JsonObject e : mj["eventos"].as<JsonArray>()) {
        if (m.nEventos >= FITO_COLA_EV_MAX) break;
        m.eventos[m.nEventos++] = (int8_t)fito_evento_desde_texto(e["tipo"] | "");
    }
}

int enviarLectura(const String &token, const Lectura &l, String &estado, bool &noche, EstadoMascota &mascota) {
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
    noche = false;
    mascota = EstadoMascota();
    if (codigo == 201) {
        JsonDocument r;
        if (!deserializeJson(r, resp, DeserializationOption::Filter(filtroDeLectura()))) {
            tomarMascota(r.as<JsonObject>(), mascota);
            estado = (const char *)(r["diagnostico"]["estado"] | "");
            noche = !strcmp(r["diagnostico"]["periodo"] | "DIA", "NOCHE");
            tomarApodo(r);
            const char *esp = r["diagnostico"]["especie"] | "";
            if (esp[0] && especieActual != esp) { especieActual = esp; registrar("especie de la planta: %s", esp); }
            Rangos nuevos = {};
            int cuantos = 0;
            for (JsonObject p : r["diagnostico"]["parametros"].as<JsonArray>()) {
                const char *nombre = p["nombre"] | "";
                int i = !strcmp(nombre, "humedad") ? 0 : !strcmp(nombre, "luz") ? 1 : !strcmp(nombre, "temperatura") ? 2 : -1;
                JsonArray rango = p["rangoOptimo"].as<JsonArray>();
                if (i < 0 || rango.size() != 2) continue;
                nuevos.minimo[i] = rango[0].as<float>();
                nuevos.maximo[i] = rango[1].as<float>();
                cuantos++;
            }
            if (cuantos == 3) { nuevos.hay = true; rangosActuales = nuevos; }
        }
    } else if (codigo != SIN_RED) {
        registrar("lectura HTTP %d %s", codigo, mensajeDeError(resp).c_str());
    }
    return codigo;
}

int acariciar(const String &token) {
    JsonDocument d;
    d["tipo"] = "ACARICIAR";
    String cuerpo, resp;
    serializeJson(d, cuerpo);
    int codigo = post(String("/dispositivos/") + identidad::serial() + "/mascota/acciones", cuerpo, token.c_str(), resp);
    if (codigo != 200 && codigo != 201 && codigo != SIN_RED) registrar("caricia HTTP %d %s", codigo, mensajeDeError(resp).c_str());
    return codigo;
}

int enviarSalud(const String &token, const bool falla[3], const char *detalle) {
    static const char *const NOMBRES[3] = {"humedad", "luz", "temperatura"};
    JsonDocument d;
    for (int i = 0; i < 3; i++) d["sensores"][NOMBRES[i]] = falla[i] ? "FALLA" : "OK";
    if (detalle && detalle[0]) d["detalle"] = String(detalle).substring(0, 200);   // el servidor acepta hasta 200
    String cuerpo, resp;
    serializeJson(d, cuerpo);
    int codigo = post(String("/dispositivos/") + identidad::serial() + "/salud", cuerpo, token.c_str(), resp);
    if (codigo == 200) {
        JsonDocument r;
        if (!deserializeJson(r, resp)) registrar("salud HTTP 200 estado=%s", (const char *)(r["estado"] | "?"));
    } else if (codigo != SIN_RED) {
        registrar("salud HTTP %d %s", codigo, mensajeDeError(resp).c_str());
    }
    return codigo;
}
}
