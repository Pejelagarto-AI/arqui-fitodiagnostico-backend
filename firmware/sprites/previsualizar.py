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

def ventana_nombre(o, x, y, ancho, nombre, t):
    """Espejo de nombreEnVentana(): fijo si cabe, marquesina con pausa de 1 s en los extremos si no."""
    w = len(nombre) * 6 - 1
    off = 0
    if w > ancho:
        recorrido = (w - ancho) * 1000 // 24
        fase = t % (recorrido + 2000)
        off = 0 if fase < 1000 else (fase - 1000) * 24 // 1000 if fase < 1000 + recorrido else w - ancho
    tmp = Oled()
    tmp.texto(-off, 0, nombre)
    o.im.paste(tmp.im.crop((0, 0, ancho, 8)), (x, y))

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
        d = 3 if i == puntos % 3 else 2
        o.d.rectangle((70 + i * 8, 58 - d // 2, 70 + i * 8 + d - 1, 58 - d // 2 + d - 1), fill=1)
    return o

def pantalla_exito(i):
    o = Oled()
    o.sprite(f"exito_{i}", 32, 0)
    return o

def pantalla_vinculada_final(codigo="YUBUD6"):
    o = Oled()
    o.sprite("exito_7", 2, 0)
    o.texto(70, 22, "Vinculada")
    o.texto(70, 36, codigo)
    return o

def pantalla_portal():
    o = Oled()
    o.qr(0, 0, 62, qr_ejemplo(2), margen=2)
    o.texto(63, 4, "Conecta tu")
    o.texto(63, 14, "celular a:")
    o.texto(63, 34, "Fito-YUBUD6")
    return o

# ---------------------------------------------------------------- animacion (espejo de pantalla.cpp)
AMPLITUD = [2.2, 1.5, 0.8]
CARA_NIVEL = [(1.0, 0.0, 1.0, 0.0), (0.55, 0.6, -0.5, 0.7), (0.6, 1.0, -1.0, 0.0)]   # abierto, inclina, boca, onda
MORDISCO = [(90, 0, 0), (170, 1, 0), (290, 2, 2), (380, 1, 1), (460, 0, -1)]
CARNI_BOCA = ["carni_abierta_0", "carni_media", "carni_cerrada"]
CARNI_G = [14, 7, 0]
PARPADEOS = [3400, 7600, 11300, 15700]       # en C++ es cada 3 a 5 s al azar

def suave(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)

def redondear(v):
    return int(math.floor(abs(v) + 0.5)) * (1 if v >= 0 else -1)

# Espejo de las familias de pantalla.cpp (fase 7): geometria de la cara (separacion, alto del ojo, media boca,
# fila de la boca, curva, fila de la ceja, columna de la lagrima), centro de la cara por nivel y balanceo.
GEO_TRAMPA = (9, 7, 7, 2, 4.0, -6, 13)
GEO_CHICA = (4, 5, 4, 4, 2.0, -4, 7)
GEO_CACTUS = (5, 5, 5, 4, 2.0, -5, 8)
CARA_GIRASOL = [(24, 17), (28, 22), (31, 29)]
CARA_CACTUS = [(24, 22), (24, 23), (24, 29)]
CARA_SUCULENTA = [(24, 36), (24, 37), (24, 39)]
FACTOR_BALANCEO = {"comun": 1.0, "carnivora": 1.0, "girasol": 1.3, "cactus": 0.4, "suculenta": 0.4, "helecho": 1.2}
HELECHO_MS = [450, 700, 1100]

class Lecturas:
    def __init__(self, familia, nivel_en, mordiscos=(), codigo="Poti", hum="65", luz="1200", temp="24.5"):
        if familia is True or familia is False:            # compatibilidad: antes era "es carnivora"
            familia = "carnivora" if familia else "comun"
        self.familia = familia
        self.carn, self.nivel_en, self.mordiscos = familia == "carnivora", nivel_en, list(mordiscos)
        self.codigo, self.hum, self.luz, self.temp = codigo, hum, luz, temp
        self.cara_nivel = -1
        self.nivel_previo, self.nivel_desde = -1, 0
        self.regado_desde = None
        self.desde = self.hasta = CARA_NIVEL[0]
        self.t0 = 0

    def cara(self, t):
        k = suave((t - self.t0) / 400.0)
        return tuple(a + (b - a) * k for a, b in zip(self.desde, self.hasta))

    def fijar(self, nivel, t):
        if nivel == self.cara_nivel:
            return
        self.desde = CARA_NIVEL[nivel] if self.cara_nivel < 0 else self.cara(t)
        self.hasta = CARA_NIVEL[nivel]
        self.t0, self.cara_nivel = t, nivel

    def parpadeo(self, t):
        for b in PARPADEOS:
            if 0 <= t - b < 150:
                dt = t - b
                return 0.5 if dt < 50 else 0.0 if dt < 100 else 0.5
        return 1.0

    def desv(self, y, seno, amp):
        return redondear(amp * seno * (46 - y) / 46.0) if y < 46 else 0

    def cuerpo(self, o, nombre, seno, amp, dy):
        s = arte(nombre)
        for y in range(46):
            o.im.paste(1, (self.desv(y, seno, amp), y + dy), s.crop((0, y, 48, y + 1)))
        o.im.paste(1, (0, 46), s.crop((0, 46, 48, 64)))

    def boca_y(self, x, cx, semi, y0, amplitud, c):
        u = (x - cx) / semi
        return y0 - redondear(c[2] * amplitud * u * u) + redondear(c[3] * 0.9 * math.sin(x * 2.3))

    def cara_matera(self, o, c, parp, t):
        a = c[0] * parp
        h = 3 if a >= 0.85 else 2 if a >= 0.4 else 1
        for ex in (17, 28):
            o.d.rectangle((ex, 58 - h, ex + 2, 57), fill=0)
            if h == 3:
                o.d.point((ex, 55), fill=1); o.d.point((ex + 2, 55), fill=1)
        if c[1] > 0.2:
            dentro = 53 - redondear(c[1]); medio = 53 - (1 if c[1] > 0.5 else 0)
            for p in ((17, 53), (18, medio), (19, dentro), (30, 53), (29, medio), (28, dentro)):
                o.d.point(p, fill=0)
        for x in range(21, 27):
            y = self.boca_y(x, 23.5, 2.5, 60, 1.2, c)
            o.d.point((x, max(59, min(62, y))), fill=0)
        if self.cara_nivel == 2:
            fase = t % 4200
            if fase < 900:
                y = 56 + fase * 5 // 900
                for p in ((31, y), (30, y + 1), (31, y + 1), (32, y + 1), (31, y + 2)):
                    o.d.point(p, fill=0)

    def cara_grande(self, o, c, parp, t, cx, cy, g, con_boca):
        sep, alto, semi, yb, curva, yceja, xlag = g
        a = c[0] * parp
        h = max(1, redondear(alto * a))
        for lado in (-1, 1):
            ex = cx + sep * lado
            top = cy - h // 2
            r = 2 if h >= 4 else 1 if h >= 2 else 0
            o.d.rounded_rectangle((ex - 2, top, ex + 2, top + h - 1), radius=r, fill=0)
            if h >= 5:
                o.d.point((ex - 1, top + 1), fill=1)
            if c[1] > 0.2:
                fuera = cy + yceja + redondear(c[1]); dentro = cy + yceja - redondear(2 * c[1])
                o.d.line((ex + 4 * lado, fuera, ex - 3 * lado, dentro), fill=0)
        if con_boca:
            for x in range(cx - semi, cx + semi + 1):
                o.d.point((x, self.boca_y(x, cx, float(semi), cy + yb, curva, c)), fill=0)
            if self.cara_nivel == 2:
                fase = t % 4200
                if fase < 900:
                    y = cy + yb + 1 + fase * 7 // 900
                    o.d.point((cx + xlag, y), fill=0); o.d.point((cx + xlag, y + 1), fill=0)

    def flor_cactus(self, o, x, y, k):
        if k <= 0:
            return
        if k < 0.3:
            o.d.rectangle((x - 1, y + 1, x + 1, y + 2), fill=1)
            return
        dd, r = 1 + redondear(2 * k), 1
        for cx, cy in ((x, y - dd), (x - dd, y - 1), (x + dd, y - 1), (x - dd + 1, y + dd - 1), (x + dd - 1, y + dd - 1)):
            o.d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=1)
        o.d.point((x, y), fill=0)

    def pintar_cactus(self, o, c, parp, t, seno, amp, nivel):
        x, y = CARA_CACTUS[nivel]
        self.cuerpo(o, ["cactus_saludable", "cactus_riesgo", "cactus_critico"][nivel], seno, amp, 0)
        self.cara_grande(o, c, parp, t, x + self.desv(y, seno, amp), y, GEO_CACTUS, True)
        if nivel == 0 and t - self.nivel_desde > 600:
            k = min(1.0, (t - self.nivel_desde - 600) / 1500)
            self.flor_cactus(o, 24 + self.desv(4, seno, amp), 4, suave(k))

    def regar(self, t):
        self.regado_desde = t

    def pintar_suculenta(self, o, c, parp, t, seno, amp, nivel):
        x, y = CARA_SUCULENTA[nivel]
        tr = t - self.regado_desde if self.regado_desde is not None else 10 ** 9
        regando = tr < 1600
        inflada = nivel == 0 and t % 4500 < 500
        dy = 0
        if regando and tr >= 500:
            if nivel == 0:
                inflada = True
            else:
                dy = -1 if tr < 800 else 0
            c = (c[0], min(c[1], 0.3), max(c[2], 0.8), 0.0)
        nombre = "suculenta_saludable_1" if inflada else ["suculenta_saludable_0", "suculenta_riesgo_0", "suculenta_critico_0"][nivel]
        self.cuerpo(o, nombre, seno, amp, dy)
        self.cara_grande(o, c, parp, t, x + self.desv(y, seno, amp), y + dy, GEO_CHICA, True)
        if regando and tr < 500:
            o.sprite("icono_gota", 20, -8 + (y - 14) * tr // 500)

    def pintar_helecho(self, o, c, parp, t, seno, amp, nivel):
        estado = ["saludable", "riesgo", "critico"][nivel]
        self.cuerpo(o, f"helecho_{estado}_{(t // HELECHO_MS[nivel]) % 2}", seno, amp, 0)
        self.cara_matera(o, c, parp, t)

    def cara_trampa(self, o, c, parp, t, seno, amp, yo, dy, con_boca):
        self.cara_grande(o, c, parp, t, 24 + self.desv(yo, seno, amp), yo + dy, GEO_TRAMPA, con_boca)

    def pintar_girasol(self, o, c, parp, t, seno, amp, nivel):
        x, y = CARA_GIRASOL[nivel]
        dy = mira = 0
        nombre = ["girasol_saludable_0", "girasol_riesgo_0", "girasol_critico_0"][nivel]
        if nivel == 0:
            if (t // 400) % 2:
                nombre = "girasol_saludable_1"
            dy = redondear(0.6 * math.sin(2 * math.pi * (t % 1600) / 1600))
            mira = redondear(1.5 * math.sin(2 * math.pi * (t % 5000) / 5000))
        self.cuerpo(o, nombre, seno, amp, dy)
        self.cara_grande(o, c, parp, t, x + mira + self.desv(y, seno, amp), y + dy, GEO_CHICA, True)

    def cuadro(self, t):
        nivel = self.nivel_en(t)
        if nivel != self.nivel_previo:
            self.nivel_previo, self.nivel_desde = nivel, t
        self.fijar(nivel, t)
        c = self.cara(t)
        parp = self.parpadeo(t)
        seno = math.sin(2 * math.pi * (t % 2800) / 2800)
        amp = AMPLITUD[nivel] * FACTOR_BALANCEO[self.familia]
        o = Oled()
        if self.familia == "girasol":
            self.pintar_girasol(o, c, parp, t, seno, amp, nivel)
        elif self.familia == "cactus":
            self.pintar_cactus(o, c, parp, t, seno, amp, nivel)
        elif self.familia == "suculenta":
            self.pintar_suculenta(o, c, parp, t, seno, amp, nivel)
        elif self.familia == "helecho":
            self.pintar_helecho(o, c, parp, t, seno, amp, nivel)
        elif not self.carn:
            self.cuerpo(o, ["planta_saludable_0", "planta_riesgo_0", "planta_critico_0"][nivel], seno, amp, 0)
            self.cara_matera(o, c, parp, t)
        elif nivel > 0:
            self.cuerpo(o, ["carni_riesgo_0", "carni_critico_0"][nivel - 1], seno, amp, 0)
            self.cara_trampa(o, c, parp, t, seno, amp, 25 if nivel == 1 else 30, 0, True)
        else:
            boca, dy = 0, 0
            for ini in self.mordiscos:
                if 0 <= t - ini < 460:
                    for hasta, b, d in MORDISCO:
                        if t - ini < hasta:
                            boca, dy = b, d
                            break
            self.cuerpo(o, CARNI_BOCA[boca], seno, amp, dy)
            cm = (c[0] * (0.3 if boca == 2 else 1),) + tuple(c[1:])
            self.cara_trampa(o, cm, parp, t, seno, amp, (32 - CARNI_G[boca]) // 2, dy, False)
        etiqueta = ["SALUDABLE", "EN RIESGO", "CRITICO"][nivel]
        for i, (ic, v, u) in enumerate((("icono_gota", self.hum, "%"), ("icono_sol", self.luz, "lx"), ("icono_termometro", self.temp, "C"))):
            y = i * 15
            o.sprite(ic, 50, y + 3)
            o.texto(61, y, v, 2)
            if len(v) < 5:
                o.texto(61 + 12 * len(v) + 1, y + 7, u)
        ventana_nombre(o, 50, 47, 78, self.codigo, t)
        o.texto(50, 56, etiqueta)
        return o

def anillo_visto(o, xo, p):
    for r in (28, 29, 30):
        o.d.ellipse((xo + 32 - r, 32 - r, xo + 32 + r, 32 + r), outline=1)
    L1, L2 = 15.6, 29.4
    d = p * (L1 + L2)
    s = 0.0
    while s <= d:
        if s <= L1:
            x, y = 17 + 11 * s / L1, 33 + 11 * s / L1
        else:
            k = (s - L1) / L2
            x, y = 28 + 19 * k, 44 - 23 * k
        o.d.ellipse((xo + int(x) - 2, int(y) - 2, xo + int(x) + 2, int(y) + 2), fill=1)
        s += 1.0
    if p >= 1.0:
        for (x, y) in ((xo + 6, 56), (xo + 58, 6)):
            o.d.line((x - 3, y, x + 3, y), fill=1); o.d.line((x, y - 3, x, y + 3), fill=1)

def cuadro_exito(t, codigo="Poti"):
    o = Oled()
    T_ANILLO, T_VISTO = 750, 1000
    T_FIN_VISTO = T_VISTO + 450
    T_DESLIZ = T_FIN_VISTO + 200
    T_FIN_DESLIZ = T_DESLIZ + 400
    xo = 32 if t < T_DESLIZ else 32 - int(suave((t - T_DESLIZ) / (T_FIN_DESLIZ - T_DESLIZ)) * 30)
    if t < T_ANILLO:
        o.sprite(f"exito_{t // 125}", xo, 0)
    else:
        p = 0.0 if t <= T_VISTO else 1.0 if t >= T_FIN_VISTO else (t - T_VISTO) / (T_FIN_VISTO - T_VISTO)
        p = 1 - (1 - p) ** 2
        anillo_visto(o, xo, p)
        if t >= T_FIN_DESLIZ:
            o.texto(70, 22, "Vinculada"); ventana_nombre(o, 70, 36, 58, codigo, t - T_FIN_DESLIZ)
    return o

def gif(nombre, cuadro_fn, duracion_ms, paso=50):
    cuadros = [cuadro_fn(t).im.convert("L").resize((512, 256), Image.NEAREST) for t in range(0, duracion_ms, paso)]
    cuadros[0].save(os.path.join(SALIDA, nombre + ".gif"), save_all=True, append_images=cuadros[1:], duration=paso, loop=0, optimize=False)

def pantalla_lecturas(sprite, estado, punto=False, hum="65", luz="1200", temp="24.5", codigo="Poti", t=None):
    nivel = {"SALUDABLE": 0, "EN RIESGO": 1, "CRITICO": 2}[estado]
    mord = sprite == "carni_cerrada"
    sim = Lecturas(sprite.startswith("carni"), lambda t: nivel, [100000] if mord else [], codigo, hum, luz, temp)
    o = sim.cuadro(t if t else 100000 + 200 if mord else 2500)
    if punto:
        o.d.ellipse((120, 56, 126, 62), fill=1)
    return o

def main():
    pantalla_reclamo().guardar("01_reclamo")
    for i in range(6):
        pantalla_exito(i).guardar(f"02_exito_{i}")
    cuadro_exito(1000 + 450 + 100).guardar("02_exito_visto")
    cuadro_exito(3400).guardar("03_vinculada_final")
    pantalla_lecturas("planta_saludable_0", "SALUDABLE", True, codigo="Poti").guardar("04_lecturas_saludable")
    pantalla_lecturas("carni_abierta_0", "SALUDABLE", luz="9500", codigo="Lola la carnivora", t=3000).guardar("12_lecturas_nombre_largo")
    pantalla_lecturas("planta_riesgo_0", "EN RIESGO", hum="22").guardar("05_lecturas_riesgo")
    pantalla_lecturas("planta_critico_0", "CRITICO", hum="9").guardar("06_lecturas_critico")
    pantalla_lecturas("carni_abierta_0", "SALUDABLE", luz="9500").guardar("07_carni_abierta")
    pantalla_lecturas("carni_cerrada", "SALUDABLE", True, luz="9500").guardar("08_carni_mordisco")
    pantalla_lecturas("carni_riesgo_0", "EN RIESGO", luz="9500").guardar("09_carni_riesgo")
    pantalla_lecturas("carni_critico_0", "CRITICO", luz="9500").guardar("10_carni_critico")
    pantalla_portal().guardar("11_portal")
    gif("anim_saludable", Lecturas(False, lambda t: 0, codigo="Lola la carnivora").cuadro, 6000)
    gif("anim_riesgo", Lecturas(False, lambda t: 1, hum="22").cuadro, 6000)
    gif("anim_critico", Lecturas(False, lambda t: 2, hum="9").cuadro, 6000)
    gif("anim_transicion", Lecturas(False, lambda t: 0 if t < 2000 else 1 if t < 4000 else 2).cuadro, 6000)
    gif("anim_carnivora", Lecturas(True, lambda t: 0, [1000, 4000], luz="9500").cuadro, 6000)
    gif("anim_carnivora_triste", Lecturas(True, lambda t: 0 if t < 1500 else 1 if t < 3500 else 2, luz="9500").cuadro, 6000)
    gif("anim_exito", cuadro_exito, 3500)
    mascotas(SALIDA)
    print("pantallas en", SALIDA)

def mascotas(salida):
    """Fase 7: una pantalla por familia y nivel, y un GIF de cada familia pasando por los tres estados."""
    for familia in FACTOR_BALANCEO:
        if familia in ("comun", "carnivora"):
            continue
        for nivel, estado in enumerate(("saludable", "riesgo", "critico")):
            sim = Lecturas(familia, lambda t, n=nivel: n)
            sim.cuadro(0)                                     # entra al nivel en t=0: a los 2.5 s la flor ya crecio
            sim.cuadro(2500).guardar(f"20_{familia}_{estado}")
        # el GIF pasa por los tres estados y vuelve a saludable (la flor del cactus vuelve a crecer)
        sim = Lecturas(familia, lambda t: 0 if t < 4000 or t >= 8000 else 1 if t < 6000 else 2)
        if familia == "suculenta":
            def cuadro(t, sim=sim):
                if t in (2000, 9000):                         # riego en saludable y al volver de critico
                    sim.regar(t)
                return sim.cuadro(t)
            gif(f"anim_{familia}", cuadro, 11000)
        else:
            gif(f"anim_{familia}", sim.cuadro, 11000)

main()
