# Simulador del ESP32

Script independiente (Python 3, solo biblioteca estandar) que hace de la matera: consigue solo su credencial (reto
HMAC) y envia lecturas periodicas a `POST /api/v1/dispositivos/{serial}/lecturas`. No hace falta hardware, y nadie
copia ningun token. Tiene dos modos de nacer:

| Modo | Cuando usarlo | Como |
|---|---|---|
| **Fabrica automatica** (`--serial auto`) | el flujo normal y el de la demo: la matera se registra sola la primera vez que prende | solo necesita la clave de flota; el backend debe arrancar con `FITO_CLAVE_FLOTA` |
| **Fabrica manual** (`fabrica.py`) | el backend no tiene clave de flota, o se quiere un serial elegido a mano | un administrador da de alta el dispositivo y se pasan `--serial` y `--secreto` del sticker |

## Fabrica automatica (fase 3b): `--serial auto`

El firmware de todas las materas trae grabada la **clave de flota**. La primera vez que prende, el chip:

1. inventa una "MAC" (6 bytes aleatorios) y deriva de ella su serial `fito-XXXXXX` (alfabeto sin 0/O/1/I);
2. genera su secreto (32 bytes aleatorios en base64url);
3. guarda MAC, serial y secreto en la NVS (`simulador/.nvs-auto-<serial>.json`, o la ruta de `--nvs`);
4. se registra con `POST /api/v1/dispositivos/registro {serial, secreto, firma}`, donde
   `firma = base64(HMAC-SHA256(clave de flota, serial + "\n" + secreto))`. `201` si es nuevo, `200` si ya existia con
   ese secreto (idempotente); en ambos marca `registrado: true` en la NVS;
5. pinta el **sticker** (serial y el enlace `<front>/#/vincular?serial=...` que llevaria el QR) y sigue como siempre:
   reto, `409` mientras espera el reclamo, credencial, lecturas, cola sin red.

```bash
FITO_CLAVE_FLOTA=<la misma del servidor> python3 simulador/simulador_esp32.py --serial auto --intervalo 2
```

```
identidad automatica fito-75CBL9 (NVS simulador/.nvs-auto-fito-75CBL9.json, nueva, sin registrar)
[2026-11-04T01:49:18Z] registro HTTP 201: fito-75CBL9 SIN_RECLAMAR
+-------------------------------------------------------------+
| STICKER DE LA MATERA                                        |
| serial  fito-75CBL9                                         |
| enlace  http://localhost:5500/#/vincular?serial=fito-75CBL9 |
+-------------------------------------------------------------+
[2026-11-04T01:49:18Z] esperando reclamo del serial fito-75CBL9... (reintento en 3 s)
[2026-11-04T01:49:31Z] credencial obtenida para la planta ef592324 (vence 2026-11-04T02:49:30.998998882Z)
[2026-11-04T01:49:31Z] #1 NORMAL   dentro del optimo                      -> HTTP 201 estado=SALUDABLE id=4b8084cd
```

- **Reiniciar** conserva la identidad: lee la NVS, no se vuelve a registrar y reutiliza el token si sigue vigente.
  Sin `--nvs`, reutiliza la unica `simulador/.nvs-auto-*.json` que exista; si hay varias exige `--nvs` para saber cual.
- **Borrar la NVS** es "restaurar de fabrica": el chip inventa otra identidad y se registra como una matera nueva.
- **Clave de flota equivocada**: termina con `la clave de flota no coincide con la del servidor`. Si el backend no tiene
  `FITO_CLAVE_FLOTA`, la ruta no existe y termina avisando que la fabrica automatica esta apagada.
- Opciones nuevas: `--clave-flota` (`FITO_CLAVE_FLOTA`, obligatoria con `auto`) y `--front-url` (`FITO_FRONT_URL`,
  `http://localhost:5500`) para el enlace del sticker.
- El dueno reclama el serial que pinto el sticker (en el front, o
  `POST /api/v1/plantas/{id}/dispositivos {"serial": "fito-75CBL9"}`). Si lo hace **antes** de que el simulador prenda,
  recibe `404` con el mensaje `no existe o la matera no ha encendido todavia`: basta prender la matera y reintentar.

## Fabrica manual y como se autentica (fase 3)

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

## Como se usa (fabrica manual)

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

## Sin red

Como el chip real, el simulador no pierde lecturas cuando se cae la red: las guarda en la NVS y las entrega despues.

- Si el POST falla por red (conexion rechazada, timeout) o por `5xx`, la lectura se **encola** en la NVS (clave
  `pendientes`, ordenada por `marcaDeTiempo`) con su marca original y se sigue midiendo a su ritmo. Cada lectura
  nueva tambien se encola mientras no haya red.
- Cada `--espera-red` segundos (10) prueba si volvio. Al volver **vacia la cola en orden**, una a una, y despues
  sigue en vivo. Un `400` de la cola se descarta como en vivo (no es falta de red); `404` y `403` tampoco se reencolan.
- Si el token vencio sin red, el primer envio da `401`, renueva con reto + credencial y sigue vaciando. Si el dueno
  desvinculo mientras tanto (`409` al renovar), la cola queda en la NVS y vuelve a esperar el reclamo.
- Al arrancar, los pendientes de una ejecucion anterior se envian primero.
- Tope `--max-pendientes` (500): al llenarse descarta la mas vieja y avisa.
- El servidor guarda cada lectura con su `tomadaEn` original y un `registradoEn` de cuando llego, asi el vigente sigue
  siendo la ultima en llegar (D67, D82).

Para la demo sin tocar el backend, `--simular-caida 10 --duracion-caida 20` finge que no hay red entre los segundos 10
y 30 desde el arranque:

```bash
python3 simulador/simulador_esp32.py --simular-caida 10 --duracion-caida 20 --intervalo 2 --espera-red 6
```

```
[2026-11-03T22:55:22Z] --- CAIDA SIMULADA: entra, sin red por 20 s ---
[2026-11-03T22:55:22Z] sin red: lectura #6 guardada en NVS (1 pendientes)
[2026-11-03T22:55:24Z] sin red: lectura #7 guardada en NVS (2 pendientes)
[2026-11-03T22:55:42Z] --- CAIDA SIMULADA: termina, la red vuelve ---
[2026-11-03T22:55:44Z] sin red: lectura #17 guardada en NVS (12 pendientes)
[2026-11-03T22:55:46Z] red de vuelta: enviando 12 pendientes...
[2026-11-03T22:55:46Z] #6 NORMAL   dentro del optimo                      -> HTTP 201 estado=SALUDABLE id=d34e48f7 (pendiente, tomada 2026-11-03T22:55:22Z)
[2026-11-03T22:55:46Z] #7 NORMAL   dentro del optimo                      -> HTTP 201 estado=SALUDABLE id=2f9c9d4d (pendiente, tomada 2026-11-03T22:55:24Z)
[2026-11-03T22:55:46Z] #17 NORMAL   dentro del optimo                      -> HTTP 201 estado=SALUDABLE id=5c26c5df (pendiente, tomada 2026-11-03T22:55:44Z)
[2026-11-03T22:55:46Z] #18 INVALIDA valor no numerico (luz)                -> HTTP 400 PARAMETRO_INVALIDO: el campo 'luz' no tiene un formato válido.
[2026-11-03T22:55:48Z] #19 NORMAL   dentro del optimo                      -> HTTP 201 estado=SALUDABLE id=ad2e7104
```

Salida real recortada: durante la caida encola, al volver anuncia `red de vuelta: enviando 12 pendientes...` y las
entrega en orden con `(pendiente, tomada ...)`; la `#16` era invalida a proposito y recibe su `400`. Para probarlo con el
backend caido de verdad, apuntar a un puerto vacio (`--url http://localhost:8099`), matar el simulador y arrancarlo
contra el backend real: la NVS conserva la cola y se envia primero.

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
| `--serial` | `FITO_SERIAL` | obligatorio (el del sticker), o `auto` para la fabrica automatica |
| `--secreto` | `FITO_SECRETO` | obligatorio (el del sticker); no se usa con `--serial auto` |
| `--clave-flota` | `FITO_CLAVE_FLOTA` | obligatoria con `--serial auto` |
| `--front-url` | `FITO_FRONT_URL` | `http://localhost:5500` (enlace del sticker) |
| `--nvs` | `FITO_NVS` | `simulador/.nvs-<serial>.json` (`.nvs-auto-<serial>.json` con `auto`) |
| `--espera-reclamo` | `FITO_ESPERA_RECLAMO` | `5` segundos entre reintentos mientras nadie lo reclama |
| `--intervalo` | `FITO_INTERVALO` | `5` segundos |
| `--especie` | `FITO_ESPECIE` | `potos` (`sansevieria`, `potos`, `suculenta`, `helecho`, `lavanda`) |
| `--lecturas` | `FITO_LECTURAS` | `0` = hasta Ctrl+C |
| `--max-pendientes` | `FITO_MAX_PENDIENTES` | `500` lecturas guardadas sin red |
| `--espera-red` | `FITO_ESPERA_RED` | `10` segundos entre pruebas de red |
| `--simular-caida` | `FITO_SIMULAR_CAIDA` | sin simular; N = segundos desde el arranque |
| `--duracion-caida` | `FITO_DURACION_CAIDA` | `20` segundos |
| `--prob-fuera-de-rango` | | `0.15` |
| `--prob-invalida` | | `0.10` |
| `--semilla` | | al azar |

Desaparecieron `--token`, `FITO_TOKEN_DISPOSITIVO` y `--dispositivo`: el token ya no lo entrega nadie a mano.

Casos para probar: sin reclamar el simulador espera (`409`); un secreto equivocado da `401 CREDENCIAL_RECHAZADA` tres
veces seguidas y termina con un mensaje; un serial que no existe termina con `404 DISPOSITIVO_NO_ENCONTRADO`; con
`--prob-invalida 1 --lecturas 10` manda solo datos invalidos y la API debe responder siempre `400` y seguir viva.
