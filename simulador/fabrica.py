#!/usr/bin/env python3
"""La "fabrica": da de alta un dispositivo nuevo y pinta su sticker (solo biblioteca estandar de Python 3).

Inicia sesion como administrador, llama a POST /api/v1/admin/dispositivos y muestra el serial y el secreto
(el secreto se entrega UNA sola vez: no hay forma de volver a verlo) junto con el comando exacto para arrancar
el simulador con esos valores. En el chip real, el serial se imprime en la matera y el secreto se graba en su
memoria; el dueno solo escribe el serial en la app.

Configuracion (argumento de linea de comandos, o variable de entorno):

  --url          FITO_URL                  base de la API            (http://localhost:8080)
  --serial       FITO_SERIAL               serial a registrar        (opcional: si falta lo genera el servidor)
                 FITO_ADMIN_CORREO         correo del administrador  (obligatorio)
                 FITO_ADMIN_CONTRASENA     contrasena                (obligatoria)

Ejemplo:
  FITO_ADMIN_CORREO=admin@fito.local FITO_ADMIN_CONTRASENA=... python3 simulador/fabrica.py
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request


def pedir(url, cuerpo, token=None):
    """POST JSON; devuelve (codigo, dict) sin lanzar por 4xx/5xx."""
    cabeceras = {"Content-Type": "application/json"}
    if token:
        cabeceras["Authorization"] = "Bearer " + token
    peticion = urllib.request.Request(url, data=json.dumps(cuerpo).encode("utf-8"), method="POST", headers=cabeceras)
    try:
        with urllib.request.urlopen(peticion, timeout=10) as respuesta:
            codigo, texto = respuesta.status, respuesta.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        codigo, texto = error.code, error.read().decode("utf-8", errors="replace")
    try:
        datos = json.loads(texto)
    except ValueError:
        datos = {}
    return codigo, datos if isinstance(datos, dict) else {}


def sticker(serial, secreto, url):
    """El texto para pegar en la matera. Devuelve una lista de lineas."""
    ruta = "python3 simulador/simulador_esp32.py"
    comando = "FITO_SERIAL=%s FITO_SECRETO=%s %s" % (serial, secreto, ruta)
    if url != "http://localhost:8080":
        comando += " --url " + url
    contenido = [
        "DISPOSITIVO NUEVO",
        "",
        "serial   %s      <- se imprime en la matera; el dueno lo escribe en la app" % serial,
        "secreto  %s      <- se graba en el chip; se muestra UNA vez" % secreto,
        "",
        "arrancar el simulador con estos valores:",
        "  " + comando,
    ]
    ancho = max(len(linea) for linea in contenido) + 2
    borde = "+" + "-" * ancho + "+"
    return [borde] + ["| " + linea.ljust(ancho - 1) + "|" for linea in contenido] + [borde]


def main(argv=None):
    env = os.environ.get
    p = argparse.ArgumentParser(description="Da de alta un dispositivo de fabrica y pinta su sticker.")
    p.add_argument("--url", default=env("FITO_URL", "http://localhost:8080"))
    p.add_argument("--serial", default=env("FITO_SERIAL"), help="serial a registrar (por defecto lo genera el servidor)")
    args = p.parse_args(argv)
    correo, contrasena = env("FITO_ADMIN_CORREO"), env("FITO_ADMIN_CONTRASENA")
    if not correo or not contrasena:
        print("faltan FITO_ADMIN_CORREO y FITO_ADMIN_CONTRASENA (las credenciales del administrador)", file=sys.stderr)
        return 2

    base = args.url.rstrip("/") + "/api/v1"
    try:
        codigo, sesion = pedir(base + "/auth/sesion", {"correo": correo, "contrasena": contrasena})
        if codigo != 200:
            print("no se pudo iniciar sesion (HTTP %d %s): %s" % (codigo, sesion.get("error", "?"),
                                                                  sesion.get("mensaje", "")), file=sys.stderr)
            return 1
        cuerpo = {"serial": args.serial} if args.serial else {}
        codigo, datos = pedir(base + "/admin/dispositivos", cuerpo, sesion["token"])
    except (urllib.error.URLError, OSError) as error:
        print("sin respuesta del servidor en %s: %s" % (args.url, error), file=sys.stderr)
        return 1
    if codigo != 201:
        print("no se pudo registrar el dispositivo (HTTP %d %s): %s" % (codigo, datos.get("error", "?"),
                                                                        datos.get("mensaje", "")), file=sys.stderr)
        return 1

    print("\n".join(sticker(datos["serial"], datos["secreto"], args.url.rstrip("/"))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
