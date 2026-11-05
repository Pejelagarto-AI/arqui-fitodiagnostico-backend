// Firmware de la matera de Fitodiagnostico: gemelo real de simulador/simulador_esp32.py --serial auto.
// Maquina de estados sin delay() largos: el boton BOOT y la pantalla responden siempre.
//
//   ARRANQUE -> RED -> HORA -> REGISTRO -> CREDENCIAL -> (RECLAMO <-> ) OPERANDO
//                                  \-> FATAL (clave de flota mala, serial tomado, falta configuracion)
#include <Arduino.h>
#include <WiFi.h>
#include "boton.h"
#include "cliente_api.h"
#include "cola.h"
#include "configuracion.h"
#include "identidad.h"
#include "nucleo/fito_hash.h"
#include "nucleo/fito_nucleo.h"
#include "pantalla.h"
#include "red.h"
#include "registro.h"
#include "reloj.h"
#include "sensores.h"

namespace {
enum class Fase { Arranque, Red, Hora, Registro, Credencial, Reclamo, Operando, Fatal };

Fase fase = Fase::Arranque;
uint32_t desde = 0;              // millis() de entrada a la fase
uint32_t proximoIntento = 0;     // para reintentos
String token;
time_t tokenExpira = 0;
SensoresSimulados sensoresSimulados;
Sensores *sensores = &sensoresSimulados;
uint32_t proximaLectura = 0;
uint32_t numeroDeLectura = 0;
String ultimoEstado;
bool punto = false;
uint32_t apagarPunto = 0;
bool sinRedMostrada = false;
bool sinHoraAvisada = false;
int rechazosDeCredencial = 0;
uint8_t puntosReclamo = 0;
bool exitoActivo = false;
int segundosBoton = -1;
char fatal1[24], fatal2[24], fatal3[24];
Lectura ultima = {};
bool hayUltima = false;

void entrar(Fase nueva) {
    fase = nueva;
    desde = millis();
    proximoIntento = 0;
}

void fatal(const char *l1, const char *l2 = "", const char *l3 = "") {
    strlcpy(fatal1, l1, sizeof fatal1); strlcpy(fatal2, l2, sizeof fatal2); strlcpy(fatal3, l3, sizeof fatal3);
    registrar("ERROR FATAL: %s %s %s", l1, l2, l3);
    pantalla::error(l1, l2, l3);
    entrar(Fase::Fatal);
}

String enlaceDelQr() {
    return "http://" + red::servidor() + ":" + String(FITO_PUERTO_FRONT) + "/#/v/" + identidad::codigo();
}

// Lo que se muestra de la planta: su apodo, o el codigo si el servidor nunca lo dio.
String nombreAMostrar() {
    String a = identidad::apodo();
    return a.length() ? a : String(identidad::codigo());
}

void pintarOperando() {
    if (!hayUltima) { pantalla::vinculada(nombreAMostrar().c_str()); return; }
    pantalla::lecturas(nombreAMostrar().c_str(), ultima, ultimoEstado.c_str(), punto, api::especie().c_str());
}

void empezarExito() {
    exitoActivo = true;
    pantalla::empezarExito(millis(), nombreAMostrar().c_str());
}

void redibujar() {
    switch (fase) {
    case Fase::Reclamo: pantalla::qr(enlaceDelQr(), identidad::codigo(), puntosReclamo); break;
    case Fase::Operando:
        if (!red::conectada()) pantalla::sinRed(cola::tamano()); else pintarOperando();
        break;
    case Fase::Fatal: pantalla::error(fatal1, fatal2, fatal3); break;
    case Fase::Red:
        if (red::estado() == red::Estado::Portal) pantalla::portal(red::ssidDelPortal().c_str());
        else pantalla::mensaje("Conectando al Wi-Fi", WiFi.SSID().c_str());
        break;
    default: pantalla::mensaje("Iniciando...", identidad::serial()); break;
    }
}

// El boton BOOT: cuenta regresiva en la OLED, a los 5 s borra red y token (no la identidad) y reinicia.
void atenderBoton() {
    bool cumplido;
    uint32_t ms = boton::ciclo(cumplido);
    if (cumplido) {
        registrar("BOOT sostenido 5 s: se borran Wi-Fi, servidor y token (la identidad se conserva). Reiniciando.");
        pantalla::mensaje("Wi-Fi borrado", "Reiniciando...");
        identidad::olvidarRedYToken();
        WiFi.disconnect(true, true);
        delay(800);
        ESP.restart();
    }
    if (ms >= 1000) {
        int restan = (int)((config::BOTON_REINICIO_MS - ms) / 1000) + 1;
        if (restan != segundosBoton) { segundosBoton = restan; pantalla::reinicio(restan); }
    } else if (segundosBoton != -1) {
        segundosBoton = -1;
        redibujar();                 // lo soltaron antes de tiempo
    }
}

void autotestHmac() {
    // RFC 4231, caso 2: HMAC-SHA256("Jefe", "what do ya want for nothing?") con mbedTLS.
    static const uint8_t esperado[32] = {0x5b,0xdc,0xc1,0x46,0xbf,0x60,0x75,0x4e,0x6a,0x04,0x24,0x26,0x08,0x95,0x75,0xc7,
                                         0x5a,0x00,0x3f,0x08,0x9d,0x27,0x39,0x83,0x9d,0xec,0x58,0xb9,0x64,0xec,0x38,0x43};
    uint8_t salida[32];
    const char *msg = "what do ya want for nothing?";
    fito_hmac_sha256((const uint8_t *)"Jefe", 4, (const uint8_t *)msg, strlen(msg), salida);
    registrar("autotest HMAC-SHA256 (mbedTLS): %s", memcmp(salida, esperado, 32) == 0 ? "ok" : "FALLA");
}

// ---- fases ----

void faseRed() {
    red::ciclo();
    switch (red::estado()) {
    case red::Estado::Portal:
        if (millis() - desde < 100 || segundosBoton == -1) {
            static bool pintado = false;
            if (!pintado) { pantalla::portal(red::ssidDelPortal().c_str()); pintado = true; }
        }
        break;
    case red::Estado::Conectada:
        reloj::iniciarNtp();
        entrar(Fase::Hora);
        pantalla::mensaje("Wi-Fi listo", WiFi.localIP().toString().c_str(), "Buscando la hora...");
        break;
    case red::Estado::SinServidor:
        fatal("Falta el servidor", "Borre el Wi-Fi con", "BOOT 5 s y use el portal");
        break;
    default: break;
    }
}

void faseHora() {
    if (reloj::hayHora()) { registrar("hora por NTP lista"); entrar(Fase::Registro); return; }
    if (millis() - desde > config::ESPERA_NTP_MS) {
        registrar("sin NTP en %u s: se usara la cabecera Date del backend", (unsigned)(config::ESPERA_NTP_MS / 1000));
        entrar(Fase::Registro);
    }
}

void faseRegistro() {
    if (strlen(FITO_CLAVE_FLOTA) == 0) { fatal("Falta clave de flota", "Llene local.ini", "y vuelva a compilar"); return; }
    if (identidad::registrado()) { api::iniciar(red::servidor()); entrar(Fase::Credencial); return; }
    if (millis() < proximoIntento) return;
    static bool iniciada = false;
    if (!iniciada) { api::iniciar(red::servidor()); iniciada = true; }
    pantalla::mensaje("Registrando matera", identidad::serial());
    int codigo = api::registro();
    if (codigo == 200 || codigo == 201) {
        identidad::marcarRegistrado(true);
        Serial.println("+------------------------------------------------+");
        Serial.println("| STICKER DE LA MATERA                           |");
        Serial.printf( "| serial  %-38s |\n", identidad::serial());
        Serial.printf( "| enlace  %-38s |\n", enlaceDelQr().c_str());
        Serial.println("+------------------------------------------------+");
        entrar(Fase::Credencial);
    } else if (codigo == 401) {
        fatal("Clave de flota", "no coincide con", "la del servidor");
    } else if (codigo == 409) {
        fatal("Serial ya existe", "con otro secreto", "(reinicie el backend)");
    } else if (codigo == 404) {
        fatal("Servidor sin fabrica", "automatica: arranque", "con CLAVE_FLOTA");
    } else {
        pantalla::mensaje("Sin respuesta del", "servidor", red::servidor().c_str(), "Reintentando...");
        registrar("reintento del registro en %u s", (unsigned)(config::ESPERA_REINTENTO_MS / 1000));
        proximoIntento = millis() + config::ESPERA_REINTENTO_MS;
    }
}

// Reto + credencial. Devuelve true si quedo con token vigente.
bool renovarCredencial() {
    String nuevo;
    time_t expira = 0;
    int codigo = api::pedirCredencial(nuevo, expira);
    if (codigo == 200) {
        token = nuevo; tokenExpira = expira;
        identidad::guardarToken(token, expira);
        rechazosDeCredencial = 0;
        return true;
    }
    token = ""; tokenExpira = 0;
    identidad::borrarToken();
    // Ya en Reclamo no se vuelve a entrar: entrar() reinicia el temporizador y el reintento saldria sin espera.
    if (codigo == 409) { if (fase != Fase::Reclamo) { entrar(Fase::Reclamo); redibujar(); } }
    else if (codigo == 404) { registrar("el servidor no conoce el serial: se vuelve a registrar"); identidad::marcarRegistrado(false); entrar(Fase::Registro); }
    else if (codigo == 401) {
        if (++rechazosDeCredencial >= 3) fatal("Secreto rechazado", "borre la flash o", "reinicie el backend");
    }
    return false;
}

void faseCredencial() {
    if (millis() < proximoIntento) return;
    String guardado = identidad::token();
    time_t expira = identidad::tokenExpira();
    if (guardado.length() && reloj::hayHora() && expira - time(nullptr) > (time_t)config::MARGEN_RENOVACION_S) {
        token = guardado; tokenExpira = expira;
        registrar("token guardado en NVS, vigente");
        entrar(Fase::Operando); empezarExito();
        return;
    }
    if (renovarCredencial()) {
        entrar(Fase::Operando);
        empezarExito();
        proximaLectura = millis() + 2500;
    } else if (fase == Fase::Credencial) {
        proximoIntento = millis() + config::ESPERA_REINTENTO_MS;
        pantalla::mensaje("Sin respuesta del", "servidor", "Reintentando...");
    }
}

void faseReclamo() {
    pantalla::animarEspera(millis());
    if (millis() < proximoIntento) return;
    proximoIntento = millis() + config::ESPERA_RECLAMO_MS;
    puntosReclamo++;
    if (renovarCredencial()) {
        entrar(Fase::Operando);
        empezarExito();
        proximaLectura = millis() + 2500;
        return;
    }
    proximoIntento = millis() + config::ESPERA_RECLAMO_MS;   // tambien aqui: renovarCredencial() pudo cambiar de fase
    if (fase == Fase::Reclamo) pantalla::qr(enlaceDelQr(), identidad::codigo(), puntosReclamo);
}

void tomarLectura() {
    if (!reloj::hayHora()) {
        if (!sinHoraAvisada) { registrar("sin hora valida: no se reportan lecturas"); sinHoraAvisada = true; }
        return;
    }
    Lectura l = {};
    const char *descripcion = "";
    sensores->leer(l, descripcion);
    l.cuando = time(nullptr);
    l.n = ++numeroDeLectura;
    l.fuera = strncmp(descripcion, "fuera", 5) == 0;
    registrar("#%u %-6s %-30s h=%.1f l=%.0f t=%.1f", (unsigned)l.n, l.fuera ? "FUERA" : "NORMAL", descripcion,
              l.humedad, l.luz, l.temperatura);
    cola::empujar(l);
    ultima = l; hayUltima = true;
}

// Entrega la cola en orden. Se corta si no hay red o el servidor falla; se reintenta en el siguiente ciclo.
void vaciarCola() {
    int enviados = 0;
    while (cola::tamano() && enviados < config::MAX_ENVIOS_POR_CICLO) {
        if (!red::conectada()) return;
        if (!token.length() || (reloj::hayHora() && tokenExpira - time(nullptr) < (time_t)config::MARGEN_RENOVACION_S)) {
            registrar("renovando credencial...");
            if (!renovarCredencial()) return;       // 409 -> ya cambio de fase; sin red -> se reintenta
        }
        const Lectura &l = cola::frente();
        String estado;
        int codigo = api::enviarLectura(token, l, estado);
        char marca[24];
        reloj::iso(l.cuando, marca, sizeof marca);
        if (codigo == 201) {
            registrar("#%u -> HTTP 201 estado=%s (tomada %s)", (unsigned)l.n, estado.c_str(), marca);
            ultimoEstado = estado;
            if (api::rangos().hay) sensoresSimulados.adoptarRangos(api::rangos().minimo, api::rangos().maximo);
            pantalla::mordisco(millis());
            punto = true; apagarPunto = millis() + 300;
            cola::sacar();
            enviados++;
        } else if (codigo == 401) {
            registrar("#%u -> HTTP 401 (token descartado, se pedira otro)", (unsigned)l.n);
            token = ""; identidad::borrarToken();
            if (++rechazosDeCredencial >= 3) { fatal("Credencial rechazada", "revise el secreto"); return; }
        } else if (codigo == api::SIN_RED || codigo >= 500) {
            registrar("sin red o servidor caido: %u lecturas guardadas en RAM", (unsigned)cola::tamano());
            return;
        } else {
            registrar("#%u -> HTTP %d: el servidor rechazo la lectura, se descarta", (unsigned)l.n, codigo);
            cola::sacar();
        }
    }
}

void faseOperando() {
    uint32_t ahora = millis();
    if (exitoActivo) {                                           // animacion de exito, sin bloquear
        if (pantalla::animarExito(ahora)) return;
        exitoActivo = false;
        if (hayUltima) pintarOperando();
    }
    pantalla::animarLecturas(ahora);
    if (punto && ahora > apagarPunto) { punto = false; if (red::conectada() && hayUltima) pintarOperando(); }

    if (!red::conectada()) {
        if (!sinRedMostrada) { sinRedMostrada = true; registrar("Wi-Fi perdido"); }
        if (ahora >= proximoIntento) { proximoIntento = ahora + config::ESPERA_REINTENTO_MS; red::reconectar(); }
    } else if (sinRedMostrada) {
        sinRedMostrada = false;
        registrar("red de vuelta: se entregan %u pendientes", (unsigned)cola::tamano());
    }
    if (ahora < proximaLectura) return;
    proximaLectura = ahora + config::INTERVALO_LECTURA_MS;
    tomarLectura();
    vaciarCola();
    if (fase != Fase::Operando) return;
    if (!red::conectada() || cola::tamano() > 1) { char b[8]; (void)b; pantalla::sinRed(cola::tamano()); }
    else pintarOperando();
}
}

void setup() {
    Serial.begin(115200);
    delay(300);
    Serial.println();
    registrar("firmware de la matera Fito (ESP32), 115200 baudios");
    boton::iniciar();
    pantalla::iniciar();
    identidad::iniciar();
    autotestHmac();
    pantalla::arranque(identidad::serial());
    red::iniciar();
    entrar(Fase::Red);
    delay(1200);
}

void loop() {
    atenderBoton();
    if (segundosBoton == -1) {
        if (pantalla::reintentarSiFalta(millis())) redibujar();   // OLED con contacto intermitente: reaparece y se repinta
        switch (fase) {
        case Fase::Arranque: entrar(Fase::Red); break;
        case Fase::Red: faseRed(); break;
        case Fase::Hora: faseHora(); break;
        case Fase::Registro: faseRegistro(); break;
        case Fase::Credencial: faseCredencial(); break;
        case Fase::Reclamo: faseReclamo(); break;
        case Fase::Operando: faseOperando(); break;
        case Fase::Fatal: break;
        }
    }
    delay(10);
}
