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
import json
import math
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
           os.path.join(RAIZ, "firmware", "src", "nucleo", "fito_nucleo.c"),
           os.path.join(RAIZ, "firmware", "src", "nucleo", "fito_mascota.c"), "-o", destino]
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

        # 7. Salud de la matera (fase 10): cuando reportar que un sensor falla. El C y la MemoriaDeSalud del simulador
        # deben decidir igual, paso a paso, sobre los mismos guiones. Cada paso es "ms:estado:resultado" con estado de
        # tres letras (humedad, luz, temperatura; f = falla, o = ok) y resultado ok (el servidor confirmo) o fallo.
        def guion_python(pasos):
            m = sim.MemoriaDeSalud()
            salida = []
            for ms, estado, resultado in pasos:
                actual = tuple(ch == "f" for ch in estado)
                toca = m.toca(actual, ms)
                salida.append(int(toca))
                if toca and resultado:
                    m.intento(ms)
                    if resultado == "ok":
                        m.confirmado(actual, ms)
            return " ".join(map(str, salida))

        def guion_c(pasos):
            return c("salud", *["%d:%s%s" % (ms, estado, ":" + res if res else "") for ms, estado, res in pasos])

        LATIDO = 300000
        guiones = {
            "sin fallas nunca reporta": [(0, "ooo", "ok"), (5000, "ooo", "ok"), (LATIDO * 3, "ooo", "ok")],
            "falla nueva: reporta una vez y espera": [(0, "ooo", "ok"), (5000, "oof", "ok"), (10000, "oof", "ok"),
                                                      (20000, "oof", "ok")],
            "latido a los 5 min, no antes": [(0, "oof", "ok"), (LATIDO - 1, "oof", "ok"), (LATIDO, "oof", "ok"),
                                             (LATIDO + 5000, "oof", "ok"), (2 * LATIDO, "oof", "ok")],
            "recuperacion reporta OK y luego calla": [(0, "oof", "ok"), (60000, "ooo", "ok"), (65000, "ooo", "ok"),
                                                      (LATIDO * 2, "ooo", "ok")],
            "recuperacion antes de 10 s espera al freno": [(0, "oof", "ok"), (5000, "ooo", "ok"),
                                                           (10000, "ooo", "ok"), (10001, "ooo", "ok")],
            "un envio fallido se reintenta cada 10 s": [(0, "oof", "fallo"), (5000, "oof", "fallo"),
                                                        (10000, "oof", "fallo"), (20000, "oof", "ok"),
                                                        (30000, "oof", "ok")],
            "un latido fallido se reintenta cada 10 s": [(0, "oof", "ok"), (LATIDO, "oof", "fallo"),
                                                         (LATIDO + 5000, "oof", "fallo"), (LATIDO + 10000, "oof", "ok"),
                                                         (LATIDO + 20000, "oof", "ok")],
            "cambia de uno a dos sensores": [(0, "oof", "ok"), (20000, "fof", "ok"), (40000, "fof", "ok")],
            "parpadeo: no inunda": [(0, "oof", "ok"), (1000, "ooo", "ok"), (2000, "oof", "ok"), (11000, "ooo", "ok")],
            "desborde de millis (uint32)": [(4294967000, "oof", "ok"), (4294967000 + 6000 - 2 ** 32, "oof", "ok"),
                                            (4294967000 + LATIDO - 2 ** 32, "oof", "ok")],
        }
        for nombre, pasos in guiones.items():
            comprobar("salud: " + nombre, guion_python(pasos), guion_c(pasos))
        # Y los valores que importan, para que el acuerdo C = Python no sea un acuerdo sobre algo equivocado.
        comprobar("salud: primera falla reporta", "0 1 0 0", guion_c(guiones["falla nueva: reporta una vez y espera"]))
        comprobar("salud: latidos", "1 0 1 0 1", guion_c(guiones["latido a los 5 min, no antes"]))
        comprobar("salud: recuperacion", "1 1 0 0", guion_c(guiones["recuperacion reporta OK y luego calla"]))
        azar_salud = random.Random(11)
        for i in range(40):
            t, pasos = 0, []
            for _ in range(60):
                t += azar_salud.choice([500, 2000, 5000, 9000, 11000, 60000, LATIDO])
                pasos.append((t, "".join(azar_salud.choice("ofo") for _ in range(3)),
                              azar_salud.choice(["ok", "ok", "fallo", ""])))
            comprobar("salud: guion al azar %d" % i, guion_python(pasos), guion_c(pasos))

        # 8. "hace X" de la OLED (la hora de la ultima lectura buena).
        for segundos, esperado in ((-5, "hace 0 s"), (0, "hace 0 s"), (59, "hace 59 s"), (60, "hace 1 min"),
                                   (754, "hace 12 min"), (3599, "hace 59 min"), (3600, "hace 1 h"),
                                   (7300, "hace 2 h")):
            comprobar("hace_cuanto(%d)" % segundos, esperado, c("hace", str(segundos)))

        # 9. La mascota duerme y saluda (fase 11). Los numeros son los del bloque SALUDO del front (F47) y los de la
        # planta dormida que el front calca (mascota-tiempo.js): si cambian aqui, cambian alla.
        DY = [-2, -4, -5, -5, -4, -2, 0, 1, 0, 0, 0, 0]
        OJOS = [1.0, 1.0, 0.5, 0.0, 0.5, 1.0, 0.5, 0.0, 0.5, 1.0, 1.0, 1.0]
        for k in range(12):
            for t in (50 * k, 50 * k + 25, 50 * k + 49):
                comprobar("saludo t=%d" % t, "%d %d %.1f" % (k, DY[k], OJOS[k]), c("saludo", str(t)))
        comprobar("saludo termina a los 600 ms", "-1", c("saludo", "600"))
        comprobar("saludo: sube 5 px como maximo", "-5", str(min(DY)))
        for t, esperado in ((0, 0), (749, 0), (750, -1), (1500, -1), (2249, -1), (2250, 0), (2999, 0), (3000, 0),
                            (3750, -1), (4294967295, 0)):
            comprobar("respira t=%d" % t, str(esperado), c("respira", str(t)))
        # La regla entera coincide con lround(0.5 - 0.5 cos) salvo en el borde exacto.
        for t in range(0, 3000, 10):
            if t in (750, 2250):
                continue
            x = 0.5 - 0.5 * math.cos(2 * math.pi * t / 3000)
            comprobar("respira = lround(0.5-0.5cos) en %d" % t, str(-int(x + 0.5)), c("respira", str(t)))
        for t, esperado in ((0, 0), (999, 0), (1000, 1), (2500, 2), (3000, 0), (4294967295, 2)):
            comprobar("zzz t=%d" % t, str(esperado), c("zzz", str(t)))
        for ms, esperado in ((0, 0), (29, 0), (30, 1), (400, 1), (999, 1), (1000, 0), (5000, 0)):
            comprobar("pulsacion corta %d ms" % ms, str(esperado), c("pulsacion", str(ms), "1000"))

        # 10. La mascota completa (fase 14). Los numeros son los que el front calca (docs/fase14/IMPLEMENTACION.md).
        # 10a. Textos del servidor a numeros (el orden es el de frases.h y el de los FITO_* del nucleo).
        ANIMOS = ["FELIZ", "CANSADA", "SEDIENTA", "AHOGADA", "CON_FRIO", "CON_CALOR", "A_OSCURAS", "DESLUMBRADA",
                  "DESMAYADA", "DORMIDA", "SIN_DATOS"]
        ACCESORIOS = ["NINGUNO", "SOMBRERO", "MONO", "GAFAS"]
        EVENTOS = ["REGADA", "SALIO_AL_SOL", "DESMAYO", "REVIVIO", "ACARICIADA", "RACHA", "RIEGO_DECLARADO",
                   "RIEGO_CONFIRMADO", "RIEGO_DESMENTIDO"]
        for kind, lista in (("animo", ANIMOS), ("accesorio", ACCESORIOS), ("evento", EVENTOS)):
            for i, nombre in enumerate(lista):
                comprobar("%s %s" % (kind, nombre), str(i), c("texto", kind, nombre))
            for malo in ("", "feliz", "VOLANDO", "FELIZ "):
                comprobar("%s desconocido '%s'" % (kind, malo), "-1", c("texto", kind, malo))

        # 10b. La cola de eventos: guiones escritos a mano. Cada paso imprime "tipo:ms/fila" (fila 0 estado, 1 frase del
        # animo, 2 frase del evento); "-:0/f" si no suena ninguno. Al final, "descartados" y "esperando".
        def cola(hay, pasos, base=0):
            return c("cola", str(hay), str(base), *pasos)

        def lista_tipos(tipos):
            return ",".join(map(str, tipos))

        comprobar("cola: sin eventos alterna cada 3 s", "-:0/0 -:0/0 -:0/1 -:0/1 -:0/0 -:0/1 descartados=0 esperando=0",
                  cola(1, ["0", "2999", "3000", "5999", "6000", "9000"]))
        comprobar("cola: sin mascota siempre el estado", "-:0/0 -:0/0 -:0/0 descartados=0 esperando=0",
                  cola(0, ["0", "3000", "9000"]))
        comprobar("cola: un evento y vuelve al ciclo (empieza por el estado)",
                  "4:0/2 4:2999/2 -:0/0 -:0/0 -:0/1 descartados=0 esperando=0",
                  cola(1, ["1000+4", "3999", "4000", "6999", "7000"]))
        comprobar("cola: tres eventos de a uno en orden",
                  "0:0/2 0:2999/2 1:0/2 1:2999/2 4:0/2 4:2999/2 -:0/0 -:0/1 descartados=0 esperando=0",
                  cola(1, ["0+0,1,4", "2999", "3000", "5999", "6000", "8999", "9000", "12000"]))
        comprobar("cola: RIEGO_DECLARADO y tipos desconocidos se ignoran", "-:0/0 -:0/0 descartados=0 esperando=0",
                  cola(1, ["0+6,-1,9,42", "100"]))
        comprobar("cola: lo ignorado no estorba a lo valido", "7:0/2 8:0/2 descartados=0 esperando=0",
                  cola(1, ["0+6,7,6,-1,8", "3000"]))
        comprobar("cola: un bucle lento no recupera tiempo", "0:0/2 1:0/2 1:2999/2 -:0/0 descartados=0 esperando=0",
                  cola(1, ["0+0,1", "10000", "12999", "13000"]))

        # diez de golpe: suenan los diez en orden, 3 s cada uno (30 s), sin perder ninguno
        diez = [0, 1, 2, 3, 4, 5, 7, 8, 0, 1]
        pasos = ["0+" + lista_tipos(diez)]
        for k in range(10):
            pasos += [str(3000 * k), str(3000 * k + 2999)]
        pasos += ["30000", "33000"]
        salida = cola(1, pasos).split()
        # salida[0] es el paso "0+..." (ya arranca el primero); luego cada par es (inicio, ultimo ms) de cada evento
        sonaron = [salida[1 + 2 * k].split(":")[0] for k in range(10)]
        comprobar("cola: diez de golpe suenan en orden", " ".join(map(str, diez)), " ".join(sonaron))
        comprobar("cola: diez de golpe, el ultimo suena hasta los 29999 ms", "1:2999/2", salida[-5])
        comprobar("cola: despues de los diez vuelve al estado y luego a la frase", "-:0/0 -:0/1", " ".join(salida[-4:-2]))
        comprobar("cola: diez de golpe, nada se pierde", "descartados=0 esperando=0", " ".join(salida[-2:]))

        # once de golpe: se pierde el mas antiguo de los que esperan
        once = [0, 1, 2, 3, 4, 5, 7, 8, 0, 1, 2]
        salida = cola(1, ["0+" + lista_tipos(once)]).split()
        comprobar("cola: once de golpe, suena el segundo (el primero se perdio)", "1:0/2", salida[0])
        comprobar("cola: once de golpe, cuenta el descarte", "descartados=1 esperando=9", " ".join(salida[1:]))
        # llegan mientras suena otro: el que suena no se corta
        salida = cola(1, ["0+5", "100+" + lista_tipos(diez)]).split()
        comprobar("cola: el que suena no se corta y los diez esperan", "5:0/2 5:100/2 descartados=0 esperando=10",
                  " ".join(salida))
        salida = cola(1, ["0+5", "100+" + lista_tipos(once)]).split()
        comprobar("cola: once mientras suena uno, cae el mas antiguo de los que esperan",
                  "5:100/2 descartados=1 esperando=10", " ".join(salida[1:]))
        # desborde de millis(): el mismo guion a 400 ms del final de uint32
        guion = ["0+4,1", "2999", "3000", "5999", "6000", "9000"]
        comprobar("cola: desborde de millis() (la salida es igual)", cola(1, guion), cola(1, guion, base=2 ** 32 - 400))

        # 10c. Contra un modelo independiente en Python (lista en vez de anillo), con guiones al azar.
        class ModeloCola:
            def __init__(self, ahora):
                self.espera, self.activo, self.desde, self.ciclo, self.desc = [], None, 0, ahora, 0

            def empujar(self, t):
                if t < 0 or t > 8 or t == 6:
                    return
                if len(self.espera) == 10:
                    self.espera.pop(0)
                    self.desc += 1
                self.espera.append(t)

            def avanzar(self, ahora):
                if self.activo is not None and (ahora - self.desde) & 0xFFFFFFFF >= 3000:
                    self.activo, self.ciclo = None, ahora
                if self.activo is None and self.espera:
                    self.activo, self.desde = self.espera.pop(0), ahora
                return self.activo

            def fila(self, ahora, hay):
                if self.activo is not None:
                    return 2
                return ((ahora - self.ciclo) & 0xFFFFFFFF) // 3000 % 2 if hay else 0

        def modelo(hay, pasos, base):
            m, salida = ModeloCola(base), []
            for ms, tipos in pasos:
                ahora = (base + ms) & 0xFFFFFFFF
                for t in tipos:
                    m.empujar(t)
                a = m.avanzar(ahora)
                salida.append("%d:%d/%d" % (a, (ahora - m.desde) & 0xFFFFFFFF, m.fila(ahora, hay)) if a is not None
                              else "-:0/%d" % m.fila(ahora, hay))
            return " ".join(salida) + " descartados=%d esperando=%d" % (m.desc, len(m.espera))

        azar_ev = random.Random(14)
        for i in range(60):
            base = azar_ev.choice([0, 1000, 2 ** 32 - 5000, 2 ** 32 - 1])
            hay = azar_ev.choice([0, 1, 1])
            t, pasos = 0, []
            for _ in range(40):
                t += azar_ev.choice([0, 16, 50, 500, 2999, 3000, 3001, 7000])
                tipos = [azar_ev.choice([-1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9])
                         for _ in range(azar_ev.choice([0, 0, 0, 1, 2, 4, 11]))]
                pasos.append((t, tipos))
            texto = [str(ms) + ("+" + lista_tipos(tipos) if tipos else "") for ms, tipos in pasos]
            comprobar("cola: guion al azar %d" % i, modelo(hay, pasos, base), cola(hay, texto, base))

        # 10d. Cuadros de las animaciones: ms por cuadro, cuadros por vuelta y vueltas.
        for efecto, ms, cuadros, vueltas in (("rayos", 250, 3, 2), ("corazon", 300, 2, 3), ("destellos", 200, 3, 2),
                                             ("estrella", 300, 3, 2)):
            duracion = ms * cuadros * vueltas
            for t in list(range(0, duracion, 50)) + [duracion - 1, duracion, duracion + 1, duracion * 3, 4294967295]:
                esperado = (t // ms) % cuadros if t < duracion else -1
                comprobar("%s t=%d" % (efecto, t), str(esperado), c("cuadro", efecto, str(t)))
        comprobar("duraciones (rayos, corazon, destellos, estrella)", "1500 1800 1200 1800",
                  " ".join(c("animacion", str(t)) for t in (1, 4, 3, 5)))
        for t, esperado in ((0, 0), (99, 0), (100, 0), (199, 0), (200, 1), (399, 1), (400, 2), (599, 2), (600, 2), (5000, 2)):
            comprobar("desmayo t=%d" % t, str(esperado), c("desmayo", str(t)))
        comprobar("desmayo dura 600 ms", "600", c("animacion", "2"))
        for t in range(9):
            comprobar("animacion del evento %d cabe en su turno de 3000 ms" % t, "True",
                      str(0 <= int(c("animacion", str(t))) <= 3000))
        for t in (0, 6, 7, 8):
            comprobar("evento %d sin animacion propia (no despierta)" % t, "0", c("animacion", str(t)))
        for vit, ancho, esperado in ((0, 78, 0), (100, 78, 78), (50, 78, 39), (64, 78, 50), (1, 78, 1), (-5, 78, 0),
                                     (150, 78, 78), (3, 78, 2), (99, 78, 77)):
            comprobar("barra vitalidad %d de %d" % (vit, ancho), str(esperado), c("barra", str(vit), str(ancho)))
        for vit in range(0, 101, 7):
            comprobar("barra %d = round(v*78/100)" % vit, str(int(vit * 78 / 100 + 0.5)), c("barra", str(vit), "78"))

        # 10e. Las frases: frases.h sale de frases.json y el generador rechaza lo que no cumple.
        sys.path.insert(0, os.path.join(RAIZ, "firmware", "sprites"))
        import copy
        import json
        import generar_frases as gf  # noqa: E402
        datos = json.load(open(os.path.join(RAIZ, "firmware", "sprites", "frases.json"), encoding="utf-8"))
        actual = open(os.path.join(RAIZ, "firmware", "src", "frases.h"), encoding="utf-8").read()
        comprobar("frases.h esta al dia con frases.json", "True", str(gf.contenido(datos) == actual))
        todas = [f for sec in ("animo", "evento") for fam in datos[sec].values() for f in fam.values()]
        comprobar("todas las frases miden 13 o menos y son ASCII", "True",
                  str(all(len(f) <= 13 and f.isascii() for f in todas)))
        comprobar("66 frases de animo (6 familias x 11)", "66", str(sum(len(v) for v in datos["animo"].values())))
        for motivo, cambio in (("14 caracteres", lambda d: d["animo"]["girasol"].__setitem__("FELIZ", "x" * 14)),
                               ("no ASCII", lambda d: d["animo"]["girasol"].__setitem__("FELIZ", "todo béllo")),
                               ("falta un animo", lambda d: d["animo"]["cactus"].pop("CANSADA")),
                               ("falta un evento", lambda d: d["evento"]["helecho"].pop("RACHA")),
                               ("falta una familia", lambda d: d["animo"].pop("helecho")),
                               ("familia desconocida", lambda d: d["animo"].__setitem__("rosa", {})),
                               ("animo desconocido", lambda d: d["animo"]["cactus"].__setitem__("ENOJADA", "hmm")),
                               ("maximo cambiado", lambda d: d.__setitem__("maximo", 20))):
            malo = copy.deepcopy(datos)
            cambio(malo)
            try:
                gf.contenido(malo)
                obtenido = "aceptado"
            except SystemExit:
                obtenido = "rechazado"
            comprobar("generar_frases rechaza: %s" % motivo, "rechazado", obtenido)
        sin_declarado = copy.deepcopy(datos)
        sin_declarado["evento"]["cactus"].pop("RIEGO_DECLARADO", None)
        comprobar("generar_frases: RIEGO_DECLARADO puede faltar", "True", str(gf.contenido(sin_declarado) is not None))

        # 11. El simulador (fase 14): la linea de la mascota en la 201 y la caricia de dispositivo.
        cuerpo201 = {"id": "abcdef0123456789", "diagnostico": {"estado": "SALUDABLE"},
                     "mascota": {"vitalidad": 64, "animo": "FELIZ", "racha": 3, "accesorio": "SOMBRERO",
                                 "eventos": [{"tipo": "REGADA", "ocurridoEn": "2026-11-16T14:40:05Z"},
                                             {"tipo": "ACARICIADA", "ocurridoEn": "2026-11-16T14:41:00Z"}]}}
        comprobar("simulador: linea de la mascota",
                  " | mascota vitalidad=64 animo=FELIZ racha=3 accesorio=SOMBRERO eventos=[REGADA, ACARICIADA]",
                  sim.texto_mascota(cuerpo201))
        comprobar("simulador: sin eventos", " | mascota vitalidad=10 animo=DESMAYADA racha=0 accesorio=NINGUNO eventos=[]",
                  sim.texto_mascota({"mascota": {"vitalidad": 10, "animo": "DESMAYADA", "racha": 0,
                                                 "accesorio": "NINGUNO", "eventos": []}}))
        comprobar("simulador: servidor viejo sin mascota", "", sim.texto_mascota({"id": "x"}))
        comprobar("simulador: resumen de la 201 incluye la mascota",
                  "estado=SALUDABLE id=abcdef01" + sim.texto_mascota(cuerpo201), sim.resumen(201, json.dumps(cuerpo201)))
        comprobar("simulador: resumen de una 201 vieja sigue igual", "estado=SALUDABLE id=abcdef01",
                  sim.resumen(201, json.dumps({"id": "abcdef0123", "diagnostico": {"estado": "SALUDABLE"}})))

        class CredencialFalsa:
            def __init__(self, token):
                self.token, self.descartada = token, False

            def asegurar(self, esperar_red=True):
                return self.token

            def descartar(self):
                self.descartada = True

        for respuesta, esperado_descartar in ((201, False), (200, False), (401, True)):
            llamadas = []
            original = sim.enviar

            def falso(url, token, cuerpo, r=respuesta, registro=llamadas):
                registro.append((url, token, cuerpo))
                return r, "{}"

            sim.enviar = falso
            try:
                cred = CredencialFalsa("tok")
                codigo = sim.acariciar("http://x:8080", "fito-ABC234", cred)
            finally:
                sim.enviar = original
            comprobar("simulador: caricia HTTP %d" % respuesta, "%d True" % respuesta,
                      "%s %s" % (codigo, cred.descartada == esperado_descartar))
            comprobar("simulador: la caricia va a /mascota/acciones con el token y {tipo: ACARICIAR}",
                      "[('http://x:8080/api/v1/dispositivos/fito-ABC234/mascota/acciones', 'tok', '{\"tipo\": \"ACARICIAR\"}')]",
                      str(llamadas))
        comprobar("simulador: sin token no acaricia", "None", str(sim.acariciar("http://x", "fito-ABC234", CredencialFalsa(None))))
    print("\n%d comprobaciones, %d fallas" % (total, fallos))
    return 1 if fallos else 0


if __name__ == "__main__":
    sys.exit(main())
