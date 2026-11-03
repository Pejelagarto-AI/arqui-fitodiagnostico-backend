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


def enviar(url, token, cuerpo_texto):
    """POST del cuerpo ya serializado; devuelve (codigo_http, texto_de_respuesta) sin lanzar por 4xx/5xx.
    Sin token no manda Authorization: asi pide el chip su reto y su credencial."""
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


def escribir_nvs(ruta, serial, token, expira):
    datos = {"serial": serial, "token": token, "expira": expira}
    with open(ruta, "w", encoding="utf-8") as archivo:
        json.dump(datos, archivo)


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
        borrar_nvs(self.ruta_nvs)

    def asegurar(self):
        """Devuelve un token vigente; si no hay, lo consigue (y espera el reclamo todo lo que haga falta).
        Devuelve None si el servidor rechaza al dispositivo de forma que esperar no arregla (serial inexistente
        o secreto equivocado), tras avisar por que."""
        if self.vigente():
            return self.token
        rechazos = 0
        while True:
            try:
                codigo, datos = pedir_credencial(self.base, self.serial, self.secreto)
            except (urllib.error.URLError, OSError) as error:
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
    p.add_argument("--prob-fuera-de-rango", type=float, default=0.15,
                   help="probabilidad de una lectura valida pero fuera del optimo (0 a 1)")
    p.add_argument("--prob-invalida", type=float, default=0.10,
                   help="probabilidad de un dato invalido (0 a 1)")
    p.add_argument("--semilla", type=int, default=None, help="semilla del azar, para repetir una corrida")
    return p


def main(argv=None):
    args = construir_parser().parse_args(argv)
    if not args.serial or not args.secreto:
        print("faltan el serial y el secreto de fabrica: use --serial y --secreto (o FITO_SERIAL y FITO_SECRETO); "
              "los imprime simulador/fabrica.py", file=sys.stderr)
        return 2
    if args.intervalo < 0:
        print("--intervalo no puede ser negativo", file=sys.stderr)
        return 2

    azar = random.Random(args.semilla)
    sensor = Sensor(args.especie, azar)
    base = args.url.rstrip("/")
    url = base + "/api/v1/dispositivos/" + args.serial + "/lecturas"
    ruta_nvs = args.nvs or os.path.join(os.path.dirname(os.path.abspath(__file__)), ".nvs-%s.json" % args.serial)
    credencial = Credencial(base, args.serial, args.secreto, ruta_nvs, espera=args.espera_reclamo)
    print("simulador %s -> %s (especie %s, cada %.1f s, NVS %s)"
          % (args.serial, url, args.especie, args.intervalo, ruta_nvs), flush=True)

    enviadas = 0
    try:
        while args.lecturas == 0 or enviadas < args.lecturas:
            token = credencial.asegurar()   # renueva sola si falta menos de 2 minutos o si no hay token
            if token is None:
                return 1

            sorteo = azar.random()
            if sorteo < args.prob_invalida:
                tipo = "INVALIDA"
                detalle, cuerpo = cuerpo_invalido(sensor.normal(), azar)
            elif sorteo < args.prob_invalida + args.prob_fuera_de_rango:
                lectura, campo = sensor.fuera_de_rango()
                tipo, detalle = "FUERA", "fuera de rango en " + campo
                cuerpo = json.dumps(dict(lectura, marcaDeTiempo=ahora_iso()))
            else:
                tipo, detalle = "NORMAL", "dentro del optimo"
                cuerpo = json.dumps(dict(sensor.normal(), marcaDeTiempo=ahora_iso()))

            try:
                codigo, texto = enviar(url, token, cuerpo)
                salida = "HTTP %d %s" % (codigo, resumen(codigo, texto))
                if codigo == 401:
                    credencial.descartar()   # el token ya no sirve: la proxima vuelta pide reto y credencial
                    salida += " (token descartado, se pedira otro)"
            except (urllib.error.URLError, OSError) as error:
                salida = "sin respuesta del servidor: %s" % error
            enviadas += 1
            print("[%s] #%d %-8s %-38s -> %s" % (ahora_iso(), enviadas, tipo, detalle, salida), flush=True)
            if args.lecturas == 0 or enviadas < args.lecturas:
                time.sleep(args.intervalo)
    except KeyboardInterrupt:
        print("\ninterrumpido por el usuario")
    return 0


if __name__ == "__main__":
    sys.exit(main())
