#!/usr/bin/env python3
"""Simulador del ESP32 de la matera (solo biblioteca estandar de Python 3).

Envia lecturas periodicas a POST /api/v1/dispositivos/{id}/lecturas con el token
de dispositivo que emite el dueno de la planta. Genera lecturas realistas
alrededor del rango optimo de una especie (con una deriva lenta, como un
sustrato que se seca), de vez en cuando una lectura fuera del rango optimo
(la planta pasa a EN_RIESGO o CRITICO) y de vez en cuando datos invalidos
(campo ausente, valor no numerico, valor fisicamente imposible, fecha mal
formada) para comprobar que la API responde 400 con su cuerpo uniforme y no
se cae. Cada respuesta se registra en la salida.

Configuracion (argumento de linea de comandos, o variable de entorno):

  --url           FITO_URL                 base de la API         (http://localhost:8080)
  --dispositivo   FITO_DISPOSITIVO_ID      id del dispositivo     (esp32-01)
  --token         FITO_TOKEN_DISPOSITIVO   JWT del dispositivo    (obligatorio)
  --intervalo     FITO_INTERVALO           segundos entre lecturas (5)
  --especie       FITO_ESPECIE             especie de la planta   (potos)
  --lecturas      FITO_LECTURAS            cuantas enviar, 0 = sin fin (0)

Ejemplo:
  export FITO_TOKEN_DISPOSITIVO="eyJ..."          # lo entrega POST /api/v1/plantas/{id}/dispositivos
  python3 simulador/simulador_esp32.py --intervalo 2 --prob-invalida 0.2
"""

import argparse
import json
import os
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

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
    """POST del cuerpo ya serializado; devuelve (codigo_http, texto_de_respuesta) sin lanzar por 4xx/5xx."""
    peticion = urllib.request.Request(
        url, data=cuerpo_texto.encode("utf-8"), method="POST",
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + token})
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


def construir_parser():
    env = os.environ.get
    p = argparse.ArgumentParser(description="Simulador del ESP32 de la matera.")
    p.add_argument("--url", default=env("FITO_URL", "http://localhost:8080"))
    p.add_argument("--dispositivo", default=env("FITO_DISPOSITIVO_ID", "esp32-01"))
    p.add_argument("--token", default=env("FITO_TOKEN_DISPOSITIVO"))
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
    if not args.token:
        print("falta el token del dispositivo: use --token o la variable FITO_TOKEN_DISPOSITIVO", file=sys.stderr)
        return 2
    if args.intervalo < 0:
        print("--intervalo no puede ser negativo", file=sys.stderr)
        return 2

    azar = random.Random(args.semilla)
    sensor = Sensor(args.especie, azar)
    url = args.url.rstrip("/") + "/api/v1/dispositivos/" + args.dispositivo + "/lecturas"
    print("simulador %s -> %s (especie %s, cada %.1f s)" % (args.dispositivo, url, args.especie, args.intervalo))

    enviadas = 0
    try:
        while args.lecturas == 0 or enviadas < args.lecturas:
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
                codigo, texto = enviar(url, args.token, cuerpo)
                salida = "HTTP %d %s" % (codigo, resumen(codigo, texto))
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
