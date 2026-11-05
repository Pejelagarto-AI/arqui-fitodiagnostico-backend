# Firmware de la matera (ESP32 + OLED)

Gemelo real de `simulador/simulador_esp32.py --serial auto`: mismas llamadas HTTP, mismos cuerpos, mismas firmas. PlatformIO, framework Arduino. Sensores simulados por ahora (la interfaz `Sensores` deja el hueco para DHT22, sonda capacitiva y BH1750).

## Cableado de la OLED SSD1306 128x64 (I2C)

| OLED | ESP32 DevKit |
|---|---|
| VCC | 3V3 |
| GND | GND |
| SCL | GPIO 22 |
| SDA | GPIO 21 |

Dirección `0x3C`; si no responde, el firmware prueba `0x3D` y lo dice por serie (`escaneo I2C`). Sin pantalla el firmware sigue funcionando y lo informa por serie.

## Configurar (`local.ini`)

```bash
cd firmware
cp local.ini.ejemplo local.ini      # local.ini no se versiona
```

`scripts/demo-lan.sh` imprime el bloque ya listo. Claves de la sección `[fito]`:

| Clave | Para qué |
|---|---|
| `clave_flota` | La misma `FITO_CLAVE_FLOTA` del backend (mínimo 32 caracteres). Vacía: el firmware se detiene con "Falta clave de flota". |
| `wifi_ssid`, `wifi_clave` | Wi-Fi por defecto de compilación (demo con el hotspot, 2.4 GHz). Vacío: portal cautivo. Admiten espacios y signos. |
| `servidor` | IP del portátil en esa red. |
| `puerto_api`, `puerto_front` | 8080 y 5500. |

Las cadenas llegan al código por `include/config_local.h`, que genera `extra_script.py` en cada compilación (no se versiona), así que un SSID con espacios o una clave con comillas no rompen nada.

## Compilar, subir, mirar

```bash
PIO=~/.local/share/fito-pio/bin/pio
$PIO run -e esp32dev                 # compilar (la primera vez descarga la plataforma y las librerías)
$PIO run -e esp32dev -t upload       # subir por /dev/ttyUSB0
$PIO device monitor                  # serie a 115200
```

Otras placas: `-e esp32-s3-devkitc-1` o `-e esp32-c3-devkitm-1` (los pines I2C de `src/configuracion.h` pueden necesitar ajuste).

## Pruebas del algoritmo sin placa

```bash
python3 firmware/test/probar_nucleo.py
```

Compila con `gcc` el núcleo en C (`src/nucleo/`) con un SHA-256 portable y compara, contra el simulador de Python, el serial desde la MAC (incluida `e08cfee64440` -> `fito-YUBUD6`), el secreto en base64url, la firma de flota y la respuesta al reto (42 comprobaciones). En el chip el hash lo da mbedTLS; al arrancar, el firmware verifica ese camino con el vector RFC 4231 y escribe `autotest HMAC-SHA256 (mbedTLS): ok`.

## Qué muestra la OLED

| Estado | Pantalla |
|---|---|
| Arranque | "Fito", el serial, "Iniciando..." |
| Conectando | "Conectando al Wi-Fi" y el SSID |
| Portal cautivo | "Conéctate al Wi-Fi Fito-XXXXXX" (red abierta; pide SSID, clave y "Servidor") |
| Esperando reclamo | **QR** (64x64, a la izquierda) y a la derecha "Escanea", el serial en dos líneas y "Esperando..." |
| Vinculada | "Vinculada" con un visto, 2,5 s |
| Operando | serial, humedad, luz, temperatura, estado del servidor (SALUDABLE / EN RIESGO / CRITICO) y un punto que parpadea en cada envío |
| Sin red | "Sin red (N guardadas)" |
| Error fatal | "Clave de flota no coincide", "Serial ya existe con otro secreto", "Falta clave de flota", "Falta el servidor" |
| BOOT sostenido | cuenta regresiva de 5 s |

El QR codifica `http://<servidor>:<puerto front>/#/v/<6 caracteres>`; el front lo redirige a `#/vincular?serial=fito-<CODIGO>` (F35). Si el enlace no cabe en la versión 3, la pantalla muestra el serial en texto grande y lo avisa por serie.

## Reiniciar la configuración

Mantener **BOOT (GPIO 0) 5 s** con la matera operando: borra Wi-Fi, servidor y token (no la identidad) y reinicia. Para empezar de cero con otra identidad hay que borrar toda la flash (`pio run -t erase`); entonces el registro da 409 hasta reiniciar el backend de desarrollo (H2) o borrar la fila.

## Problemas comunes

- **No aparece `/dev/ttyUSB0` o no sube:** el cable es solo de carga; usar uno de datos.
- **`Permission denied` en el puerto:** `sudo usermod -aG dialout $USER` (cerrar sesión y volver a entrar) o `sudo chmod a+rw /dev/ttyUSB0`.
- **`Failed to start stub flasher` o no conecta al subir:** `upload_speed = 115200` ya está puesto; si insiste, descomentar `upload_flags = --no-stub` en `platformio.ini`; en algunas placas hay que mantener BOOT al empezar la subida.
- **La OLED no enciende:** revisar SDA/SCL; algunas pantallas usan `0x3D` (el firmware la encuentra solo).
- **No conecta al hotspot:** el ESP32 solo usa 2.4 GHz; en el iPhone activar "Maximizar compatibilidad". Probar sin comillas ni caracteres raros en la clave.
- **La matera conecta pero el celular o la matera no llegan al portátil:** `sudo ufw allow 8080/tcp && sudo ufw allow 5500/tcp`.
- **Pantalla "Clave de flota no coincide":** `clave_flota` de `local.ini` distinta de la del backend. `demo-lan.sh` usa la de `local.ini`.
- **Sin hora:** sin internet no hay NTP; el firmware toma la hora de la cabecera `Date` del backend en la primera respuesta.
