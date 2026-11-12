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

        # 5. Escala del potenciometro: extremos, recorte fuera del rango util del ADC y punto medio.
        for mv, esperado in ((100, "0.000"), (0, "0.000"), (3100, "100.000"), (3300, "100.000"),
                             (1600, "50.000"), (850, "25.000")):
            comprobar("escala humedad %d mV" % mv, esperado, c("escala", str(mv), "100", "3100", "0", "100"))
        comprobar("escala luz 2350 mV", "1500.000", c("escala", "2350", "100", "3100", "0", "2000"))
        comprobar("escala con rango vacio", "7.000", c("escala", "500", "100", "100", "7", "9"))
    print("\n%d comprobaciones, %d fallas" % (total, fallos))
    return 1 if fallos else 0


if __name__ == "__main__":
    sys.exit(main())
