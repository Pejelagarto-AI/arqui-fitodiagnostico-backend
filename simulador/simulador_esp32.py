#!/usr/bin/env python3
"""Simulador del ESP32 de la matera (solo biblioteca estandar de Python 3).

Hace lo que hara el chip: arranca con un SERIAL y un SECRETO de fabrica (los del sticker), consigue solo su
credencial demostrando que conoce el secreto (reto HMAC) y envia lecturas periodicas a
POST /api/v1/dispositivos/{serial}/lecturas. Nadie le copia ningun token.

Autenticacion (fase 3):
  1. POST /dispositivos/{serial}/reto            -> {reto, expira}
  2. POST /dispositivos/{serial}/credencial      {respuesta: base64(HMAC-SHA256(secreto, reto))}
       200 {token, plantaId, expira}   se guarda en la "NVS" (un JSON) y empieza a reportar
       409 DISPOSITIVO_SIN_RECLAMAR    nadie lo reclamo todavia: espera 5 s y reintenta
  El token se renueva solo cuando faltan menos de 2 minutos para que expire, o al primer 401 de una lectura.
  Si el dueno desvincula el dispositivo, la siguiente renovacion da 409 y el simulador vuelve a esperar.

Genera lecturas realistas alrededor del rango optimo de una especie (con una deriva lenta, como un
sustrato que se seca), de vez en cuando una lectura fuera del rango optimo
(la planta pasa a EN_RIESGO o CRITICO) y de vez en cuando datos invalidos
(campo ausente, valor no numerico, valor fisicamente imposible, fecha mal
formada) para comprobar que la API responde 400 con su cuerpo uniforme y no
se cae. Cada respuesta se registra en la salida.

Configuracion (argumento de linea de comandos, o variable de entorno):

  --url           FITO_URL                 base de la API          (http://localhost:8080)
  --serial        FITO_SERIAL              serial de fabrica       (obligatorio)
  --secreto       FITO_SECRETO             secreto de fabrica      (obligatorio)
  --nvs           FITO_NVS                 JSON que hace de memoria no volatil
                                                                   (simulador/.nvs-<serial>.json)
  --intervalo     FITO_INTERVALO           segundos entre lecturas (5)
  --especie       FITO_ESPECIE             especie de la planta    (potos)
  --lecturas      FITO_LECTURAS            cuantas enviar, 0 = sin fin (0)
  --max-pendientes FITO_MAX_PENDIENTES     tope de la cola sin red en la NVS (500)
  --espera-red    FITO_ESPERA_RED          segundos entre pruebas de red mientras no hay (10)
  --simular-caida FITO_SIMULAR_CAIDA       a los N s del arranque finge que no hay red (sin simular)
  --duracion-caida FITO_DURACION_CAIDA     cuantos segundos dura la caida simulada (20)

Sin red (fase 3): si un POST falla por red o por 5xx, la lectura se guarda en la NVS (clave "pendientes") con su
marcaDeTiempo original y se entrega en orden cuando vuelve la red, antes de seguir en vivo (ver D82).

Ejemplo (el serial y el secreto los imprime simulador/fabrica.py):
  FITO_SERIAL=fito-7K2M9Q FITO_SECRETO=... python3 simulador/simulador_esp32.py --intervalo 2
"""

import argparse
import base64
import hashlib
import hmac
import json
import os
import random
import re
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

# Rangos optimos del Anexo B (src/main/resources/referencia/especies.csv):
# especie -> (humedad_min, humedad_max, luz_min, luz_max, temp_min, temp_max)
ESPECIES = {
    "sansevieria": (20, 45, 200, 1500, 15, 29),
    "potos": (40, 70, 300, 1200, 18, 30),
    "suculenta": (10, 30, 800, 2500, 15, 32),
    "helecho": (60, 85, 150, 800, 16, 26),
    "lavanda": (25, 50, 1000, 3000, 15, 30),
}

# Limites fisicos del sensor (los mismos que valida el dominio).
LIMITES_FISICOS = {"humedad": (0.0, 100.0), "luz": (0.0, 150000.0), "temperatura": (-20.0, 60.0)}

CAMPOS = ("humedad", "luz", "temperatura")

ESPERA_RECLAMO_S = 5                       # cada cuanto reintenta mientras nadie lo reclama
MARGEN_DE_RENOVACION = timedelta(minutes=2)  # renueva cuando faltan menos de 2 minutos
ESPERA_RED_S = 10                          # cada cuanto prueba si volvio la red
MAX_PENDIENTES = 500                       # como una memoria flash chica
DURACION_CAIDA_S = 20
SIN_RED = object()                         # lo que devuelve Credencial.asegurar(esperar_red=False) sin red
INTENTOS_CON_RESPUESTA_RECHAZADA = 3       # 401 seguidos antes de rendirse (casi seguro el secreto esta mal)


def ahora_iso():
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


class Sensor:
    """Genera lecturas que derivan lentamente alrededor del centro del rango optimo."""

    def __init__(self, especie, azar):
        h0, h1, l0, l1, t0, t1 = ESPECIES[especie]
        self.azar = azar
        self.rangos = {"humedad": (h0, h1), "luz": (l0, l1), "temperatura": (t0, t1)}
        self.actual = {c: (lo + hi) / 2 for c, (lo, hi) in self.rangos.items()}

    def normal(self):
        """Una lectura dentro (o muy cerca) del rango optimo: camina al azar sin salirse mucho."""
        lectura = {}
        for campo, (lo, hi) in self.rangos.items():
            ancho = hi - lo
            valor = self.actual[campo] + self.azar.gauss(0, ancho * 0.05)
            valor = min(max(valor, lo + ancho * 0.05), hi - ancho * 0.05)   # vuelve hacia el interior
            self.actual[campo] = valor
            lectura[campo] = round(valor, 1)
        return lectura

    def fuera_de_rango(self):
        """Una lectura valida fisicamente pero fuera del optimo en una magnitud (la planta se resiente)."""
        lectura = self.normal()
        campo = self.azar.choice(CAMPOS)
        lo, hi = self.rangos[campo]
        ancho = hi - lo
        piso, techo = LIMITES_FISICOS[campo]
        if self.azar.random() < 0.5:
            valor = max(piso, lo - ancho * self.azar.uniform(0.2, 0.9))
        else:
            valor = min(techo, hi + ancho * self.azar.uniform(0.2, 0.9))
        lectura[campo] = round(valor, 1)
        return lectura, campo


def cuerpo_invalido(lectura, azar):
    """Devuelve (descripcion, cuerpo_json_como_texto) con un defecto elegido al azar."""
    tipo = azar.choice(["campo ausente", "valor no numerico", "valor fisicamente imposible", "fecha mal formada",
                        "json roto"])
    cuerpo = dict(lectura, marcaDeTiempo=ahora_iso())
    campo = azar.choice(CAMPOS)
    if tipo == "campo ausente":
        del cuerpo[campo]
    elif tipo == "valor no numerico":
        cuerpo[campo] = azar.choice(["abc", "NaN?", "--"])
    elif tipo == "valor fisicamente imposible":
        piso, techo = LIMITES_FISICOS[campo]
        cuerpo[campo] = azar.choice([piso - 50.0, techo + 50.0])
    elif tipo == "fecha mal formada":
        cuerpo["marcaDeTiempo"] = "ayer por la tarde"
    else:
        return "json roto (" + campo + ")", '{"humedad": 55, "luz": '
    return tipo + " (" + campo + ")", json.dumps(cuerpo)


class CaidaSimulada:
    """Finge que no hay red entre inicio_s y inicio_s + duracion_s segundos despues de crearse (reloj monotonico)."""

    def __init__(self, inicio_s, duracion_s, reloj=time.monotonic):
        self.reloj, self.inicio_s, self.duracion_s = reloj, inicio_s, duracion_s
        self.t0 = reloj()
        self.anunciada = False

    def activa(self):
        t = self.reloj() - self.t0
        return self.inicio_s <= t < self.inicio_s + self.duracion_s

    def anunciar(self):
        """Imprime cuando entra y cuando sale de la caida."""
        activa = self.activa()
        if activa and not self.anunciada:
            print("[%s] --- CAIDA SIMULADA: entra, sin red por %g s ---" % (ahora_iso(), self.duracion_s), flush=True)
            self.anunciada = True
        elif not activa and self.anunciada and self.reloj() - self.t0 >= self.inicio_s + self.duracion_s:
            print("[%s] --- CAIDA SIMULADA: termina, la red vuelve ---" % ahora_iso(), flush=True)
            self.anunciada = None   # ya salio, no se repite


CAIDA = None   # CaidaSimulada o None: si esta activa, enviar() falla como si no hubiera red


def enviar(url, token, cuerpo_texto):
    """POST del cuerpo ya serializado; devuelve (codigo_http, texto_de_respuesta) sin lanzar por 4xx/5xx.
    Sin token no manda Authorization: asi pide el chip su reto y su credencial."""
    if CAIDA is not None and CAIDA.activa():
        raise urllib.error.URLError("caida simulada")
    cabeceras = {"Content-Type": "application/json"}
    if token:
        cabeceras["Authorization"] = "Bearer " + token
    peticion = urllib.request.Request(url, data=cuerpo_texto.encode("utf-8"), method="POST", headers=cabeceras)
    try:
        with urllib.request.urlopen(peticion, timeout=10) as respuesta:
            return respuesta.status, respuesta.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", errors="replace")


def resumen(codigo, texto):
    """Una linea legible de la respuesta: estado del diagnostico si fue 201, codigo de error si no."""
    try:
        datos = json.loads(texto)
    except ValueError:
        return texto[:120]
    if codigo == 201:
        return "estado=%s id=%s" % (datos["diagnostico"]["estado"], datos["id"][:8])
    return "%s: %s" % (datos.get("error", "?"), datos.get("mensaje", ""))


# ---- "NVS": la memoria no volatil del chip, aqui un JSON ----

def leer_nvs(ruta, serial):
    """Devuelve {token, expira} guardado, o {} si no hay nada, esta roto o es de otro serial."""
    try:
        with open(ruta, encoding="utf-8") as archivo:
            datos = json.load(archivo)
    except (OSError, ValueError):
        return {}
    if not isinstance(datos, dict) or datos.get("serial") != serial:
        return {}
    return datos


def _volcar(ruta, datos):
    """Escritura atomica (temporal + rename): un corte a medias no deja la NVS rota."""
    temporal = ruta + ".tmp"
    with open(temporal, "w", encoding="utf-8") as archivo:
        json.dump(datos, archivo)
    os.replace(temporal, ruta)


def escribir_nvs(ruta, serial, token, expira):
    """Guarda el token sin tocar la cola de pendientes."""
    datos = {"serial": serial, "token": token, "expira": expira,
             "pendientes": leer_nvs(ruta, serial).get("pendientes", [])}
    _volcar(ruta, datos)


def leer_pendientes(ruta, serial):
    """La cola guardada, ordenada por marcaDeTiempo (orden estable)."""
    lista = leer_nvs(ruta, serial).get("pendientes", [])
    if not isinstance(lista, list):
        return []
    return sorted((p for p in lista if isinstance(p, dict)), key=lambda p: str(p.get("marcaDeTiempo", "")))


def guardar_pendientes(ruta, serial, pendientes):
    """Guarda la cola sin tocar el token."""
    datos = leer_nvs(ruta, serial)
    datos["serial"] = serial
    datos["pendientes"] = list(pendientes)
    _volcar(ruta, datos)


def borrar_nvs(ruta):
    try:
        os.remove(ruta)
    except OSError:
        pass


def instante(texto):
    """Lee un instante ISO-8601 de la API (hasta nanosegundos, con Z) en cualquier version de Python 3."""
    m = re.fullmatch(r"(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:\d{2})", texto)
    if not m:
        raise ValueError("instante ilegible: " + texto)
    base, fraccion, zona = m.groups()
    micros = (fraccion or "0")[:6].ljust(6, "0")
    return datetime.fromisoformat("%s.%s%s" % (base, micros, "+00:00" if zona == "Z" else zona))


# ---- autenticacion por reto HMAC ----

def responder_al_reto(secreto, reto):
    """base64( HMAC-SHA256( clave = secreto como texto, mensaje = reto como texto ) ): lo que hace el chip."""
    firma = hmac.new(secreto.encode("utf-8"), reto.encode("utf-8"), hashlib.sha256).digest()
    return base64.b64encode(firma).decode("ascii")


def pedir_credencial(base, serial, secreto):
    """Un intento completo: reto + credencial. Devuelve (codigo, datos): 200 con {token, plantaId, expira}, o el
    error de la API ({error, mensaje, detalle}). Lanza OSError/URLError si no hay red."""
    ruta = base + "/api/v1/dispositivos/" + serial
    codigo, texto = enviar(ruta + "/reto", None, "")
    datos = cargar_json(texto)
    if codigo != 200:
        return codigo, datos
    cuerpo = json.dumps({"respuesta": responder_al_reto(secreto, datos["reto"])})
    codigo, texto = enviar(ruta + "/credencial", None, cuerpo)
    return codigo, cargar_json(texto)


def cargar_json(texto):
    try:
        datos = json.loads(texto)
    except ValueError:
        return {}
    return datos if isinstance(datos, dict) else {}


class Credencial:
    """El token del chip: lo trae de la NVS, lo pide cuando falta o esta por vencer y espera si nadie lo reclamo."""

    def __init__(self, base, serial, secreto, ruta_nvs, espera=ESPERA_RECLAMO_S, dormir=time.sleep, ahora=None):
        self.base, self.serial, self.secreto, self.ruta_nvs = base, serial, secreto, ruta_nvs
        self.espera, self.dormir = espera, dormir
        self.ahora = ahora or (lambda: datetime.now(timezone.utc))
        guardado = leer_nvs(ruta_nvs, serial)
        self.token = guardado.get("token")
        self.expira = None
        if self.token:
            try:
                self.expira = instante(guardado["expira"])
            except (KeyError, ValueError):
                self.token = None

    def vigente(self):
        return bool(self.token) and self.expira - self.ahora() > MARGEN_DE_RENOVACION

    def descartar(self):
        """Un 401 en una lectura: el token ya no sirve. Se olvida (tambien de la NVS) y se vuelve al reto."""
        self.token, self.expira = None, None
        escribir_nvs(self.ruta_nvs, self.serial, None, None)   # la cola de pendientes se conserva

    def asegurar(self, esperar_red=True):
        """Devuelve un token vigente; si no hay, lo consigue (y espera el reclamo todo lo que haga falta).
        Devuelve None si el servidor rechaza al dispositivo de forma que esperar no arregla (serial inexistente
        o secreto equivocado), tras avisar por que. Con esperar_red=False, si no hay red devuelve SIN_RED en vez de
        reintentar sin fin (asi el chip sigue midiendo y encolando)."""
        if self.vigente():
            return self.token
        rechazos = 0
        while True:
            try:
                codigo, datos = pedir_credencial(self.base, self.serial, self.secreto)
            except (urllib.error.URLError, OSError) as error:
                if not esperar_red:
                    return SIN_RED
                print("[%s] sin respuesta del servidor al pedir credencial: %s (reintento en %g s)"
                      % (ahora_iso(), error, self.espera), flush=True)
                self.dormir(self.espera)
                continue
            if codigo == 200:
                self.token, self.expira = datos["token"], instante(datos["expira"])
                escribir_nvs(self.ruta_nvs, self.serial, self.token, datos["expira"])
                print("[%s] credencial obtenida para la planta %s (vence %s)"
                      % (ahora_iso(), datos.get("plantaId", "?")[:8], datos["expira"]), flush=True)
                return self.token
            if codigo == 409:
                self.token, self.expira = None, None
                print("[%s] esperando reclamo del serial %s... (reintento en %g s)"
                      % (ahora_iso(), self.serial, self.espera), flush=True)
                self.dormir(self.espera)
                continue
            if codigo == 401:
                rechazos += 1
                print("[%s] credencial rechazada (%d/%d): %s" % (ahora_iso(), rechazos,
                      INTENTOS_CON_RESPUESTA_RECHAZADA, datos.get("mensaje", "")), flush=True)
                if rechazos >= INTENTOS_CON_RESPUESTA_RECHAZADA:
                    print("el secreto no corresponde al serial %s: revise --secreto" % self.serial, file=sys.stderr)
                    return None
                self.dormir(self.espera)
                continue
            print("el servidor respondio HTTP %d %s: %s" % (codigo, datos.get("error", "?"), datos.get("mensaje", "")),
                  file=sys.stderr)
            return None


def construir_parser():
    env = os.environ.get
    p = argparse.ArgumentParser(description="Simulador del ESP32 de la matera.")
    p.add_argument("--url", default=env("FITO_URL", "http://localhost:8080"))
    p.add_argument("--serial", default=env("FITO_SERIAL"), help="serial de fabrica (el del sticker)")
    p.add_argument("--secreto", default=env("FITO_SECRETO"), help="secreto de fabrica (el del sticker)")
    p.add_argument("--nvs", default=env("FITO_NVS"),
                   help="JSON que hace de memoria no volatil (por defecto simulador/.nvs-<serial>.json)")
    p.add_argument("--espera-reclamo", type=float, default=float(env("FITO_ESPERA_RECLAMO", str(ESPERA_RECLAMO_S))),
                   help="segundos entre reintentos mientras nadie lo reclama")
    p.add_argument("--intervalo", type=float, default=float(env("FITO_INTERVALO", "5")))
    p.add_argument("--especie", default=env("FITO_ESPECIE", "potos"), choices=sorted(ESPECIES))
    p.add_argument("--lecturas", type=int, default=int(env("FITO_LECTURAS", "0")),
                   help="cuantas lecturas enviar; 0 = hasta Ctrl+C")
    p.add_argument("--max-pendientes", type=int, default=int(env("FITO_MAX_PENDIENTES", str(MAX_PENDIENTES))),
                   help="tope de lecturas guardadas sin red; al llenarse se descarta la mas vieja")
    p.add_argument("--espera-red", type=float, default=float(env("FITO_ESPERA_RED", str(ESPERA_RED_S))),
                   help="segundos entre pruebas de red mientras no hay")
    p.add_argument("--simular-caida", type=float, default=float(env("FITO_SIMULAR_CAIDA", "-1")),
                   help="a los N segundos del arranque finge que no hay red (sin tumbar el backend)")
    p.add_argument("--duracion-caida", type=float, default=float(env("FITO_DURACION_CAIDA", str(DURACION_CAIDA_S))),
                   help="cuantos segundos dura la caida simulada")
    p.add_argument("--prob-fuera-de-rango", type=float, default=0.15,
                   help="probabilidad de una lectura valida pero fuera del optimo (0 a 1)")
    p.add_argument("--prob-invalida", type=float, default=0.10,
                   help="probabilidad de un dato invalido (0 a 1)")
    p.add_argument("--semilla", type=int, default=None, help="semilla del azar, para repetir una corrida")
    return p


class Cola:
    """La cola de lecturas sin entregar: en memoria y, cuando hace falta, en la NVS (clave "pendientes")."""

    def __init__(self, ruta, serial, tope):
        self.ruta, self.serial, self.tope = ruta, serial, tope
        self.items = leer_pendientes(ruta, serial)
        for it in self.items:
            it["tarde"] = True
        self.persistida = bool(self.items)

    def empujar(self, item):
        """Agrega al final (las marcas crecen, asi que sigue ordenada). Si se pasa del tope, descarta la mas vieja."""
        self.items.append(item)
        while len(self.items) > self.tope:
            viejo = self.items.pop(0)
            print("[%s] NVS llena (%d): se descarta la lectura mas vieja (#%s, tomada %s)"
                  % (ahora_iso(), self.tope, viejo.get("n", "?"), viejo.get("marcaDeTiempo", "?")), flush=True)

    def persistir(self):
        if self.items or self.persistida:
            guardar_pendientes(self.ruta, self.serial, [limpio(i) for i in self.items])
            self.persistida = bool(self.items)


def limpio(item):
    return {k: v for k, v in item.items() if k != "tarde"}


def cuerpo_de(item):
    return item["crudo"] if "crudo" in item else json.dumps(
        {"marcaDeTiempo": item["marcaDeTiempo"], "humedad": item["humedad"], "luz": item["luz"],
         "temperatura": item["temperatura"]})


def vaciar(cola, credencial, url, viejas, titulo):
    """Envia la cola en orden. Devuelve True si quedo vacia, False si se corto (sin red o 5xx: se reintenta mas
    tarde), o None si no hay forma de autenticarse (error fatal ya avisado). `viejas` = cuantas lecturas anteriores
    a la recien medida hay; si son mas de cero se anuncia `titulo` con la primera respuesta del servidor."""
    anunciado = viejas == 0
    rechazos = 0
    while cola.items:
        if not credencial.vigente():
            cola.persistir()   # puede quedarse esperando el reclamo: que la cola ya este a salvo
        token = credencial.asegurar(esperar_red=False)
        if token is SIN_RED:
            return False
        if token is None:
            return None
        item = cola.items[0]
        try:
            codigo, texto = enviar(url, token, cuerpo_de(item))
        except (urllib.error.URLError, OSError):
            return False
        if codigo >= 500:
            return False
        if not anunciado:
            print("[%s] %s: enviando %d pendientes..." % (ahora_iso(), titulo, viejas), flush=True)
            anunciado = True
        salida = "HTTP %d %s" % (codigo, resumen(codigo, texto))
        if codigo == 401:
            credencial.descartar()
            rechazos += 1
            print("[%s] #%s %s -> %s (token descartado, se pedira otro)"
                  % (ahora_iso(), item.get("n", "?"), item.get("tipo", ""), salida), flush=True)
            cola.persistir()
            if rechazos >= INTENTOS_CON_RESPUESTA_RECHAZADA:
                return False
            continue
        rechazos = 0
        cola.items.pop(0)
        cola.persistir()
        extra = " (pendiente, tomada %s)" % item["marcaDeTiempo"] if item.get("tarde") else ""
        print("[%s] #%s %-8s %-38s -> %s%s" % (ahora_iso(), item.get("n", "?"), item.get("tipo", ""),
              item.get("detalle", ""), salida, extra), flush=True)
    return True


def main(argv=None):
    global CAIDA
    args = construir_parser().parse_args(argv)
    if not args.serial or not args.secreto:
        print("faltan el serial y el secreto de fabrica: use --serial y --secreto (o FITO_SERIAL y FITO_SECRETO); "
              "los imprime simulador/fabrica.py", file=sys.stderr)
        return 2
    if args.intervalo < 0:
        print("--intervalo no puede ser negativo", file=sys.stderr)
        return 2
    if args.max_pendientes < 1:
        print("--max-pendientes debe ser al menos 1", file=sys.stderr)
        return 2

    azar = random.Random(args.semilla)
    sensor = Sensor(args.especie, azar)
    base = args.url.rstrip("/")
    url = base + "/api/v1/dispositivos/" + args.serial + "/lecturas"
    ruta_nvs = args.nvs or os.path.join(os.path.dirname(os.path.abspath(__file__)), ".nvs-%s.json" % args.serial)
    credencial = Credencial(base, args.serial, args.secreto, ruta_nvs, espera=args.espera_reclamo)
    cola = Cola(ruta_nvs, args.serial, args.max_pendientes)
    if args.simular_caida >= 0:
        CAIDA = CaidaSimulada(args.simular_caida, args.duracion_caida)
    print("simulador %s -> %s (especie %s, cada %.1f s, NVS %s)"
          % (args.serial, url, args.especie, args.intervalo, ruta_nvs), flush=True)

    sin_red = False
    proxima_prueba = 0.0     # reloj monotonico: cuando toca volver a probar la red
    enviadas = 0
    try:
        if cola.items:
            print("[%s] la NVS tiene %d pendientes de una ejecucion anterior: se envian primero"
                  % (ahora_iso(), len(cola.items)), flush=True)
            if CAIDA:
                CAIDA.anunciar()
            ok = vaciar(cola, credencial, url, len(cola.items), "pendientes de la ejecucion anterior")
            if ok is None:
                return 1
            sin_red = ok is False
            if sin_red:
                proxima_prueba = time.monotonic() + args.espera_red
                print("[%s] sin red: %d pendientes siguen en NVS" % (ahora_iso(), len(cola.items)), flush=True)

        while args.lecturas == 0 or enviadas < args.lecturas:
            if CAIDA:
                CAIDA.anunciar()
            sorteo = azar.random()
            if sorteo < args.prob_invalida:
                tipo = "INVALIDA"
                detalle, cuerpo = cuerpo_invalido(sensor.normal(), azar)
                item = {"marcaDeTiempo": ahora_iso(), "crudo": cuerpo}
            elif sorteo < args.prob_invalida + args.prob_fuera_de_rango:
                lectura, campo = sensor.fuera_de_rango()
                tipo, detalle = "FUERA", "fuera de rango en " + campo
                item = dict(lectura, marcaDeTiempo=ahora_iso())
            else:
                tipo, detalle = "NORMAL", "dentro del optimo"
                item = dict(sensor.normal(), marcaDeTiempo=ahora_iso())
            enviadas += 1
            item.update(n=enviadas, tipo=tipo, detalle=detalle)
            viejas = len(cola.items)
            cola.empujar(item)

            if not sin_red or time.monotonic() >= proxima_prueba:
                ok = vaciar(cola, credencial, url, viejas, "red de vuelta")
                if ok is None:
                    return 1
                if ok:
                    sin_red = False
                else:
                    sin_red = True
                    proxima_prueba = time.monotonic() + args.espera_red
                    cola.persistir()
            if any(i is item for i in cola.items):
                item["tarde"] = True
                cola.persistir()
                print("[%s] sin red: lectura #%d guardada en NVS (%d pendientes)"
                      % (ahora_iso(), enviadas, len(cola.items)), flush=True)
            if args.lecturas == 0 or enviadas < args.lecturas:
                time.sleep(args.intervalo)
        if cola.items:
            print("[%s] termino con %d pendientes en NVS (se envian en la proxima ejecucion)"
                  % (ahora_iso(), len(cola.items)), flush=True)
    except KeyboardInterrupt:
        print("\ninterrumpido por el usuario")
    finally:
        if cola.items:
            cola.persistir()
    return 0


if __name__ == "__main__":
    sys.exit(main())
