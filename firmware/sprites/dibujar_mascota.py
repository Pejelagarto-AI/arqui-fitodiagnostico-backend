#!/usr/bin/env python3
"""Escribe en arte/*.txt los dibujos de la mascota completa (fase 14): efectos de los eventos (rayos, corazon, destellos,
estrella), ojos en "x" y los accesorios. Son pocos pixeles: se arman con rejillas de texto y unas formas simples. Los .txt
(`#` encendido, `.` transparente) son la fuente de verdad y se pueden retocar a mano; despues:

    python3 firmware/sprites/dibujar_mascota.py && python3 firmware/sprites/generar.py

(este script NO toca los demas .txt de arte/, a diferencia de dibujar.py)."""
import os

AQUI = os.path.dirname(os.path.abspath(__file__))
ARTE = os.path.join(AQUI, "arte")


def rejilla(w, h):
    return [["." for _ in range(w)] for _ in range(h)]


def punto(g, x, y):
    if 0 <= y < len(g) and 0 <= x < len(g[0]):
        g[y][x] = "#"


def mas(g, cx, cy, r):
    """Destello de cuatro puntas con brazos de `r` px."""
    for d in range(-r, r + 1):
        punto(g, cx + d, cy)
        punto(g, cx, cy + d)


def guardar(nombre, filas):
    filas = ["".join(f) if not isinstance(f, str) else f for f in filas]
    assert len({len(f) for f in filas}) == 1, nombre
    with open(os.path.join(ARTE, nombre + ".txt"), "w") as f:
        f.write("\n".join(filas) + "\n")


def disco_rayos(g):
    for y in range(4, 7):
        for x in range(4, 7):
            punto(g, x, y)


def rayos():
    """SALIO_AL_SOL, 11x11: un solcito que gira sus rayos (rectos, diagonales, ambos)."""
    for i in range(3):
        g = rejilla(11, 11)
        disco_rayos(g)
        if i in (0, 2):
            largo = (0, 1) if i == 0 else (0, 1, 2)
            for k in largo:
                punto(g, 5, k); punto(g, 5, 10 - k); punto(g, k, 5); punto(g, 10 - k, 5)
        if i in (1, 2):
            for k in ((1, 1), (2, 2)) if i == 1 else ((2, 2),):
                x, y = k
                for sx, sy in ((x, y), (10 - x, y), (x, 10 - y), (10 - x, 10 - y)):
                    punto(g, sx, sy)
        guardar(f"rayos_{i}", g)


def corazon():
    """ACARICIADA, 9x8: corazon chico (cuadro 0) y grande (cuadro 1): el latido."""
    guardar("corazon_0", ["........." , ".........", "..##.##..", ".#######.", ".#######.", "..#####..", "...###...", "....#...."])
    guardar("corazon_1", [".##...##.", "####.####", "#########", "#########", ".#######.", "..#####..", "...###...", "....#...."])


def destellos():
    """REVIVIO, 11x11: destellos de cuatro puntas que aparecen y cambian de lugar."""
    cuadros = [((3, 3, 2), (8, 7, 1)), ((3, 3, 1), (8, 5, 2), (4, 9, 1)), ((7, 2, 1), (3, 7, 2), (9, 9, 1))]
    for i, destellos_ in enumerate(cuadros):
        g = rejilla(11, 11)
        for cx, cy, r in destellos_:
            mas(g, cx, cy, r)
        guardar(f"destellos_{i}", g)


ESTRELLA_GRANDE = ["....#....", "....#....", "...###...", "#########", ".#######.", "..#####..", "..#####..", ".###.###.", ".##...##.", ".#.....#."]


def estrella():
    """RACHA, 11x11: estrella de cinco puntas chica, grande y brillante (hueca)."""
    chica = rejilla(11, 11)
    for y, f in enumerate([".#.", "###", ".#.", "#.#"]):
        for x, c in enumerate(f):
            if c == "#":
                punto(chica, 4 + x, 3 + y)
    guardar("estrella_0", chica)
    grande = rejilla(11, 11)
    for y, f in enumerate(ESTRELLA_GRANDE):
        for x, c in enumerate(f):
            if c == "#":
                punto(grande, 1 + x, y)
    guardar("estrella_1", grande)
    hueca = [fila[:] for fila in grande]
    for y in range(1, 10):
        for x in range(1, 10):
            if grande[y][x] == "#" and all(grande[y + dy][x + dx] == "#" for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                hueca[y][x] = "."
    guardar("estrella_2", hueca)


def ojos_x():
    guardar("ojo_x_5", ["#...#", ".#.#.", "..#..", ".#.#.", "#...#"])
    guardar("ojo_x_3", ["#.#", ".#.", "#.#"])


def accesorios():
    guardar("accesorio_sombrero", ["......####......", ".....######.....", ".....######.....", ".....######.....",
                                   ".....#....#.....", "..############..", "################", ".##############."])
    guardar("accesorio_mono", ["##......##", "###....###", "####..####", "##########", "##########", "####..####", "###....###", "##......##"])
    lente = ["..####..", ".######.", "##....##", "##....##", "##....##", ".######.", "..####.."]
    chica = [".####.", "######", "##..##", "##..##", "######", ".####."]
    # gafas 14x6: dos lentes de 6x6 con aro de 2 px (el centro queda apagado y la aureola negra lo rellena) y un puente
    guardar("accesorio_gafas", [chica[y] + ("##" if y == 2 else "..") + chica[y] for y in range(6)])
    # gafas anchas 24x7, solo para la carnivora (sus ojos estan a 18 px uno del otro)
    guardar("accesorio_gafas_ancha", [lente[y] + ("########" if y == 3 else "........") + lente[y] for y in range(7)])


if __name__ == "__main__":
    rayos()
    corazon()
    destellos()
    estrella()
    ojos_x()
    accesorios()
    print("arte de la mascota completa escrito")
