"""Tablas de calibracion de local.ini (cal_humedad, cal_luz): "2900:0, 1200:100" -> puntos ordenados por mV.

Lo usa extra_script.py al compilar (un error aqui detiene la compilacion con el mensaje) y lo prueba
test/probar_nucleo.py. La interpolacion la hace fito_interpolar_cal en src/nucleo/, que exige mV ascendente.
"""

MAX_PUNTOS = 8      # FITO_MAX_PUNTOS_CAL en src/nucleo/fito_nucleo.h
MV_MAXIMO = 3300    # el ADC del ESP32 no pasa de 3.3 V


def leer_tabla(nombre, texto):
    """Devuelve [(mv, valor), ...] ordenada por mv. Lanza ValueError con un mensaje claro si no sirve."""
    puntos = []
    for trozo in texto.replace(";", ",").split(","):
        trozo = trozo.strip()
        if not trozo:
            continue
        if trozo.count(":") != 1:
            raise ValueError("%s: el punto %r debe ser mV:valor (por ejemplo 2900:0)" % (nombre, trozo))
        mv_txt, valor_txt = (p.strip() for p in trozo.split(":"))
        try:
            mv = int(mv_txt)
            valor = float(valor_txt)
        except ValueError:
            raise ValueError("%s: el punto %r debe ser un entero de mV y un numero (por ejemplo 2900:0)" % (nombre, trozo))
        if not 0 <= mv <= MV_MAXIMO:
            raise ValueError("%s: %d mV esta fuera de 0 a %d" % (nombre, mv, MV_MAXIMO))
        puntos.append((mv, valor))
    if len(puntos) < 2:
        raise ValueError("%s: hacen falta al menos dos puntos mV:valor (dice %r)" % (nombre, texto))
    if len(puntos) > MAX_PUNTOS:
        raise ValueError("%s: maximo %d puntos (tiene %d)" % (nombre, MAX_PUNTOS, len(puntos)))
    puntos.sort()
    for (a, _), (b, _) in zip(puntos, puntos[1:]):
        if a == b:
            raise ValueError("%s: el mV %d aparece dos veces" % (nombre, a))
    return puntos


def como_c(puntos):
    """Inicializador de C para un arreglo de fito_punto_cal: {{1200, 100.0f}, {2900, 0.0f}}"""
    return "{" + ", ".join("{%d, %sf}" % (mv, repr(float(v))) for mv, v in puntos) + "}"
