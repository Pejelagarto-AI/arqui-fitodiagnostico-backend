# Simulador del ESP32

Script independiente (Python 3, solo biblioteca estandar) que hace de la matera: arranca con el **serial** y el
**secreto** de fabrica, consigue solo su credencial (reto HMAC) y envia lecturas periodicas a
`POST /api/v1/dispositivos/{serial}/lecturas`. No hace falta hardware, y nadie copia ningun token. Incluye
`fabrica.py`, que da de alta el dispositivo y pinta su sticker.

## Como se autentica (fase 3)

1. `POST /dispositivos/{serial}/reto` devuelve un reto aleatorio (32 bytes en base64, vale 60 s, un solo uso).
2. `POST /dispositivos/{serial}/credencial` con `{respuesta}`, donde
   `respuesta = base64(HMAC-SHA256(clave = el texto del secreto, mensaje = el texto del reto))`
   (`hmac.new(secreto.encode(), reto.encode(), hashlib.sha256)`, sin decodificar nada).
   - `200 {token, plantaId, expira}`: guarda el token en la "NVS" y empieza a reportar.
   - `409 DISPOSITIVO_SIN_RECLAMAR`: nadie lo reclamo todavia. Imprime
     `esperando reclamo del serial fito-XXXXXX... (reintento en 5 s)` y reintenta.
3. El token dura una hora. Se renueva solo (reto + credencial otra vez) cuando faltan menos de 2 minutos para que
   expire, o apenas una lectura da `401`. Si el dueno desvinculo el dispositivo, la renovacion da `409` y el
   simulador vuelve a esperar el reclamo.

La **NVS** es un JSON (`simulador/.nvs-<serial>.json` por defecto, en `.gitignore`) con el ultimo token: si el
simulador se reinicia y el token sigue vigente, lo reutiliza sin pedir otro. Borrar el archivo es "restaurar de
fabrica".

## Como se usa

1. Levantar la API (ver el README de la raiz), crear un usuario y una planta (desde el front o con la API).
2. **Fabrica**: dar de alta un dispositivo como administrador. Imprime el sticker y el comando exacto:

   ```bash
   FITO_ADMIN_CORREO=admin@fito.local FITO_ADMIN_CONTRASENA=admin12345 python3 simulador/fabrica.py
   ```

   ```
   +-----------------------------------------------------------------------------------+
   | DISPOSITIVO NUEVO                                                                 |
   |                                                                                   |
   | serial   fito-7K2M9Q      <- se imprime en la matera; el dueno lo escribe en la app |
   | secreto  <43 caracteres>  <- se graba en el chip; se muestra UNA vez              |
   | ...                                                                               |
   ```

   Opciones: `--url` (`FITO_URL`) y `--serial` (`FITO_SERIAL`, si se quiere elegir el serial en vez de dejar que
   el servidor genere `fito-XXXXXX`).

3. **Arrancar el simulador antes de reclamar** (asi se ve la espera):

   ```bash
   FITO_SERIAL=fito-7K2M9Q FITO_SECRETO=<el del sticker> python3 simulador/simulador_esp32.py --intervalo 3
   ```

4. El dueno escribe el serial en su planta (en el front, o `POST /api/v1/plantas/{id}/dispositivos {"serial": "fito-7K2M9Q"}`).
   El simulador despierta solo, obtiene su credencial y empieza a mandar lecturas.

Salida de ejemplo:

```
simulador fito-7K2M9Q -> http://localhost:8080/api/v1/dispositivos/fito-7K2M9Q/lecturas (especie potos, cada 3.0 s, NVS simulador/.nvs-fito-7K2M9Q.json)
[2026-11-03T21:50:05Z] esperando reclamo del serial fito-7K2M9Q... (reintento en 5 s)
[2026-11-03T21:50:10Z] esperando reclamo del serial fito-7K2M9Q... (reintento en 5 s)
[2026-11-03T21:50:15Z] credencial obtenida para la planta a7915510 (vence 2026-11-03T22:50:15.956636349Z)
[2026-11-03T21:50:15Z] #1 NORMAL   dentro del optimo                      -> HTTP 201 estado=SALUDABLE id=3f2a91c0
[2026-11-03T21:50:18Z] #2 FUERA    fuera de rango en humedad              -> HTTP 201 estado=EN_RIESGO id=9be10d77
[2026-11-03T21:50:21Z] #3 INVALIDA valor fisicamente imposible (luz)      -> HTTP 400 PARAMETRO_INVALIDO: luz -50.0 lux fuera del rango fisico del sensor
```

## Que genera

| Tipo | Probabilidad (por defecto) | Que es | Respuesta esperada |
|---|---|---|---|
| `NORMAL` | 75 % | valores que derivan despacio alrededor del rango optimo de la especie | `201`, `estado=SALUDABLE` |
| `FUERA` | 15 % | una magnitud claramente fuera del optimo, pero fisicamente posible | `201`, `EN_RIESGO` o `CRITICO` |
| `INVALIDA` | 10 % | campo ausente, valor no numerico, valor fisicamente imposible (humedad 150), fecha mal formada o JSON roto | `400` con el cuerpo uniforme `{error, mensaje, detalle}` |

Cada envio se registra en una linea con la respuesta del servidor. Las probabilidades, el intervalo y la semilla
del azar son configurables. `--especie` se mantiene porque el simulador necesita los rangos para inventar datos
plausibles; el chip real mide y ya.

## Opciones

| Argumento | Variable de entorno | Por defecto |
|---|---|---|
| `--url` | `FITO_URL` | `http://localhost:8080` |
| `--serial` | `FITO_SERIAL` | obligatorio (el del sticker) |
| `--secreto` | `FITO_SECRETO` | obligatorio (el del sticker) |
| `--nvs` | `FITO_NVS` | `simulador/.nvs-<serial>.json` |
| `--espera-reclamo` | `FITO_ESPERA_RECLAMO` | `5` segundos entre reintentos mientras nadie lo reclama |
| `--intervalo` | `FITO_INTERVALO` | `5` segundos |
| `--especie` | `FITO_ESPECIE` | `potos` (`sansevieria`, `potos`, `suculenta`, `helecho`, `lavanda`) |
| `--lecturas` | `FITO_LECTURAS` | `0` = hasta Ctrl+C |
| `--prob-fuera-de-rango` | | `0.15` |
| `--prob-invalida` | | `0.10` |
| `--semilla` | | al azar |

Desaparecieron `--token`, `FITO_TOKEN_DISPOSITIVO` y `--dispositivo`: el token ya no lo entrega nadie a mano.

Casos para probar: sin reclamar el simulador espera (`409`); un secreto equivocado da `401 CREDENCIAL_RECHAZADA` tres
veces seguidas y termina con un mensaje; un serial que no existe termina con `404 DISPOSITIVO_NO_ENCONTRADO`; con
`--prob-invalida 1 --lecturas 10` manda solo datos invalidos y la API debe responder siempre `400` y seguir viva.
