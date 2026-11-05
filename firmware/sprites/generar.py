#!/usr/bin/env python3
"""Convierte sprites/arte/*.txt en src/sprites.h (formato Adafruit_GFX::drawBitmap, MSB primero) y en PNG ampliados x4
en sprites/previa/. Uso:  python3 firmware/sprites/generar.py   (Python 3 + PIL)"""
import glob
import os
from PIL import Image

AQUI = os.path.dirname(os.path.abspath(__file__))
SALIDA_H = os.path.join(AQUI, "..", "src", "sprites.h")
PREVIA = os.path.join(AQUI, "previa")


def leer(ruta):
    filas = [l.strip() for l in open(ruta) if l.strip()]
    w = len(filas[0])
    assert all(len(f) == w for f in filas), f"{ruta}: filas de distinto ancho"
    assert all(set(f) <= set("#.") for f in filas), f"{ruta}: solo # y ."
    return w, filas


def empaquetar(w, filas):
    bpf = (w + 7) // 8
    out = []
    for f in filas:
        for b in range(bpf):
            v = 0
            for bit in range(8):
                x = b * 8 + bit
                v = (v << 1) | (1 if x < w and f[x] == "#" else 0)
            out.append(v)
    return out


def main():
    os.makedirs(PREVIA, exist_ok=True)
    lineas = ["// GENERADO por sprites/generar.py desde sprites/arte/*.txt. No editar a mano.",
              "// Formato de Adafruit_GFX::drawBitmap: MSB primero, filas completadas a byte.",
              "#pragma once", "#include <Arduino.h>", "", "namespace sprites {"]
    for ruta in sorted(glob.glob(os.path.join(AQUI, "arte", "*.txt"))):
        nombre = os.path.basename(ruta)[:-4]
        w, filas = leer(ruta)
        h = len(filas)
        N = nombre.upper()
        datos = empaquetar(w, filas)
        lineas.append(f"constexpr int {N}_W = {w}, {N}_H = {h};")
        lineas.append(f"const uint8_t {nombre}[] PROGMEM = {{")
        bpf = (w + 7) // 8
        for i in range(0, len(datos), bpf):
            lineas.append("    " + ",".join(f"0x{b:02X}" for b in datos[i:i + bpf]) + ",")
        lineas.append("};")
        im = Image.new("L", (w, h), 0)
        im.putdata([255 if c == "#" else 0 for f in filas for c in f])
        im.resize((w * 4, h * 4), Image.NEAREST).save(os.path.join(PREVIA, nombre + ".png"))
    lineas.append("}")
    open(SALIDA_H, "w").write("\n".join(lineas) + "\n")
    print("sprites.h escrito")


if __name__ == "__main__":
    main()
