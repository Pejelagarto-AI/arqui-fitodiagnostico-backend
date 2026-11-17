#!/usr/bin/env python3
"""Convierte sprites/frases.json (la fuente de verdad de las frases, compartida con el front) en src/frases.h:
tablas PROGMEM por familia x animo y familia x evento. Falla si falta una frase, si pasa de 13 caracteres o si no es
ASCII imprimible. Uso:  python3 firmware/sprites/generar_frases.py"""
import json
import os
import sys

AQUI = os.path.dirname(os.path.abspath(__file__))
ENTRADA = os.path.join(AQUI, "frases.json")
SALIDA = os.path.join(AQUI, "..", "src", "frases.h")

MAXIMO = 13
# Mismo orden que la enum Familia de pantalla.cpp (Comun, Carnivora, Girasol, Cactus, Suculenta, Helecho).
FAMILIAS = ["generica", "carnivora", "girasol", "cactus", "suculenta", "helecho"]
# Mismo orden que FITO_ANIMO_* y FITO_EV_* de nucleo/fito_mascota.h.
ANIMOS = ["FELIZ", "CANSADA", "SEDIENTA", "AHOGADA", "CON_FRIO", "CON_CALOR", "A_OSCURAS", "DESLUMBRADA",
          "DESMAYADA", "DORMIDA", "SIN_DATOS"]
EVENTOS = ["REGADA", "SALIO_AL_SOL", "DESMAYO", "REVIVIO", "ACARICIADA", "RACHA", "RIEGO_DECLARADO",
           "RIEGO_CONFIRMADO", "RIEGO_DESMENTIDO"]
SIN_FRASE = {"RIEGO_DECLARADO"}          # la OLED no lo muestra (D192): queda la cadena vacia


def validar(frase, donde):
    if not frase.isascii() or not all(32 <= ord(c) < 127 for c in frase):
        sys.exit(f"{donde}: '{frase}' no es ASCII imprimible")
    if len(frase) > MAXIMO:
        sys.exit(f"{donde}: '{frase}' tiene {len(frase)} caracteres (maximo {MAXIMO})")


def tabla(datos, seccion, claves, opcionales=()):
    filas = []
    for fam in FAMILIAS:
        if fam not in datos[seccion]:
            sys.exit(f"{seccion}: falta la familia {fam}")
        fila = []
        for k in claves:
            frase = datos[seccion][fam].get(k)
            if frase is None:
                if k in opcionales:
                    frase = ""
                else:
                    sys.exit(f"{seccion}.{fam}: falta {k}")
            validar(frase, f"{seccion}.{fam}.{k}")
            fila.append(frase)
        filas.append(fila)
    return filas


def contenido(datos):
    """El texto de frases.h para el contenido de frases.json (ya cargado). Falla (SystemExit) si algo no cumple."""
    if datos.get("maximo") != MAXIMO:
        sys.exit(f"frases.json: 'maximo' debe ser {MAXIMO}")
    animos = tabla(datos, "animo", ANIMOS)
    eventos = tabla(datos, "evento", EVENTOS, SIN_FRASE)
    for sec, claves in (("animo", ANIMOS), ("evento", EVENTOS)):
        for fam, d in datos[sec].items():
            if fam not in FAMILIAS:
                sys.exit(f"{sec}: familia desconocida {fam}")
            sobran = set(d) - set(claves)
            if sobran:
                sys.exit(f"{sec}.{fam}: claves desconocidas {sorted(sobran)}")
    L = ["// GENERADO por sprites/generar_frases.py desde sprites/frases.json. No editar a mano.",
         "// Familias en el orden de la enum Familia de pantalla.cpp: " + ", ".join(FAMILIAS) + " (la primera es la comun).",
         "#pragma once", "#include <Arduino.h>", "", "namespace frases {",
         f"constexpr int MAXIMO = {MAXIMO};",
         f"constexpr int N_FAMILIAS = {len(FAMILIAS)}, N_ANIMOS = {len(ANIMOS)}, N_EVENTOS = {len(EVENTOS)};",
         "// Cada frase ocupa MAXIMO + 1 bytes (con el NUL). Se leen con memcpy_P."]
    for nombre, filas, claves in (("ANIMO", animos, ANIMOS), ("EVENTO", eventos, EVENTOS)):
        L.append(f"const char {nombre}[N_FAMILIAS][N_{nombre}S][MAXIMO + 1] PROGMEM = {{")
        for fam, fila in zip(FAMILIAS, filas):
            L.append(f"    {{  // {fam}")
            for k, frase in zip(claves, fila):
                L.append(f'        "{frase}",  // {k}')
            L.append("    },")
        L.append("};")
    L.append("}")
    return "\n".join(L) + "\n"


def main():
    datos = json.load(open(ENTRADA, encoding="utf-8"))
    open(SALIDA, "w", encoding="utf-8").write(contenido(datos))
    print("frases.h escrito (%d animos, %d eventos, %d familias)" % (len(ANIMOS), len(EVENTOS), len(FAMILIAS)))


if __name__ == "__main__":
    main()
