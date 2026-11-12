#!/usr/bin/env python3
"""Pruebas del algoritmo del firmware SIN placa.

Compila con gcc el nucleo en C (src/nucleo/fito_nucleo.c) junto con un hash portable (test/hash_portable.c) y
compara su salida con los vectores que produce el simulador de Python (simulador/simulador_esp32.py):
mismo serial desde la MAC, mismo secreto en base64url, misma firma de flota y misma respuesta al reto.
Uso:  python3 firmware/test/probar_nucleo.py
"""
import base64
import hashlib
import hmac
import os
import random
import subprocess
import sys
import tempfile

AQUI = os.path.dirname(os.path.abspath(__file__))
RAIZ = os.path.dirname(os.path.dirname(AQUI))
sys.path.insert(0, os.path.join(RAIZ, "simulador"))
import simulador_esp32 as sim  # noqa: E402

sys.path.insert(0, os.path.join(RAIZ, "firmware"))
from tabla_cal import como_c, leer_tabla  # noqa: E402

CLAVE_FLOTA = "clave-de-flota-de-prueba-0123456789abcdef"
# MAC real de la placa de Santiago (ESP32-D0WD-V3) y otras fijas.
MACS = ["e08cfee64440", "000000000000", "ffffffffffff", "0123456789ab", "a4cf12ff0102"]
SECRETO_BYTES = bytes(range(32))
RETOS = ["dGVzdG8tZGVsLXJldG8=", "AAAAAAAAAAAAAAAAAAAAAA==", "x" * 44]


def compilar(destino):
    cmd = ["gcc", "-std=c99", "-Wall", "-Wextra", "-Werror", "-I", os.path.join(RAIZ, "firmware", "src", "nucleo"),
           os.path.join(AQUI, "arnes.c"), os.path.join(AQUI, "hash_portable.c"),
           os.path.join(RAIZ, "firmware", "src", "nucleo", "fito_nucleo.c"), "-o", destino]
    subprocess.run(cmd, check=True)


def main():
    fallos = 0
    total = 0
    with tempfile.TemporaryDirectory() as tmp:
        arnes = os.path.join(tmp, "arnes")
        compilar(arnes)

        def c(*args):
            return subprocess.run([arnes, *args], check=True, capture_output=True, text=True).stdout.strip()

        def comprobar(nombre, esperado, obtenido):
            nonlocal fallos, total
            total += 1
            if esperado != obtenido:
                fallos += 1
                print("FALLA %s\n  simulador: %s\n  firmware : %s" % (nombre, esperado, obtenido))
            else:
                print("ok    %s = %s" % (nombre, obtenido))

        # Cimientos: el hash y el HMAC portables contra hashlib/hmac y un vector publico (RFC 4231, caso 2).
        comprobar("sha256('abc')", hashlib.sha256(b"abc").hexdigest(), c("sha256", "abc"))
        comprobar("hmac RFC4231 caso 2", "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                  c("hmac", "Jefe", "what do ya want for nothing?"))
        largo = "m" * 200
        comprobar("sha256 de 200 bytes", hashlib.sha256(largo.encode()).hexdigest(), c("sha256", largo))

        # 1. Serial desde la MAC.
        azar = random.Random(7)
        macs = MACS + ["%012x" % azar.getrandbits(48) for _ in range(20)]
        for mac in macs:
            comprobar("serial(%s)" % mac, sim.serial_desde_mac(bytes.fromhex(mac)), c("serial", mac))
        comprobar("serial de la placa de Santiago", "fito-YUBUD6", c("serial", "e08cfee64440"))

        # 2. Secreto en base64url sin relleno.
        for bytes_ in (SECRETO_BYTES, bytes(32), b"\xff" * 32, bytes(azar.getrandbits(8) for _ in range(32))):
            esperado = base64.urlsafe_b64encode(bytes_).decode("ascii").rstrip("=")
            comprobar("secreto(%s...)" % bytes_.hex()[:8], esperado, c("secreto", bytes_.hex()))

        # 3. Firma de flota y 4. respuesta al reto.
        secreto = base64.urlsafe_b64encode(SECRETO_BYTES).decode("ascii").rstrip("=")
        for mac in MACS:
            serial = sim.serial_desde_mac(bytes.fromhex(mac))
            comprobar("firma_de_flota(%s)" % serial, sim.firma_de_flota(CLAVE_FLOTA, serial, secreto),
                      c("flota", CLAVE_FLOTA, serial, secreto))
        for reto in RETOS:
            comprobar("respuesta_al_reto(%s)" % reto[:12], sim.responder_al_reto(secreto, reto),
                      c("reto", secreto, reto))
        # Clave de flota mas larga que el bloque de HMAC (se hashea primero).
        larga = "k" * 100
        comprobar("firma_de_flota con clave de 100", sim.firma_de_flota(larga, "fito-YUBUD6", secreto),
                  c("flota", larga, "fito-YUBUD6", secreto))

        # 5. Tabla de calibracion (SensorAnalogico). Dos puntos = la recta del potenciometro: extremos, recorte
        # fuera del rango util del ADC y punto medio.
        pot = ["100:0", "3100:100"]
        for mv, esperado in ((100, "0.000"), (0, "0.000"), (3100, "100.000"), (3300, "100.000"),
                             (1600, "50.000"), (850, "25.000")):
            comprobar("tabla potenciometro %d mV" % mv, esperado, c("tabla", str(mv), *pot))
        comprobar("tabla luz del potenciometro 2350 mV", "1500.000", c("tabla", "2350", "100:0", "3100:2000"))
        # Curva descendente (sonda de humedad: al aire mucho voltaje = 0 %, en agua poco = 100 %).
        sonda = ["1200:100", "2900:0"]
        for mv, esperado in ((1200, "100.000"), (2900, "0.000"), (2050, "50.000"), (3200, "0.000"), (800, "100.000"),
                             (1625, "75.000")):
            comprobar("tabla sonda %d mV" % mv, esperado, c("tabla", str(mv), *sonda))
        # Varios tramos, no lineal (LDR): cada tramo interpola entre sus vecinos.
        ldr = ["150:5000", "400:2000", "1000:500", "2000:100", "2800:10", "3100:0"]
        for mv, esperado in ((150, "5000.000"), (100, "5000.000"), (275, "3500.000"), (400, "2000.000"),
                             (700, "1250.000"), (1500, "300.000"), (2400, "55.000"), (2950, "5.000"), (3100, "0.000"),
                             (3300, "0.000")):
            comprobar("tabla ldr %d mV" % mv, esperado, c("tabla", str(mv), *ldr))
        # Casos borde: un solo punto, tabla vacia, valores negativos (temperatura).
        comprobar("tabla de un punto", "7.000", c("tabla", "500", "100:7"))
        comprobar("tabla vacia", "0.000", c("tabla", "500"))
        comprobar("tabla con negativos", "-5.000", c("tabla", "1000", "0:-20", "2000:10"))

        # 6. Lectura de las tablas de local.ini (tabla_cal.py, la usa extra_script.py al compilar).
        comprobar("leer_tabla ordena", "[(1200, 100.0), (2900, 0.0)]", str(leer_tabla("cal", "2900:0, 1200:100")))
        comprobar("leer_tabla con espacios y ;", "[(150, 5000.0), (400, 2000.5)]",
                  str(leer_tabla("cal", " 400 : 2000.5 ; 150:5000 ")))
        comprobar("como_c", "{{1200, 100.0f}, {2900, 0.0f}}", como_c(leer_tabla("cal", "2900:0, 1200:100")))
        for malo, motivo in (("2900:0", "un solo punto"), ("", "vacia"), ("2900:0, 2900:5", "mV repetido"),
                             ("2900-0, 1200:100", "sin dos puntos"), ("abc:0, 1200:100", "mV no numerico"),
                             ("4000:0, 1200:100", "mV fuera del ADC"),
                             (", ".join("%d:0" % (100 * i) for i in range(9)), "mas de 8 puntos")):
            try:
                leer_tabla("cal", malo)
                obtenido = "aceptada"
            except ValueError:
                obtenido = "rechazada"
            comprobar("leer_tabla rechaza %s" % motivo, "rechazada", obtenido)
    print("\n%d comprobaciones, %d fallas" % (total, fallos))
    return 1 if fallos else 0


if __name__ == "__main__":
    sys.exit(main())
