#!/usr/bin/env bash
# Demo en red local: backend + front accesibles desde el celular y desde la matera (ESP32).
#
#   scripts/demo-lan.sh                arranca backend (H2 en archivo en /tmp) y front
#   scripts/demo-lan.sh parar          apaga SOLO lo que este script arranco (por PID guardado)
#   scripts/demo-lan.sh --ip 10.0.0.5  fuerza la IP en vez de detectarla
#   --puerto-api N / --puerto-front N  puertos alternos (por defecto 8080 y 5500)
#
# La clave de flota es UNA sola: si firmware/local.ini tiene clave_flota, se usa esa; si no, la de .env.demo; si
# ninguna existe, se genera y se escribe en .env.demo y (si el archivo existe) en firmware/local.ini.
# Variables: FITO_FRONT_DIR (carpeta del front, por defecto ~/Documentos/fitodiagnostico-frontend), FITO_CORS_EXTRA (origenes
# adicionales permitidos, separados por coma; demo-https.sh pasa los suyos), FITO_RAIZ (raiz del repo si el script corre desde otro sitio).
set -euo pipefail

RAIZ="${FITO_RAIZ:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"   # FITO_RAIZ: usar otro checkout como raiz (jar, .env.demo, local.ini)
LOCAL_INI="$RAIZ/firmware/local.ini"
ENV_DEMO="$RAIZ/.env.demo"
FRONT_DIR="${FITO_FRONT_DIR:-$HOME/Documentos/fitodiagnostico-frontend}"
PUERTO_API="${FITO_PUERTO_API:-8080}"
PUERTO_FRONT="${FITO_PUERTO_FRONT:-5500}"
IP=""
ORDEN="arrancar"

while [ $# -gt 0 ]; do
  case "$1" in
    parar) ORDEN="parar" ;;
    --ip) IP="${2:?falta la IP}"; shift ;;
    --puerto-api) PUERTO_API="${2:?falta el puerto}"; shift ;;
    --puerto-front) PUERTO_FRONT="${2:?falta el puerto}"; shift ;;
    -h|--help) sed -n '2,12p' "$0"; exit 0 ;;
    *) echo "argumento desconocido: $1 (use --help)" >&2; exit 2 ;;
  esac
  shift
done

ESTADO="/tmp/fito-demo/$PUERTO_API-$PUERTO_FRONT"   # PIDs, registros y la H2 de esta instancia

ocupado() { ss -ltn "sport = :$1" 2>/dev/null | tail -n +2 | grep -q .; }

# Apaga un PID solo si sigue vivo y su linea de comandos es la que arrancamos nosotros.
apagar() {
  local archivo="$1" patron="$2" nombre="$3" pid
  [ -f "$archivo" ] || { echo "$nombre: nada que apagar (no hay PID guardado)"; return; }
  pid="$(cat "$archivo")"
  if [ -d "/proc/$pid" ] && tr '\0' ' ' < "/proc/$pid/cmdline" | grep -q -- "$patron"; then
    kill "$pid"
    for _ in $(seq 1 30); do [ -d "/proc/$pid" ] || break; sleep 0.5; done
    [ -d "/proc/$pid" ] && kill -9 "$pid" || true
    echo "$nombre: apagado (PID $pid)"
  else
    echo "$nombre: el PID $pid ya no es nuestro o ya termino; no se toca"
  fi
  rm -f "$archivo"
}

if [ "$ORDEN" = "parar" ]; then
  apagar "$ESTADO/backend.pid" "fitodiagnostico" "backend"
  apagar "$ESTADO/front.pid" "http.server" "front"
  exit 0
fi

# ---- IP de la red activa: interfaz Wi-Fi, ignorando docker, veth, br- y CloudflareWARP ----
detectar_ip() {
  local linea nombre ip candidatas="" wifi=""
  while read -r _ nombre _ ip _; do
    ip="${ip%%/*}"
    case "$nombre" in lo|docker*|veth*|br-*|CloudflareWARP*|warp*|virbr*|tun*|tailscale*) continue ;; esac
    case "$nombre" in wl*) wifi="${wifi:-$ip}" ;; *) candidatas="${candidatas:-$ip}" ;; esac
  done < <(ip -4 -o addr show up scope global)
  echo "${wifi:-$candidatas}"
}
[ -n "$IP" ] || IP="$(detectar_ip)"
[ -n "$IP" ] || { echo "no encontre una IP de red local; use --ip <ip>" >&2; exit 1; }

# ---- puertos libres (si hay algo, no se toca nada) ----
for p in "$PUERTO_API" "$PUERTO_FRONT"; do
  if ocupado "$p"; then
    echo "ya hay algo escuchando en el puerto $p: no toco nada. Use 'demo-lan.sh parar' si lo arranque yo, o --puerto-api / --puerto-front." >&2
    exit 1
  fi
done

JAR="$RAIZ/target/fitodiagnostico-0.0.1-SNAPSHOT.jar"
if [ ! -f "$JAR" ]; then
  echo "compilando el backend (./mvnw -q -DskipTests package)..."
  (cd "$RAIZ" && ./mvnw -q -DskipTests package)
fi
[ -f "$FRONT_DIR/index.html" ] || { echo "no encuentro el front en $FRONT_DIR (FITO_FRONT_DIR)" >&2; exit 1; }

# ---- clave de flota unica ----
leer_ini() { [ -f "$LOCAL_INI" ] && sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$LOCAL_INI" | head -1 | sed 's/[[:space:]]*$//' || true; }
leer_env() { [ -f "$ENV_DEMO" ] && sed -n "s/^$1=//p" "$ENV_DEMO" | head -1 || true; }
escribir_env() {   # clave valor: reemplaza o agrega en .env.demo
  touch "$ENV_DEMO"; chmod 600 "$ENV_DEMO"
  if grep -q "^$1=" "$ENV_DEMO"; then sed -i "s|^$1=.*|$1=$2|" "$ENV_DEMO"; else echo "$1=$2" >> "$ENV_DEMO"; fi
}
aleatorio() { python3 -c "import secrets; print(secrets.token_hex($1))"; }

CLAVE_INI="$(leer_ini clave_flota)"
CLAVE_ENV="$(leer_env FITO_CLAVE_FLOTA)"
if [ -n "$CLAVE_INI" ]; then
  CLAVE="$CLAVE_INI"; ORIGEN="firmware/local.ini"
  [ "$CLAVE_ENV" = "$CLAVE" ] || escribir_env FITO_CLAVE_FLOTA "$CLAVE"
elif [ -n "$CLAVE_ENV" ]; then
  CLAVE="$CLAVE_ENV"; ORIGEN=".env.demo"
else
  CLAVE="$(aleatorio 32)"; ORIGEN="generada ahora"
  escribir_env FITO_CLAVE_FLOTA "$CLAVE"
fi
if [ -f "$LOCAL_INI" ] && [ -z "$CLAVE_INI" ]; then   # que el firmware quede con la misma
  if grep -q '^[[:space:]]*clave_flota' "$LOCAL_INI"; then sed -i "s|^[[:space:]]*clave_flota.*|clave_flota = $CLAVE|" "$LOCAL_INI"
  else sed -i "0,/^\[fito\]/s||[fito]\nclave_flota = $CLAVE|" "$LOCAL_INI"; fi
  echo "clave de flota escrita tambien en firmware/local.ini"
fi
[ "${#CLAVE}" -ge 32 ] || { echo "la clave de flota debe tener al menos 32 caracteres" >&2; exit 1; }
[ -n "$(leer_env FITO_JWT_SECRETO)" ] || escribir_env FITO_JWT_SECRETO "$(aleatorio 32)"
[ -n "$(leer_env FITO_ADMIN_CORREO)" ] || escribir_env FITO_ADMIN_CORREO "admin@fito.test"
[ -n "$(leer_env FITO_ADMIN_CONTRASENA)" ] || escribir_env FITO_ADMIN_CONTRASENA "$(aleatorio 8)"

mkdir -p "$ESTADO"
FRONT_URL="http://$IP:$PUERTO_FRONT"

# ---- backend ----
(
  cd "$RAIZ"
  export FITO_JWT_SECRETO="$(leer_env FITO_JWT_SECRETO)"
  export FITO_ADMIN_CORREO="$(leer_env FITO_ADMIN_CORREO)"
  export FITO_ADMIN_CONTRASENA="$(leer_env FITO_ADMIN_CONTRASENA)"
  export FITO_CLAVE_FLOTA="$CLAVE"
  export FITO_FRONT_URL="$FRONT_URL/#"
  export FITODIAGNOSTICO_CORS_ORIGENES="$FRONT_URL,http://localhost:$PUERTO_FRONT${FITO_CORS_EXTRA:+,$FITO_CORS_EXTRA}"
  export SERVER_PORT="$PUERTO_API"
  export SPRING_DATASOURCE_URL="jdbc:h2:file:$ESTADO/fito-demo;DB_CLOSE_ON_EXIT=FALSE"
  nohup java -Xmx384m -jar "$JAR" > "$ESTADO/backend.log" 2>&1 &
  echo $! > "$ESTADO/backend.pid"
)
echo "esperando al backend (hasta 120 s)..."
LISTO=0
for _ in $(seq 1 120); do
  if curl -fs "http://127.0.0.1:$PUERTO_API/api/v1/especies" >/dev/null 2>&1; then LISTO=1; break; fi
  [ -d "/proc/$(cat "$ESTADO/backend.pid")" ] || break
  sleep 1
done
if [ "$LISTO" != 1 ]; then
  echo "el backend no arranco; ultimas lineas de $ESTADO/backend.log:" >&2
  tail -15 "$ESTADO/backend.log" >&2
  apagar "$ESTADO/backend.pid" "fitodiagnostico" "backend" >&2
  exit 1
fi

# ---- front ----
nohup python3 -m http.server "$PUERTO_FRONT" --bind 0.0.0.0 --directory "$FRONT_DIR" > "$ESTADO/front.log" 2>&1 &
echo $! > "$ESTADO/front.pid"
sleep 1

SUFIJO=""
[ "$PUERTO_API" = "8080" ] || SUFIJO="?api=http://$IP:$PUERTO_API/api/v1"
cat <<TXT

================ DEMO EN RED LOCAL ================
IP del portatil      $IP   (clave de flota: $ORIGEN)
Front (celular)      $FRONT_URL/$SUFIJO
API                  http://$IP:$PUERTO_API/api/v1
Administrador        $(leer_env FITO_ADMIN_CORREO)  /  $(leer_env FITO_ADMIN_CONTRASENA)
Registros            $ESTADO/backend.log  y  front.log

Bloque para firmware/local.ini (ya tiene la misma clave de flota que el backend):
[fito]
clave_flota = $CLAVE
wifi_ssid = <el Wi-Fi 2.4 GHz de esta misma red>
wifi_clave = <su clave>
servidor = $IP
puerto_api = $PUERTO_API
puerto_front = $PUERTO_FRONT

Si el celular no llega al portatil, abra los puertos (ufw esta activo):
  sudo ufw allow $PUERTO_API/tcp && sudo ufw allow $PUERTO_FRONT/tcp

Para apagar:  scripts/demo-lan.sh parar${PUERTO_API:+ }$( [ "$PUERTO_API" = 8080 ] && [ "$PUERTO_FRONT" = 5500 ] || echo "--puerto-api $PUERTO_API --puerto-front $PUERTO_FRONT")
===================================================
TXT
