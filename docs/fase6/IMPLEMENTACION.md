# Fase 6: sensores físicos de la matera

Cuaderno de implementación (como `docs/fase5/`). La fase 5 dejó el firmware con sensores simulados; esta fase prepara la entrada de hardware real sin tocar la máquina de estados ni el backend. Primer paso: tres potenciómetros como sensores. Siguiente: los sensores reales (sección [Plan](#plan-sensores-reales)). Guía de uso y cableado: [`firmware/README.md`](../../firmware/README.md#sensores).

## Qué hay

| Pieza | Dónde |
|---|---|
| Puerto de una magnitud | `firmware/src/sensor_de_magnitud.h` (`SensorDeMagnitud`) |
| Adaptador potenciómetro (ADC1, escala fija, promedio de 16 muestras) | `firmware/src/potenciometro.*` |
| Puerto `Sensores` armado con tres magnitudes (patrón compuesto) | `firmware/src/sensores_compuestos.*` |
| Recta de milivoltios a magnitud, con recorte en los extremos | `fito_escalar_mv` en `firmware/src/nucleo/` |
| Composición de los sensores | inicio de `firmware/src/main.cpp` |
| Selección | `sensores = simulados \| potenciometros` en `local.ini` |

```
Sensores (puerto)  <-  SensoresSimulados
                   <-  SensoresCompuestos  ->  SensorDeMagnitud (puerto, una magnitud)  <-  Potenciometro
```

## Decisiones

**D98. Un puerto por magnitud.** Los sensores reales son dispositivos distintos por magnitud (una sonda para la humedad del sustrato, un sensor de luz, un DHT22 para la temperatura). Un `SensoresReales` único mezclaría tres protocolos en una clase y obligaría a cambiarla cada vez que cambie uno. Con `SensorDeMagnitud` cada dispositivo es un adaptador pequeño y se pueden mezclar (un DHT22 real con potenciómetros para lo demás). `SensoresCompuestos` reúne los tres e implementa el puerto `Sensores` de siempre, así que la máquina de estados, el cliente de la API y la pantalla no cambian. Un dispositivo que mide dos magnitudes se expone como dos objetos sobre el mismo chip.

**D99. El puerto `Sensores` admite fallas y recibe los rangos.** `leer` devuelve `bool`: un sensor real puede no responder, y como el servidor exige las tres magnitudes, esa lectura se descarta y se anota la causa por serie, en lugar de enviar un valor inventado. `adoptarRangos` pasa a la interfaz con implementación vacía por defecto: antes `main.cpp` llamaba a `SensoresSimulados` directamente, una dependencia de un adaptador concreto desde el núcleo. `SensoresCompuestos` usa los rangos solo para describir la lectura en el registro; el diagnóstico lo sigue haciendo el servidor.

**D100. El sensor entrega una escala fija; el servidor decide.** El potenciómetro recorre 0 a 100 % de humedad, 0 a 2000 lux y 0 a 50 °C, como lo haría un sensor real, y no una escala relativa al rango óptimo de la especie. Clasificar como óptimo, riesgo o crítico es regla del dominio del backend; el firmware no la duplica. Entre 100 y 3100 mV la conversión es lineal y fuera de ahí se recorta al extremo, porque el ADC del ESP32 no es lineal en las puntas.

**D101. Pines del ADC1 y selección en la composición.** Las entradas analógicas van a GPIO 34, 35 y 32 (ADC1): el ADC2 no lee con el Wi-Fi encendido, y la matera lo tiene siempre encendido (en S3: 4, 5 y 6; en C3: 0, 1 y 3). Qué adaptador hay detrás de cada puerto se decide en un solo lugar, la composición de `main.cpp`, a partir de `local.ini`, igual que `FITO_CATALOGO_FUENTE` decide la tabla de referencia en el backend. Un valor inválido detiene la compilación con un mensaje.

## Pruebas

`python3 firmware/test/probar_nucleo.py`: 50 comprobaciones, 8 de ellas de `fito_escalar_mv` (extremos, recorte fuera de rango, punto medio, otra escala y rango vacío). Compila en `esp32dev`, `esp32-s3-devkitc-1` y `esp32-c3-devkitm-1` en los dos modos. Probado en la placa `fito-YUBUD6`: con `sensores = potenciometros`, mover una perilla cambia la lectura siguiente y el estado de la planta en el front.

## Límites

- La escala de los potenciómetros es lineal y fija; los sensores reales necesitan su propia curva (siguiente sección).
- Elegir los sensores exige recompilar. Es fijo por modelo de hardware, así que se deja en la composición; la calibración, que cambia por unidad, pasa a configuración en el plan.

## Plan: sensores reales

Hardware disponible:

| Magnitud | Sensor | Salida |
|---|---|---|
| Humedad del sustrato (%) | Sonda resistiva de dos patas con módulo LM393 (tipo YL-69 / FC-28) | Analógica (AO) |
| Luz (lux) | Módulo de fotorresistencia (LDR) con LM393 | Analógica (AO) |
| Temperatura (°C) | DHT22 (AM2302) en placa con pull-up | Digital, un cable |

Cableado previsto, todo a **3V3** (a 5 V la salida AO puede superar lo que admite el pin). Los pines DO de los módulos LM393 no se usan: dan un sí o no según un umbral, y el nivel lo decide el servidor.

| Sensor | Pin del ESP32 |
|---|---|
| Humedad, AO | GPIO 34 |
| Luz, AO | GPIO 35 |
| DHT22, `out` | GPIO 32 |
| Alimentación del módulo de humedad (VCC) | GPIO 33 |

Pasos, en orden (hechos el 1 al 4 y el 6: ver [Sensores reales](#sensores-reales); falta el 5, medir en la matera):

1. **Modo calibración.** El registro por serie muestra los milivoltios crudos de cada entrada analógica, además del valor convertido.
2. **`Potenciometro` pasa a `SensorAnalogico` con tabla de calibración.** El potenciómetro, la LDR y la sonda entregan todos un voltaje; solo cambia la curva de conversión, que es un dato y no código. La tabla son pares `mV:valor` leídos de `local.ini` (por ejemplo `cal_humedad = 2900:0, 1200:100`), interpolados por tramos; admite curvas descendentes, porque en estos módulos el voltaje suele bajar con más agua o más luz. Pasar del potenciómetro al sensor real es cambiar la tabla. La calibración va en configuración y no en `configuracion.h` porque cada sensor físico sale distinto.
3. **Adaptador `TemperaturaDht22`.** Única clase nueva de sensor: el DHT22 habla un protocolo digital propio (librería *DHT sensor library* de Adafruit). Se lee cada 2 s como mínimo y devuelve `false` si no responde (D99). La humedad del aire que también mide no se envía como `humedad`: no es la del sustrato.
4. **Decorador `AlimentadoPorPin`.** La sonda resistiva se corroe por electrólisis si queda alimentada. El decorador envuelve cualquier `SensorDeMagnitud`, enciende el pin de alimentación unos milisegundos, lee y apaga. Se agrega sin modificar `SensorAnalogico`.
5. **Calibración.**
   - Humedad: sonda al aire (0 %) y en agua (100 %); después, afinar con tierra seca y recién regada.
   - Luz: de tres a cinco puntos (tapada, interior, junto a la ventana, linterna) anotando los mV del sensor y los lux de una app de luxómetro en el celular. La LDR no es lineal: varios puntos en la tabla.
   - Temperatura: no necesita.
6. **Composición** con `sensores = reales` en `local.ini` y prueba completa en la placa con tierra.

## Sensores reales

Ejecución del plan, pasos 1 a 4 y 6. Procedimiento de calibración (paso 5): [`firmware/CALIBRACION.md`](../../firmware/CALIBRACION.md).

### Qué hay

| Pieza | Dónde |
|---|---|
| Modo calibración (`calibracion = 1`): mV crudos y valor convertido por serie | `registrarCrudos` y `calibrarFueraDeOperando` en `firmware/src/main.cpp`; `describirCrudo` en los puertos |
| Adaptador de cualquier sensor de voltaje con tabla de calibración (antes `Potenciometro`) | `firmware/src/sensor_analogico.*` |
| Interpolación por tramos de mV a magnitud, con recorte en los extremos | `fito_interpolar_cal` en `firmware/src/nucleo/` (reemplaza a `fito_escalar_mv`) |
| Lectura y validación de las tablas de `local.ini` al compilar | `firmware/tabla_cal.py`, llamado desde `extra_script.py` |
| Adaptador DHT22 (librería de Adafruit) | `firmware/src/temperatura_dht22.*` |
| Decorador que alimenta un sensor solo mientras se lee | `firmware/src/alimentado_por_pin.*` |
| Composición `sensores = reales` | inicio de `firmware/src/main.cpp` |
| Selección | `sensores = simulados \| potenciometros \| reales`, `calibracion`, `cal_humedad`, `cal_luz` en `local.ini` |

```
Sensores (puerto)  <-  SensoresSimulados
                   <-  SensoresCompuestos  ->  SensorDeMagnitud (puerto)  <-  SensorAnalogico (tabla mV:valor)
                                                                          <-  TemperaturaDht22
                                                                          <-  AlimentadoPorPin  ->  SensorDeMagnitud

reales:  humedad = AlimentadoPorPin(SensorAnalogico(GPIO 34, cal_humedad), GPIO 33)
         luz = SensorAnalogico(GPIO 35, cal_luz)      temperatura = TemperaturaDht22(GPIO 32)
```

### Decisiones

**D102. Modo calibración por configuración, apagado por defecto.** `calibracion = 1` en `local.ini` agrega una línea `cal` por lectura con lo que midió cada sensor antes de convertir y el valor convertido. Apagado, el registro queda igual al del simulador. Se activa al compilar y no con un comando por serie: calibrar ya exige recompilar para poner la tabla nueva. En modo calibración los sensores se leen también fuera de OPERANDO (con el QR de reclamo o sin servidor), así se calibra sin vincular la planta; esas lecturas no se envían. Cada puerto describe su dato crudo (`describirCrudo`, `describirCrudos`) con una implementación vacía por defecto, igual que `adoptarRangos` en D99: los simulados no cambian. `SensoresCompuestos` lee las tres magnitudes aunque una falle, para que la línea muestre cuál falló.

**D103. La curva es un dato: `SensorAnalogico` con tabla.** El potenciómetro, la sonda y la LDR son la misma clase con otra tabla de pares `mV:valor`. `fito_interpolar_cal` interpola por tramos, admite valores que bajan (curvas descendentes) y recorta en el primer y el último punto, como hacía la recta de D100. Por eso `fito_escalar_mv` desaparece: la escala del potenciómetro es una tabla de dos puntos (100 mV y 3100 mV) con el mismo resultado, probado con los mismos casos. La función del núcleo exige mV ascendente; ordenar lo hace el compilador de la tabla.

**D104. Las tablas se validan al compilar, no en la placa.** `tabla_cal.py` lee `cal_humedad` y `cal_luz`, las ordena y rechaza puntos mal escritos, mV repetidos o fuera de 0 a 3300, menos de 2 o más de 8 puntos. El error detiene la compilación con el motivo; en la placa nunca hay una tabla inválida que dé lecturas raras. Las tablas viajan como inicializadores de C en `config_local.h`, igual que las cadenas de `[fito]`. Las tablas por defecto de `platformio.ini` son provisionales (valores típicos de estos módulos a 3V3); cada unidad pisa las suyas en `local.ini`, que no se versiona porque cada sensor físico sale distinto.

**D105. DHT22 con la librería de Adafruit, 2 s y rango sano.** `TemperaturaDht22` lee el chip como mucho cada 2 s y, entre medias, repite el último resultado. Devuelve `false` si no responde o si da algo fuera de -40 a 80 °C, el rango del DHT22: eso es ruido en el cable (D99). `begin()` se llama en la primera lectura, no en el constructor, porque el objeto es global y se construye antes de que arranque Arduino. La humedad del aire que también mide sale solo en el dato crudo; no es la del sustrato y no se envía.

**D106. `AlimentadoPorPin` es un decorador.** Envuelve cualquier `SensorDeMagnitud`: pone el pin en alto, espera 30 ms a que el módulo se asiente, lee el sensor envuelto y apaga. `SensorAnalogico` no sabe que lo alimentan. Si el VCC de la sonda va al riel de 3V3, el pin cambia sin efecto y todo sigue funcionando; la sonda solo se corroe antes. El pin se configura en la primera lectura por la misma razón que el DHT22.

**D107. Tres modos en la composición.** `sensores` admite `simulados`, `potenciometros` y `reales`. `extra_script.py` rechaza otro valor con un mensaje y `main.cpp` tiene un `#error` de respaldo si falta la definición. Los pines quedan por magnitud (`PIN_HUMEDAD`, `PIN_LUZ`, `PIN_TEMPERATURA`) y no por dispositivo: el GPIO 32 es la perilla de temperatura en un modo y el DHT22 en el otro. `local.ini.ejemplo` queda en `reales`.

**D108. El Wi-Fi de `local.ini` respalda al guardado.** Fuera del plan, salió en la prueba: la placa tenía en NVS la red de otra prueba, que manda sobre la de compilación, y caía al portal sin probar el hotspot de `local.ini`. Ahora, si la red guardada no conecta en 20 s y la de compilación es otra, se prueba esa antes del portal; si conecta, queda guardada junto con el servidor de `local.ini`.

### Pruebas

`python3 firmware/test/probar_nucleo.py`: 78 comprobaciones. Las de tablas: la recta del potenciómetro con los casos de antes, una curva descendente (sonda), una de seis tramos (LDR), un punto, tabla vacía, valores negativos, y la lectura de `local.ini` (orden, separadores y siete tablas inválidas rechazadas). Compila `esp32dev` en los tres modos; un valor inválido de `sensores` detiene la compilación con el mensaje. No se compilaron S3 ni C3: este portátil no tiene sus toolchains y bajarlos no cabía en la sesión.

Prueba en la placa `fito-YUBUD6` (12-nov, sensores recién cableados, `sensores = reales`, `calibracion = 1`):

- Sonda al aire: 3134 mV, 0 %. Con GPIO 33 en bajo la entrada cae a 142 mV (el piso del ADC) y en alto sube a 3134 mV: el VCC de la sonda está en GPIO 33 y el decorador la apaga entre lecturas.
- LDR en interior: de 760 a 1135 mV según la luz del cuarto (de 450 a 1080 lux con la tabla provisional). Dos lecturas de 142 mV (linterna o un contacto suelto en la LDR, sin confirmar).
- DHT22: una sola lectura buena (24.4 °C, humedad del aire 50 %) en el primer arranque; después nunca contesta. Con la depuración de la librería: `timeout waiting for start signal high pulse`, la línea se queda en alto. Una lectura a mano con pulsos de inicio de 1.1, 2, 5 y 20 ms tampoco obtiene respuesta. Causa probable: contacto intermitente en el `+`, el `-` o el `out` del DHT22.
- Red y servidor: conecta al hotspot (con D108), se registra de nuevo en la base nueva (404 al reto, registro 201), muestra el QR y, ya reclamada, usa el token guardado. Las lecturas se descartan por la temperatura (`lectura descartada: falla el sensor de temperatura`), como manda D99: no hubo envíos 201 con los sensores reales.

### Límites

- Las tablas por defecto son provisionales hasta calibrar en la matera (paso 5 del plan, [`CALIBRACION.md`](../../firmware/CALIBRACION.md)).
- Con el DHT22 sin responder no sale ninguna lectura: el servidor exige las tres magnitudes. Mientras se arregla el cable se puede volver a `potenciometros` o a `simulados`, pero no mezclar un valor inventado.
- El protocolo del DHT22 es de microsegundos y se lee por sondeo; aun con el sensor bien cableado puede fallar alguna lectura suelta. Una falla suelta solo descarta esa lectura.
- Calibrar exige recompilar. Mover las tablas a NVS o al portal cautivo sería el paso siguiente si hay más de una matera.

