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

El **código** de 6 caracteres (`YUBUD6`) solo aparece en la pantalla de reclamo; en lecturas y vinculada se muestra el **apodo** de la planta (campo `apodo` de la respuesta 200 de la credencial y de cada 201; se guarda en NVS junto al token, se borra con él y, si nunca llegó, se muestra el código). Se pasa de UTF-8 a ASCII (á é í ó ú ü ñ a su letra base, lo demás fuera de ASCII se omite) y, si pasa de 13 caracteres, corre como marquesina con pausa de 1 s en los extremos. Nunca se ve el prefijo `fito-` del serial. Sin tildes ni eñes (fuente CP437).

| Estado | Pantalla |
|---|---|
| Arranque | "Fito", el serial, "Iniciando..." |
| Conectando | "Conectando al Wi-Fi" y el SSID |
| Portal cautivo | **QR de Wi-Fi** (`WIFI:S:Fito-XXXXXX;T:nopass;;`) a la izquierda y "Conecta tu celular a:" + el nombre de la red. La red es abierta; pide SSID, clave y "Servidor" |
| Esperando reclamo | **QR** (64x64) a la izquierda; a la derecha "Codigo", los 6 caracteres en tamaño 2 (`YUB` / `UD6`) y tres puntos de espera animados |
| Vinculada | animación de 8 cuadros a 8 por segundo (semilla, brote, dos hojas, planta, destello, disco, visto a medias, visto en círculo); el visto se desliza a la izquierda y aparece "Vinculada" con el código. No bloquea el bucle (`pantalla::animarExito`) |
| Operando | sprite de la planta (48x64) a la izquierda y a la derecha humedad, luz y temperatura con icono, el código y el estado del servidor (SALUDABLE / EN RIESGO / CRITICO) con un punto que parpadea en cada envío |
| Sin red | icono de sin red y "Sin red (N guardadas)" |
| Error fatal | "ERROR" con icono: "Clave de flota no coincide", "Serial ya existe con otro secreto", "Falta clave de flota", "Falta el servidor" |
| BOOT sostenido | cuenta regresiva de 5 s |

El QR de reclamo codifica `http://<servidor>:<puerto front>/#/v/<6 caracteres>`; el front lo redirige a `#/vincular?serial=fito-<CODIGO>` (F35). Si el enlace no cabe en la versión 3, la pantalla muestra solo el código y lo avisa por serie.

**La planta.** Las lecturas muestran una planta común en matera (erguida, caída o marchita con una hoja en el suelo según el estado del servidor, dos cuadros que alternan cada ~600 ms) o, si la especie que devolvió el servidor contiene "atrapamoscas" o "carniv", una atrapamoscas de caricatura (abierta, a medio cerrar y cerrada, triste en riesgo y crítico). La carnívora da un mordisco cada 6 a 10 s y cada vez que llega una respuesta 201 del servidor. La especie y los rangos óptimos salen de `diagnostico.especie` y `diagnostico.parametros[].rangoOptimo` de la respuesta de `POST /dispositivos/{serial}/lecturas`: los sensores simulados se centran en el medio de cada rango (cualquier especie queda saludable) y el 12 % de las lecturas sale fuera de rango para que el estado cambie en la demo. Hasta la primera respuesta se usan los rangos del poto.

**Cara y fluidez.** La cara se dibuja por código sobre el sprite (no hay un sprite por expresión): saludable = ojos redondos y sonrisa; en riesgo = ojos entrecerrados, cejas preocupadas y boca ondulada; crítico = ojos caídos, cejas inclinadas, boca hacia abajo y una lágrima ocasional. Parpadea cada 3 a 5 s y al cambiar de estado la expresión se interpola en 400 ms. La planta se balancea con un seno suave que dobla el sprite (más arriba, más desplazamiento; la marchita casi no se mueve) y la pantalla de lecturas se repinta a 20 cuadros por segundo con un temporizador no bloqueante. El I2C va a 400 kHz y al arrancar se registra "OLED: N ms por cuadro" para saber el techo real. El mordisco de la carnívora tiene 5 pasos con un pequeño rebote; la animación de éxito traza el anillo y el visto progresivamente. Durante una petición HTTP la animación se detiene un momento.

**OLED con contacto intermitente.** Cada 2 s el firmware busca la pantalla si falta (probando también SDA y SCL cruzados) y, si ya estaba lista, comprueba que siga respondiendo. Al reaparecer la reinicializa, escribe "OLED lista en 0x3C (detectada en caliente)" por serie y repinta la pantalla de la fase actual. Nada de esto bloquea el bucle más que unos milisegundos.

### Regenerar los sprites

El arte vive como texto en `sprites/arte/*.txt` (`#` encendido, `.` apagado); `src/sprites.h` se genera y se versiona.

```bash
python3 firmware/sprites/dibujar.py       # (opcional) vuelve a trazar plantas y animación en arte/*.txt; los iconos 8x8 son a mano
python3 firmware/sprites/generar.py       # arte/*.txt -> src/sprites.h + PNG x4 en sprites/previa/
python3 firmware/sprites/previsualizar.py /ruta/salida   # pantallas completas en PNG x4 y un GIF animado por estado (necesita haber compilado una vez)
```

Retocar un cuadro a mano: editar el `.txt` (mismo ancho en todas las filas) y correr `generar.py`, sin `dibujar.py`, que sobrescribe las plantas.

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
