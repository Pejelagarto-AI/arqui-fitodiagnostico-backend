# Fase 5: firmware real de la matera (ESP32 + OLED con QR)

Cuaderno de implementación (como `docs/fase3/` y `docs/fase4/`). El simulador de la fase 3b tenía un gemelo pendiente: el chip. Esta fase lo construye en `firmware/` y lo prueba contra el mismo backend, sin cambiar una línea de Java. Spec: `SPEC-FIRMWARE-ESP32.md` en la carpeta de la entrega. Guía de uso: [`firmware/README.md`](../../firmware/README.md).

## Qué hay

| Pieza | Dónde |
|---|---|
| Núcleo puro en C (serial desde la MAC, base64, firmas) | `firmware/src/nucleo/` |
| Identidad (NVS), red, reloj, cliente de la API, pantalla, sensores, cola, botón | `firmware/src/*.cpp`, una responsabilidad por archivo |
| Máquina de estados (arranque, red, hora, registro, credencial, reclamo, operando, fatal) | `firmware/src/main.cpp` |
| Pruebas del algoritmo contra el simulador | `firmware/test/probar_nucleo.py` |
| Demo en red local (backend H2 en archivo + front) | `scripts/demo-lan.sh` |
| Enlace corto `#/v/:codigo` y API por defecto en el host del front | repo del front, F35 |

## Decisiones

**D89. El QR de la OLED lleva una ruta corta.** La pantalla mide 64 px de alto; un QR versión 3 (29 módulos) a 2 px por módulo mide 58 px y guarda 53 bytes en corrección L (42 en M). `http://192.168.43.105:5500/#/vincular?serial=fito-7K2M9Q` mide 56 bytes y no cabe; `http://192.168.255.255:5500/#/v/7K2M9Q` mide 38 en el peor caso y cabe incluso en M. El front resuelve `#/v/:codigo` a `#/vincular?serial=fito-<CODIGO>` (el prefijo va implícito). El QR se dibuja sobre un cuadro encendido de 64x64 con los módulos apagados, que es como lo leen los celulares; el margen claro es de 3 px (norma: 4 módulos). Si el contenido no cabe en la versión 3 no se sube de versión (la 4 mide 66 px): se muestra el serial en texto grande y se avisa por serie. Descartado: un acortador de URL (otro servicio) y pantalla más grande (no es el hardware que hay).

**D90. Wi-Fi por defecto de compilación para la demo y portal cautivo para el flujo real.** Con credenciales en NVS conecta; si no hay y `local.ini` trae `wifi_ssid`, las usa y las guarda; si no conecta en 20 s o no hay nada, abre el portal (WiFiManager, red abierta `Fito-XXXXXX`, campos SSID, clave y "Servidor"). El portal no sirve para la demo con hotspot: el celular que hace de hotspot no puede conectarse además a la red de la matera. Los valores de compilación llegan por un header generado (`extra_script.py` -> `include/config_local.h`) y no por `-D`, para que un SSID con espacios o una clave con comillas no se rompan. La clave de flota queda en el binario (D86); `local.ini` y el header no se versionan, y `demo-lan.sh` usa la misma clave que `local.ini` para que backend y firmware no queden con claves distintas.

**D91. La hora sale de NTP, con respaldo en la cabecera `Date`.** Las lecturas llevan `marcaDeTiempo` y la vigencia del token se compara con la hora, así que sin hora válida (posterior a 2023) no se reportan lecturas. Se pide NTP (`configTime`) y se esperan 5 s; si no hay internet (hotspot sin datos), la primera respuesta HTTP del backend trae `Date` y de ahí se pone el reloj. Precisión de segundos: sobra para lecturas cada 5 s.

**D92. La cola sin red vive en RAM y guarda 120 lecturas.** 120 lecturas a 5 s son 10 minutos. Cada una conserva su hora original y se entrega en orden al volver la red (máximo 6 por ciclo para no bloquear botón ni pantalla); al llenarse se descarta la más vieja (misma regla que la D82). Diferencia con el simulador: este guarda la cola en la NVS (500); el chip la pierde si se reinicia. Descartado por ahora: cola en flash (desgaste de escritura cada 5 s; necesita un anillo con nivelación). Un 400, 403 o 404 en una lectura no se reencola (D82).

## Pruebas

`python3 firmware/test/probar_nucleo.py`: 42 comprobaciones contra `simulador_esp32.py` (serial de 25 MACs, incluida la de la placa de prueba `e08cfee64440` -> `fito-YUBUD6`; secreto base64url; firma de flota con claves cortas y de 100 caracteres; respuesta al reto). `pio run -e esp32dev` compila sin advertencias propias. La lógica contra el servidor real se prueba en la placa.

## Límites

- Borrar toda la flash cambia el secreto con el mismo serial: el registro da 409. Se arregla reiniciando el backend de desarrollo (H2) o borrando la fila; falta `DELETE /admin/dispositivos/{serial}`.
- HTTP sin TLS en la red local: el secreto cruza en claro una vez (D87).
- QR con 3 px de margen claro, por debajo de los 4 módulos de la norma; suficiente para los celulares actuales.
- Cola en RAM: se pierde al reiniciar (D92).
- Sensores simulados; MQTT queda para la siguiente iteración.
- El hash se prueba con una implementación portable; el de mbedTLS se verifica en el chip al arrancar (autotest RFC 4231). La compilación nativa de PlatformIO (`env:native`) no se usó porque la máquina no tiene `g++`.
