#!/usr/bin/env python3
"""Compone a 128x64 las pantallas de la OLED con los sprites de arte/ y una fuente 5x7 como la de Adafruit GFX
(se lee de glcdfont.c de la libreria, que PlatformIO deja en firmware/.pio/libdeps). Guarda PNG x4.
Uso: python3 firmware/sprites/previsualizar.py [carpeta_de_salida]"""
import glob
import math
import os
import re
import sys
from PIL import Image, ImageDraw

AQUI = os.path.dirname(os.path.abspath(__file__))
SALIDA = sys.argv[1] if len(sys.argv) > 1 else os.path.join(AQUI, "previa", "pantallas")
os.makedirs(SALIDA, exist_ok=True)

def cargar_fuente():
    ruta = glob.glob(os.path.join(AQUI, "..", ".pio", "libdeps", "*", "Adafruit GFX Library", "glcdfont.c"))
    if not ruta:
        raise SystemExit("falta glcdfont.c: compila una vez con pio para bajar las librerias")
    txt = open(ruta[0]).read()
    txt = txt[txt.index("{"):]
    txt = re.sub(r"//.*", "", txt)
    return [int(x, 16) for x in re.findall(r"0x[0-9A-Fa-f]{2}", txt)]

FUENTE = cargar_fuente()

def arte(nombre):
    filas = [l.strip() for l in open(os.path.join(AQUI, "arte", nombre + ".txt")) if l.strip()]
    im = Image.new("1", (len(filas[0]), len(filas)), 0)
    im.putdata([1 if c == "#" else 0 for f in filas for c in f])
    return im

class Oled:
    def __init__(self):
        self.im = Image.new("1", (128, 64), 0)
        self.d = ImageDraw.Draw(self.im)
    def sprite(self, nombre, x, y):
        s = arte(nombre)
        self.im.paste(1, (x, y), s)           # drawBitmap(..., WHITE): solo enciende
    def texto(self, x, y, t, size=1, color=1):
        for ch in t:
            c = ord(ch)
            for col in range(5):
                byte = FUENTE[c * 5 + col]
                for fila in range(7):
                    if byte >> fila & 1:
                        self.d.rectangle((x + col * size, y + fila * size, x + col * size + size - 1, y + fila * size + size - 1), fill=color)
            x += 6 * size
    def qr(self, x0, y0, lado_total, modulos, mod_px=2, margen=3):
        self.d.rectangle((x0, y0, x0 + lado_total - 1, y0 + 63), fill=1)
        n = len(modulos)
        for y in range(n):
            for x in range(n):
                if modulos[y][x]:
                    self.d.rectangle((x0 + margen + x * mod_px, y0 + margen + y * mod_px, x0 + margen + x * mod_px + mod_px - 1, y0 + margen + y * mod_px + mod_px - 1), fill=0)
    def guardar(self, nombre):
        self.im.convert("L").resize((512, 256), Image.NEAREST).save(os.path.join(SALIDA, nombre + ".png"))

def qr_ejemplo(semilla):
    """No es un QR valido: modulos de ejemplo con los tres localizadores, solo para ver el encaje (29x29)."""
    import random
    r = random.Random(semilla)
    n = 29
    m = [[r.random() < 0.5 for _ in range(n)] for _ in range(n)]
    for (ox, oy) in ((0, 0), (n - 7, 0), (0, n - 7)):
        for y in range(-1, 8):
            for x in range(-1, 8):
                xx, yy = ox + x, oy + y
                if 0 <= xx < n and 0 <= yy < n:
                    borde = x in (0, 6) or y in (0, 6)
                    centro = 2 <= x <= 4 and 2 <= y <= 4
                    m[yy][xx] = (0 <= x <= 6 and 0 <= y <= 6) and (borde or centro)
    return m

def pantalla_reclamo(codigo="YUBUD6", puntos=1):
    o = Oled()
    o.qr(0, 0, 64, qr_ejemplo(1))
    o.texto(68, 0, "Codigo")
    o.texto(68, 12, codigo[:3], 2)
    o.texto(68, 30, codigo[3:], 2)
    for i in range(3):
        o.d.rectangle((70 + i * 8, 56, 71 + i * 8 + (1 if i == puntos % 3 else 0), 57 + (1 if i == puntos % 3 else 0)), fill=1)
    return o

def pantalla_exito(i):
    o = Oled()
    o.sprite(f"exito_{i}", 32, 0)
    return o

def pantalla_vinculada_final():
    o = Oled()
    o.sprite("exito_7", 2, 0)
    o.texto(70, 20, "Vinculada", 1)
    o.texto(70, 34, "Enviando", 1)
    o.texto(70, 44, "lecturas...", 1)
    return o

def pantalla_lecturas(sprite, estado, punto=False, hum="65", luz="1200", temp="24.5", codigo="YUBUD6"):
    o = Oled()
    o.sprite(sprite, 0, 0)
    filas = [("icono_gota", hum, "%"), ("icono_sol", luz, "lx"), ("icono_termometro", temp, "C")]
    for i, (ic, v, u) in enumerate(filas):
        y = 1 + i * 16
        o.sprite(ic, 50, y + 3)
        o.texto(61, y, v, 2)
        o.texto(61 + 12 * len(v) + 1, y + 7, u, 1)
    o.d.line((50, 50, 127, 50), fill=1)
    o.texto(50, 55, estado, 1)
    if punto:
        o.d.ellipse((120, 54, 126, 60), fill=1)
    return o

def pantalla_portal():
    o = Oled()
    o.qr(0, 0, 62, qr_ejemplo(2), margen=2)
    o.texto(63, 4, "Conecta tu")
    o.texto(63, 14, "celular a:")
    o.texto(63, 34, "Fito-YUBUD6")
    return o

def main():
    pantalla_reclamo().guardar("01_reclamo")
    for i in range(8):
        pantalla_exito(i).guardar(f"02_exito_{i}")
    pantalla_vinculada_final().guardar("03_vinculada_final")
    pantalla_lecturas("planta_saludable_0", "SALUDABLE", True).guardar("04_lecturas_saludable")
    pantalla_lecturas("planta_riesgo_0", "EN RIESGO", hum="22").guardar("05_lecturas_riesgo")
    pantalla_lecturas("planta_critico_0", "CRITICO", hum="9").guardar("06_lecturas_critico")
    pantalla_lecturas("carni_abierta_0", "SALUDABLE", luz="9500").guardar("07_carni_abierta")
    pantalla_lecturas("carni_cerrada", "SALUDABLE", True, luz="9500").guardar("08_carni_mordisco")
    pantalla_lecturas("carni_riesgo_0", "EN RIESGO", luz="9500").guardar("09_carni_riesgo")
    pantalla_lecturas("carni_critico_0", "CRITICO", luz="9500").guardar("10_carni_critico")
    pantalla_portal().guardar("11_portal")
    print("pantallas en", SALIDA)

main()
