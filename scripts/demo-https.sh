#!/usr/bin/env bash
# Demo en red local POR HTTPS: lo mismo que demo-lan.sh mas un proxy Caddy delante (F40 del front).
# Hace falta para la camara en vivo del lector de QR: el navegador exige contexto seguro, y una pagina HTTPS
# no puede llamar a una API HTTP, asi que Caddy sirve el front y manda /api/* y /ws* al backend.
#
#   scripts/demo-https.sh                arranca backend + front (via demo-lan.sh) y el proxy en https://<ip>:8443
#   scripts/demo-https.sh parar          apaga el proxy y lo que arranco demo-lan.sh
#   scripts/demo-https.sh --ip 10.0.0.5  fuerza la IP (la del certificado y la que se imprime)
#   --puerto-https N                     puerto del proxy (por defecto 8443)
#
# El certificado lo emite la CA interna de Caddy (tls internal) y queda en el volumen docker fito-caddy-data,
# asi que es el mismo entre arranques: en el celular se acepta una vez ("Visitar sitio web" en Safari).
# Requiere docker (imagen caddy:2-alpine) y, si ufw esta activo, abrir el puerto: sudo ufw allow 8443/tcp.
set -euo pipefail

AQUI="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="${FITO_RAIZ:-$(cd "$AQUI/.." && pwd)}"
export FITO_RAIZ="$RAIZ"
PUERTO_API="${FITO_PUERTO_API:-8080}"
PUERTO_FRONT="${FITO_PUERTO_FRONT:-5500}"
PUERTO_HTTPS="${FITO_PUERTO_HTTPS:-8443}"
CONTENEDOR="fito-caddy"
IMAGEN="caddy:2-alpine"
IP=""
ORDEN="arrancar"

while [ $# -gt 0 ]; do
  case "$1" in
    parar) ORDEN="parar" ;;
    --ip) IP="${2:?falta la IP}"; shift ;;
    --puerto-https) PUERTO_HTTPS="${2:?falta el puerto}"; shift ;;
    -h|--help) sed -n '2,13p' "$0"; exit 0 ;;
    *) echo "argumento desconocido: $1 (use --help)" >&2; exit 2 ;;
  esac
  shift
done

ESTADO="/tmp/fito-demo/caddy-$PUERTO_HTTPS"
ocupado() { ss -ltn "sport = :$1" 2>/dev/null | tail -n +2 | grep -q .; }

if [ "$ORDEN" = "parar" ]; then
  if docker ps -a --format '{{.Names}}' | grep -qx "$CONTENEDOR"; then
    docker rm -f "$CONTENEDOR" >/dev/null && echo "proxy: apagado ($CONTENEDOR)"
  else
    echo "proxy: nada que apagar"
  fi
  "$AQUI/demo-lan.sh" parar
  exit 0
fi

command -v docker >/dev/null || { echo "hace falta docker para el proxy" >&2; exit 1; }
docker image inspect "$IMAGEN" >/dev/null 2>&1 || docker pull "$IMAGEN"

# ---- IP: la misma que detecta demo-lan.sh ----
if [ -z "$IP" ]; then
  IP="$(ip -4 -o addr show up scope global | awk '$2 ~ /^wl/ {sub(/\/.*/,"",$4); print $4; exit}')"
  [ -n "$IP" ] || IP="$(ip -4 -o addr show up scope global | awk '$2 !~ /^(docker|veth|br-|virbr|tun|tailscale|CloudflareWARP|warp)/ {sub(/\/.*/,"",$4); print $4; exit}')"
fi
[ -n "$IP" ] || { echo "no encontre una IP de red local; use --ip <ip>" >&2; exit 1; }

# El backend tiene que aceptar el origen HTTPS: el navegador manda Origin https://<ip>:8443 y, detras del proxy,
# Spring lo compara con su propio puerto 8080, asi que lo trata como CORS y sin esta lista responde 403.
export FITO_CORS_EXTRA="https://$IP:$PUERTO_HTTPS,https://localhost:$PUERTO_HTTPS,https://127.0.0.1:$PUERTO_HTTPS"

# ---- backend + front: los arranca demo-lan.sh si no estan ya ----
if ocupado "$PUERTO_API" && ocupado "$PUERTO_FRONT"; then
  echo "backend ($PUERTO_API) y front ($PUERTO_FRONT) ya estan arriba; no los toco."
  echo "  OJO: si ese backend no arranco con los origenes HTTPS (FITO_CORS_EXTRA), el navegador recibira 403; en ese caso: demo-https.sh parar && demo-https.sh"
else
  "$AQUI/demo-lan.sh" ${IP:+--ip "$IP"} --puerto-api "$PUERTO_API" --puerto-front "$PUERTO_FRONT"
fi

if ocupado "$PUERTO_HTTPS"; then
  echo "ya hay algo en el puerto $PUERTO_HTTPS: no toco nada. Use 'demo-https.sh parar' o --puerto-https." >&2
  exit 1
fi

# ---- Caddyfile para esta IP ----
mkdir -p "$ESTADO"
cat > "$ESTADO/Caddyfile" <<CADDY
{
	admin off
	auto_https disable_redirects
}

https://$IP:$PUERTO_HTTPS, https://localhost:$PUERTO_HTTPS, https://127.0.0.1:$PUERTO_HTTPS {
	tls internal
	encode gzip

	@backend path /api/* /ws /ws/*
	handle @backend {
		reverse_proxy 127.0.0.1:$PUERTO_API
	}
	handle {
		reverse_proxy 127.0.0.1:$PUERTO_FRONT
	}
}
CADDY

docker rm -f "$CONTENEDOR" >/dev/null 2>&1 || true
docker run -d --name "$CONTENEDOR" --network host \
  -v "$ESTADO/Caddyfile:/etc/caddy/Caddyfile:ro" \
  -v fito-caddy-data:/data \
  "$IMAGEN" >/dev/null

echo "esperando al proxy..."
for _ in $(seq 1 30); do
  if curl -fsk "https://127.0.0.1:$PUERTO_HTTPS/api/v1/especies" >/dev/null 2>&1; then
    cat <<TXT

================ DEMO POR HTTPS ================
Front (celular)      https://$IP:$PUERTO_HTTPS/
API (mismo origen)   https://$IP:$PUERTO_HTTPS/api/v1
Backend directo      http://$IP:$PUERTO_API/api/v1   (la matera sigue hablando por HTTP)
Caddyfile            $ESTADO/Caddyfile

En Safari el certificado es propio: "Mostrar detalles" > "Visitar sitio web". Despues la camara en vivo
del lector de QR funciona porque la pagina tiene contexto seguro.

Si el celular no llega al portatil (ufw activo):
  sudo ufw allow $PUERTO_HTTPS/tcp && sudo ufw allow $PUERTO_API/tcp

Para apagar todo:  scripts/demo-https.sh parar
================================================
TXT
    exit 0
  fi
  sleep 1
done
echo "el proxy no respondio; registros del contenedor:" >&2
docker logs "$CONTENEDOR" 2>&1 | tail -20 >&2
exit 1
