# Genera include/config_local.h desde la seccion [fito] de platformio.ini / local.ini.
# Asi las cadenas (SSID con espacios, claves con comillas, barras o signos) llegan al codigo bien escapadas,
# sin pasar por la linea de comandos del compilador.
Import("env")  # noqa: F821  (lo inyecta PlatformIO)
import os

config = env.GetProjectConfig()  # noqa: F821
seccion = "fito"


def valor(clave, defecto=""):
    return config.get(seccion, clave, defecto) if config.has_option(seccion, clave) else defecto


def literal(texto):
    escapado = texto.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\r", "")
    return '"%s"' % escapado


def entero(clave, defecto):
    try:
        return int(str(valor(clave, str(defecto))).strip())
    except ValueError:
        raise SystemExit("[fito] %s debe ser un numero entero" % clave)


sensores = valor("sensores", "simulados").strip().lower() or "simulados"
if sensores not in ("simulados", "potenciometros"):
    raise SystemExit("[fito] sensores debe ser simulados o potenciometros (dice %r)" % sensores)

lineas = [
    "// GENERADO por extra_script.py desde la seccion [fito] (platformio.ini y local.ini). No editar ni versionar.",
    "#pragma once",
    "#define FITO_CLAVE_FLOTA %s" % literal(valor("clave_flota").strip()),
    "#define FITO_WIFI_SSID %s" % literal(valor("wifi_ssid").strip("\r\n")),
    "#define FITO_WIFI_CLAVE %s" % literal(valor("wifi_clave").strip("\r\n")),
    "#define FITO_SERVIDOR %s" % literal(valor("servidor").strip()),
    "#define FITO_PUERTO_API %d" % entero("puerto_api", 8080),
    "#define FITO_PUERTO_FRONT %d" % entero("puerto_front", 5500),
    "#define FITO_SENSORES_POTENCIOMETROS %d" % (1 if sensores == "potenciometros" else 0),
    "",
]
carpeta = os.path.join(env.subst("$PROJECT_DIR"), "include")  # noqa: F821
os.makedirs(carpeta, exist_ok=True)
ruta = os.path.join(carpeta, "config_local.h")
nuevo = "\n".join(lineas)
try:
    actual = open(ruta, encoding="utf-8").read()
except OSError:
    actual = None
if actual != nuevo:  # no tocar el archivo si no cambio: evita recompilar todo
    with open(ruta, "w", encoding="utf-8") as f:
        f.write(nuevo)
