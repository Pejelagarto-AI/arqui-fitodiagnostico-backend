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

Pasos, en orden:

1. **Modo calibración.** El registro por serie muestra los milivoltios crudos de cada entrada analógica, además del valor convertido.
2. **`Potenciometro` pasa a `SensorAnalogico` con tabla de calibración.** El potenciómetro, la LDR y la sonda entregan todos un voltaje; solo cambia la curva de conversión, que es un dato y no código. La tabla son pares `mV:valor` leídos de `local.ini` (por ejemplo `cal_humedad = 2900:0, 1200:100`), interpolados por tramos; admite curvas descendentes, porque en estos módulos el voltaje suele bajar con más agua o más luz. Pasar del potenciómetro al sensor real es cambiar la tabla. La calibración va en configuración y no en `configuracion.h` porque cada sensor físico sale distinto.
3. **Adaptador `TemperaturaDht22`.** Única clase nueva de sensor: el DHT22 habla un protocolo digital propio (librería *DHT sensor library* de Adafruit). Se lee cada 2 s como mínimo y devuelve `false` si no responde (D99). La humedad del aire que también mide no se envía como `humedad`: no es la del sustrato.
4. **Decorador `AlimentadoPorPin`.** La sonda resistiva se corroe por electrólisis si queda alimentada. El decorador envuelve cualquier `SensorDeMagnitud`, enciende el pin de alimentación unos milisegundos, lee y apaga. Se agrega sin modificar `SensorAnalogico`.
5. **Calibración.**
   - Humedad: sonda al aire (0 %) y en agua (100 %); después, afinar con tierra seca y recién regada.
   - Luz: de tres a cinco puntos (tapada, interior, junto a la ventana, linterna) anotando los mV del sensor y los lux de una app de luxómetro en el celular. La LDR no es lineal: varios puntos en la tabla.
   - Temperatura: no necesita.
6. **Composición** con `sensores = reales` en `local.ini` y prueba completa en la placa con tierra.
