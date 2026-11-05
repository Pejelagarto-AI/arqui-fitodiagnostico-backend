#!/usr/bin/env python3
"""Dibuja el arte original de la matera con primitivas (PIL, 1 bit) y lo escribe como ASCII en arte/*.txt.

Los .txt (`#` encendido, `.` apagado) son la fuente de verdad: se pueden retocar a mano y generar.py los convierte
en src/sprites.h. Este script existe para no dibujar 3000 pixeles a mano; correrlo REEMPLAZA los .txt de las plantas
y de la animacion (los iconos son a mano y no se tocan).

    python3 firmware/sprites/dibujar.py && python3 firmware/sprites/generar.py
"""
import math
import os
from PIL import Image, ImageDraw

AQUI = os.path.dirname(os.path.abspath(__file__))
ARTE = os.path.join(AQUI, "arte")


def lienzo(w, h):
    im = Image.new("1", (w, h), 0)
    return im, ImageDraw.Draw(im)


def guardar(nombre, im):
    w, h = im.size
    filas = ["".join("#" if im.getpixel((x, y)) else "." for x in range(w)) for y in range(h)]
    with open(os.path.join(ARTE, nombre + ".txt"), "w") as f:
        f.write("\n".join(filas) + "\n")


def hoja(d, base, tip, ancho, curva=0.0, vena=True):
    """Hoja en forma de lenteja entre base y punta; `curva` la arquea hacia un lado (en pixeles)."""
    bx, by = base
    tx, ty = tip
    dx, dy = tx - bx, ty - by
    largo = math.hypot(dx, dy)
    ux, uy = dx / largo, dy / largo
    px, py = -uy, ux
    izq, der = [], []
    n = 14
    for i in range(n + 1):
        t = i / n
        a = ancho * math.sin(math.pi * t) ** 0.85
        c = curva * math.sin(math.pi * t)
        cx, cy = bx + dx * t + px * c, by + dy * t + py * c
        izq.append((cx + px * a, cy + py * a))
        der.append((cx - px * a, cy - py * a))
    d.polygon(izq + der[::-1], fill=1)
    if vena and largo >= 13 and ancho >= 3:
        pts = []
        for i in range(2, 11):
            t = i / n
            c = curva * math.sin(math.pi * t)
            pts.append((bx + dx * t + px * c, by + dy * t + py * c))
        d.line(pts, fill=0, width=1)


def tallo(d, pts, grosor=3):
    d.line(pts, fill=1, width=grosor, joint="curve")


def curva_q(p0, p1, p2, n=12):
    out = []
    for i in range(n + 1):
        t = i / n
        out.append(((1 - t) ** 2 * p0[0] + 2 * t * (1 - t) * p1[0] + t * t * p2[0],
                    (1 - t) ** 2 * p0[1] + 2 * t * (1 - t) * p1[1] + t * t * p2[1]))
    return out


def matera(d, cara=None, banda=False):
    """Matera 48 de ancho: borde y 46..49, linea negra en 50, cuerpo 51..63."""
    d.rectangle((8, 46, 39, 49), fill=1)
    d.line((9, 50, 38, 50), fill=0)
    d.polygon([(11, 51), (36, 51), (33, 63), (14, 63)], fill=1)
    if banda:
        d.line((13, 58, 34, 58), fill=0)
        for x in range(14, 34, 4):
            d.point((x, 60), fill=0)
    if cara:
        for x in (18, 19, 28, 29):
            if cara == "cansada":
                d.point((x, 55), fill=0)
            else:
                d.point((x, 54), fill=0); d.point((x, 55), fill=0)
        if cara == "contenta":
            for p in [(21, 58), (26, 58), (22, 59), (23, 59), (24, 59), (25, 59)]: d.point(p, fill=0)
        elif cara == "seria":
            for x in range(22, 26): d.point((x, 59), fill=0)
        elif cara in ("triste", "cansada"):
            for p in [(22, 58), (23, 58), (24, 58), (25, 58), (21, 59), (26, 59)]: d.point(p, fill=0)


# ---------------------------------------------------------------- planta comun

def planta(estado, cuadro):
    im, d = lienzo(48, 64)
    s = (-1, 1)[cuadro]
    if estado == "saludable":
        tallo(d, curva_q((24, 47), (24, 38), (24 + s, 26)), 3)
        hoja(d, (24 + s, 28), (24 + s * 2, 2), 7, curva=s * 1.5)
        hoja(d, (24, 38), (5 + s, 16), 6, curva=-3)
        hoja(d, (24, 35), (43 + s, 13), 6, curva=3)
        hoja(d, (24, 44), (7, 36), 4, curva=-2, vena=False)
        hoja(d, (24, 44), (41, 35), 4, curva=2, vena=False)
        matera(d, "contenta")
    elif estado == "riesgo":
        tallo(d, curva_q((24, 47), (24, 34), (30 + s, 25)), 3)
        hoja(d, (30 + s, 26), (42 + s, 36), 6, curva=-4)          # hoja de la punta, caida
        hoja(d, (25, 37), (5, 40 + s), 6, curva=5)
        hoja(d, (27, 33), (46, 41 - s), 5, curva=-5)
        hoja(d, (28, 28), (14, 17), 4, curva=4, vena=False)
        matera(d, "seria")
    else:  # critico
        tallo(d, curva_q((24, 47), (22, 30), (33 + s, 30)), 3)
        hoja(d, (33 + s, 31), (37 + s, 44), 5, curva=-2)           # cuelga casi vertical
        hoja(d, (24, 38), (14, 45 + s), 4, curva=3, vena=False)
        hoja(d, (26, 35), (21 + s, 45), 4, curva=-3, vena=False)
        hoja(d, (37, 61), (47, 59), 2, curva=1, vena=False)        # una hoja en el suelo
        matera(d, "triste")
    return im


# ---------------------------------------------------------------- carnivora

def mitad(w, h, cx, cy, rx, ry, arriba, y_corte):
    """Capa con la mitad de una elipse (no usada: se deja la tecnica de lente en trampa())."""
    raise NotImplementedError


def trampa(d, cx, cy, abertura, estado):
    """Cabeza de atrapamoscas: elipse grande con una boca en lente, dientes triangulares y ojos."""
    g = abertura
    sup, inf = cy - 13 - g / 2, cy + 9 + g / 2
    d.ellipse((cx - 22, sup, cx + 22, inf), fill=1)
    ancho_boca = 19
    if estado == "feliz" and g > 0:
        d.ellipse((cx - ancho_boca, cy - g / 2, cx + ancho_boca, cy + g / 2), fill=0)
        L = 5 if g >= 10 else 3

        def borde(x):
            u = (x - cx) / ancho_boca
            return (g / 2) * math.sqrt(max(0.0, 1 - u * u))

        for off in (-12, -6, 0, 6, 12):
            x, yb = cx + off, cy - borde(cx + off)
            d.polygon([(x - 3, yb - 1), (x + 3, yb - 1), (x, yb + L)], fill=1)
        for off in (-9, -3, 3, 9):
            x, yb = cx + off, cy + borde(cx + off)
            d.polygon([(x - 3, yb + 1), (x + 3, yb + 1), (x, yb - L)], fill=1)
    elif estado == "feliz":
        # cerrada: zigzag de dientes entrelazados
        pts = [(cx - ancho_boca + i * 3, cy + (2 if i % 2 else -2)) for i in range(0, 14)]
        d.line(pts, fill=0, width=1)
        d.line((cx - ancho_boca, cy, cx - ancho_boca + 1, cy), fill=0)
    else:
        # triste: boca pequena en arco hacia abajo, con dientecitos
        k = 3 if estado == "riesgo" else 5
        for x in range(cx - 7, cx + 8):
            u = (x - cx) / 7
            d.point((x, cy + 4 - round(k * (1 - u * u))), fill=0)
    # ojos
    yo = sup + 8 if estado == "feliz" else sup + 12
    for ex in (cx - 9, cx + 9):
        if estado == "critico":
            d.line((ex - 3, yo - 3, ex + 3, yo + 3), fill=0); d.line((ex - 3, yo + 3, ex + 3, yo - 3), fill=0)
            d.line((ex - 3, yo - 2, ex + 3, yo + 4), fill=0); d.line((ex - 3, yo + 4, ex + 3, yo - 2), fill=0)
        else:
            d.ellipse((ex - 3, yo - 3, ex + 3, yo + 4) if estado != "feliz" else (ex - 2, yo - 3, ex + 2, yo + 3), fill=0)
            d.point((ex - 1, yo - 2), fill=1)
            if estado == "riesgo":
                d.line((ex - 4 if ex < cx else ex + 4, yo - 4, ex + 3 if ex < cx else ex - 3, yo - 7), fill=0)
    if estado != "feliz":
        d.ellipse((cx + 13, sup + 7, cx + 15, sup + 12), fill=0)       # gota de sudor
        d.point((cx + 14, sup + 5), fill=0)


def carnivora(estado, cuadro, abertura=14):
    im, d = lienzo(48, 64)
    if estado == "feliz":
        cy = 21 + (cuadro % 2)
        sup, inf = cy - 13 - abertura / 2, cy + 9 + abertura / 2
        base_cabeza = inf
        tallo(d, curva_q((24, 47), (24, 42), (24, base_cabeza - 2)), 5)
        hoja(d, (22, 46), (6, 39), 3, curva=-2, vena=False)
        hoja(d, (26, 46), (42, 39), 3, curva=2, vena=False)
        trampa(d, 24, cy, abertura, "feliz")
    else:
        cy = (26 if estado == "riesgo" else 31) + (cuadro % 2)
        inf = cy + 9
        sup = cy - 13
        desv = 0 if estado == "riesgo" else 4
        tallo(d, curva_q((24, 47), (24 - desv, 42), (24, inf - 2)), 5)
        hoja(d, (23, 46), (4, 40), 3, curva=-2, vena=False)
        hoja(d, (25, 46), (44, 40), 3, curva=2, vena=False)
        trampa(d, 24, cy, 0, estado)
    matera(d, None, banda=True)
    return im


# ---------------------------------------------------------------- animacion de exito (64x64)

def matera_exito(d):
    d.rectangle((16, 50, 47, 53), fill=1)
    d.line((17, 54, 46, 54), fill=0)
    d.polygon([(19, 55), (44, 55), (41, 63), (22, 63)], fill=1)
    for x in range(24, 41, 4):
        d.point((x, 59), fill=0)


def planta_exito(d, nivel, color=1, ox=0):
    """nivel 1 brote, 2 dos hojas, 3 planta completa."""
    c = color
    if nivel == 1:
        d.line((32 + ox, 49, 32 + ox, 44), fill=c, width=2)
        d.point((31 + ox, 43), fill=c); d.point((33 + ox, 43), fill=c)
    elif nivel == 2:
        d.line((32 + ox, 49, 32 + ox, 38), fill=c, width=2)
        hoja_c(d, (32 + ox, 41), (22 + ox, 33), 3, c)
        hoja_c(d, (32 + ox, 39), (43 + ox, 31), 3, c)
    else:
        d.line((32 + ox, 49, 32 + ox, 24), fill=c, width=3)
        hoja_c(d, (32 + ox, 28), (32 + ox, 6), 6, c)
        hoja_c(d, (32 + ox, 40), (11 + ox, 22), 5, c)
        hoja_c(d, (32 + ox, 36), (53 + ox, 19), 5, c)
        hoja_c(d, (32 + ox, 47), (14 + ox, 40), 3, c)
        hoja_c(d, (32 + ox, 47), (50 + ox, 39), 3, c)


def hoja_c(d, base, tip, ancho, c):
    """hoja() pero con color y sin vena."""
    hoja_img = Image.new("1", (64, 64), 0)
    hd = ImageDraw.Draw(hoja_img)
    hoja(hd, base, tip, ancho, vena=False)
    d._image.paste(c, (0, 0), hoja_img)


def estrella(d, x, y, r, c=1):
    d.line((x - r, y, x + r, y), fill=c); d.line((x, y - r, x, y + r), fill=c)
    d.point((x - r // 2, y - r // 2), fill=c); d.point((x + r // 2, y - r // 2), fill=c)
    d.point((x - r // 2, y + r // 2), fill=c); d.point((x + r // 2, y + r // 2), fill=c)


def visto(d, parcial=1.0, c=0, g=5):
    pts = [(17, 33), (28, 44), (47, 21)]
    if parcial < 1.0:
        pts = [(17, 33), (17 + 11 * parcial, 33 + 11 * parcial)]
    d.line(pts, fill=c, width=g, joint="curve")


def exito(i):
    im, d = lienzo(64, 64)
    if i == 0:
        matera_exito(d)
        d.ellipse((29, 46, 35, 50), fill=1)
        d.point((32, 47), fill=0)
    elif i in (1, 2, 3):
        matera_exito(d)
        planta_exito(d, i)
    elif i == 4:
        matera_exito(d)
        planta_exito(d, 3)
        for a in (0, 45, 135, 180, 225, 315):
            r1, r2 = 24, 31
            x1, y1 = 32 + r1 * math.cos(math.radians(a)), 30 + r1 * math.sin(math.radians(a))
            x2, y2 = 32 + r2 * math.cos(math.radians(a)), 30 + r2 * math.sin(math.radians(a))
            d.line((x1, y1, x2, y2), fill=1)
        estrella(d, 7, 8, 4); estrella(d, 57, 10, 3); estrella(d, 6, 40, 3); estrella(d, 58, 38, 4)
    elif i == 5:
        d.ellipse((2, 2, 61, 61), fill=1)
        im2 = Image.new("1", (64, 64), 0)
        planta_exito(ImageDraw.Draw(im2), 3)
        d.line((32, 49, 32, 40), fill=0, width=3)
        for y in range(64):
            for x in range(64):
                if im2.getpixel((x, y)) and y < 50:
                    d.point((x, y), fill=0)
        d.rectangle((14, 50, 49, 63), fill=0)
        d.ellipse((2, 2, 61, 61), outline=1, width=1)
    elif i == 6:
        d.ellipse((2, 2, 61, 61), outline=1, width=3)
        visto(d, 0.5, c=1)
        d.point((32, 6)); estrella(d, 8, 8, 3)
    else:
        d.ellipse((2, 2, 61, 61), outline=1, width=3)
        visto(d, 1.0, c=1)
        estrella(d, 6, 56, 3); estrella(d, 58, 6, 3)
    return im


def main():
    os.makedirs(ARTE, exist_ok=True)
    for estado in ("saludable", "riesgo", "critico"):
        for c in (0, 1):
            guardar(f"planta_{estado}_{c}", planta(estado, c))
    guardar("carni_abierta_0", carnivora("feliz", 0, 14))
    guardar("carni_abierta_1", carnivora("feliz", 1, 14))
    guardar("carni_media", carnivora("feliz", 0, 7))
    guardar("carni_cerrada", carnivora("feliz", 0, 0))
    for estado, nombre in (("riesgo", "riesgo"), ("critico", "critico")):
        for c in (0, 1):
            guardar(f"carni_{nombre}_{c}", carnivora(estado, c))
    for i in range(8):
        guardar(f"exito_{i}", exito(i))


if __name__ == "__main__":
    main()
