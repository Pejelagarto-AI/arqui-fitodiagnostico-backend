# Simulador del ESP32

Script independiente (Python 3, solo biblioteca estandar) que hace de la matera: envia lecturas periodicas a
`POST /api/v1/dispositivos/{dispositivoId}/lecturas` con el token de dispositivo. No hace falta hardware.

## Que genera

| Tipo | Probabilidad (por defecto) | Que es | Respuesta esperada |
|---|---|---|---|
| `NORMAL` | 75 % | valores que derivan despacio alrededor del rango optimo de la especie | `201`, `estado=SALUDABLE` |
| `FUERA` | 15 % | una magnitud claramente fuera del optimo, pero fisicamente posible | `201`, `EN_RIESGO` o `CRITICO` |
| `INVALIDA` | 10 % | campo ausente, valor no numerico, valor fisicamente imposible (humedad 150), fecha mal formada o JSON roto | `400` con el cuerpo uniforme `{error, mensaje, detalle}` |

Cada envio se registra en una linea con la respuesta del servidor. Las probabilidades, el intervalo y la semilla
del azar son configurables.

## Como se usa

1. Levantar la API (ver el README de la raiz) y crear una planta desde el front o con la API.
2. El dueno pide el token del dispositivo (se muestra una sola vez):

   ```bash
   curl -X POST http://localhost:8080/api/v1/plantas/$PLANTA_ID/dispositivos \
     -H "Authorization: Bearer $TOKEN_DE_USUARIO" -H "Content-Type: application/json" \
     -d '{"dispositivoId": "esp32-01"}'
   ```

3. Correr el simulador con ese token:

   ```bash
   export FITO_TOKEN_DISPOSITIVO="<el token de la respuesta>"
   python3 simulador/simulador_esp32.py --intervalo 3 --especie potos
   ```

Salida de ejemplo:

```
simulador esp32-01 -> http://localhost:8080/api/v1/dispositivos/esp32-01/lecturas (especie potos, cada 3.0 s)
[2026-10-19T15:02:11Z] #1 NORMAL   dentro del optimo                      -> HTTP 201 estado=SALUDABLE id=3f2a91c0
[2026-10-19T15:02:14Z] #2 FUERA    fuera de rango en humedad              -> HTTP 201 estado=EN_RIESGO id=9be10d77
[2026-10-19T15:02:17Z] #3 INVALIDA valor fisicamente imposible (luz)      -> HTTP 400 PARAMETRO_INVALIDO: luz -50.0 lux fuera del rango fisico del sensor
```

## Opciones

| Argumento | Variable de entorno | Por defecto |
|---|---|---|
| `--url` | `FITO_URL` | `http://localhost:8080` |
| `--dispositivo` | `FITO_DISPOSITIVO_ID` | `esp32-01` (debe ser el `dispositivoId` con que se emitio el token) |
| `--token` | `FITO_TOKEN_DISPOSITIVO` | obligatorio |
| `--intervalo` | `FITO_INTERVALO` | `5` segundos |
| `--especie` | `FITO_ESPECIE` | `potos` (`sansevieria`, `potos`, `suculenta`, `helecho`, `lavanda`) |
| `--lecturas` | `FITO_LECTURAS` | `0` = hasta Ctrl+C |
| `--prob-fuera-de-rango` | | `0.15` |
| `--prob-invalida` | | `0.10` |
| `--semilla` | | al azar |

Prueba de robustez rapida: `--prob-invalida 1 --lecturas 10` manda solo datos invalidos; la API debe responder
siempre `400` y seguir viva. Con un token de otro dispositivo o de una persona responde `403`; sin token, `401`.
