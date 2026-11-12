# Calibración de los sensores reales

Con `sensores = reales` la matera convierte milivoltios en humedad y en lux con dos tablas de `local.ini`. Las que trae `platformio.ini` son **provisionales**: valores típicos de estos módulos a 3V3, no medidos en esta matera. Cada sonda y cada LDR sale distinta, así que hay que medirlas una vez por unidad. El DHT22 no se calibra.

## 1. Activar el modo calibración

En `firmware/local.ini`, sección `[fito]`:

```ini
sensores = reales
calibracion = 1
```

Compilar, subir y abrir el monitor serie:

```bash
PIO=~/.local/share/fito-pio/bin/pio
cd firmware
$PIO run -e esp32dev -t upload
$PIO device monitor
```

Cada 5 s sale una línea así, en cualquier fase (también con el QR de reclamo en pantalla o sin servidor):

```
[2026-11-12T22:20:06Z] cal humedad 3134 mV = 0.0 | luz 963 mV = 592.5 | temperatura dht22 hr aire 50 % = 24.4
```

Lo que importa para calibrar es el número de **mV** de cada sensor. El valor después del `=` es el que da la tabla actual. Si el DHT22 no contesta, sale `dht22 sin respuesta = FALLA` y esa lectura no se envía.

Cada línea ya es el promedio de 16 muestras. Aun así el número baila: anote el valor típico de unas 5 líneas seguidas, no una sola.

## 2. Humedad del sustrato (sonda)

La sonda se alimenta desde GPIO 33 solo mientras se lee. Calibre con el mismo cableado con que va a quedar: si cambia el VCC de GPIO 33 al riel de 3V3, o al revés, vuelva a calibrar.

1. **Al aire**, sonda seca y limpia: anote los mV. Es el 0 %.
2. **En un vaso de agua**, metida hasta la línea de los componentes, sin mojar el módulo: anote los mV. Es el 100 %.
3. Ponga esos dos puntos en `local.ini`:
   ```ini
   cal_humedad = 3130:0, 1250:100
   ```
4. **Afinar en tierra.** Clave la sonda en la matera con la tierra seca y anote los mV. Riegue, espere un par de minutos y anote otra vez. Al aire y en agua son los extremos; en tierra la sonda casi nunca llega a ellos. Si la tierra recién regada da, por ejemplo, 1700 mV y la seca 2800 mV, puede reemplazar los puntos o agregarlos:
   ```ini
   cal_humedad = 3130:0, 2800:15, 1700:80, 1250:100
   ```
   Los porcentajes de la tierra los decide usted: "seca para el poto" y "recién regada" son lo que el servidor va a comparar con el rango de la especie.

## 3. Luz (LDR)

La LDR no es lineal: hacen falta de 3 a 5 puntos. Use una app de luxómetro en el celular (por ejemplo *Lux Light Meter* o *Physics Toolbox*), con el sensor del celular al lado de la LDR y apuntando igual.

| Situación | Anote |
|---|---|
| LDR tapada con el dedo | mV del ESP32 y lux de la app (casi 0) |
| Interior lejos de la ventana | mV y lux |
| Interior junto a la ventana | mV y lux |
| Linterna del celular a unos 10 cm | mV y lux |
| (opcional) Al sol | mV y lux |

Luego:

```ini
cal_luz = 3050:0, 2400:60, 1500:300, 700:1500, 200:8000
```

Primero mire hacia dónde va el voltaje. En la mayoría de estos módulos baja con más luz; si el suyo sube, la tabla igual sirve: solo cambian los números.

## 4. Temperatura (DHT22)

No se calibra: el DHT22 entrega grados Celsius ya convertidos, con ±0.5 °C. La humedad del aire que también mide (`hr aire`) se muestra solo como dato: el servidor pide la humedad del sustrato, que es la de la sonda.

Si sale siempre `dht22 sin respuesta`, revise el cableado: `+` a 3V3, `-` a GND y `out` a GPIO 32 (no a 33, que es la alimentación de la sonda). Pruebe a apretar o cambiar los jumpers.

## 5. Reglas de las tablas

- Pares `mV:valor` separados por coma (o punto y coma), en cualquier orden: se ordenan al compilar.
- De 2 a 8 puntos, mV enteros de 0 a 3300 y sin repetir.
- Entre dos puntos se interpola en línea recta. Por debajo del primer mV vale lo del primero y por encima del último lo del último: nunca da valores fuera de la tabla.
- Una tabla mal escrita detiene la compilación con el motivo (por ejemplo `[fito] cal_luz: el mV 900 aparece dos veces`).
- Sin las líneas `cal_humedad` o `cal_luz` en `local.ini` se usan las provisionales de `platformio.ini`.

## 6. Terminar

Con las tablas puestas, vuelva a compilar y subir con `calibracion = 1` y compruebe que los valores convertidos tengan sentido (tierra seca da poco %, linterna da muchos lux). Después puede dejar `calibracion = 0`: el registro vuelve a ser una línea por lectura, igual que el del simulador. Las tablas se quedan en `local.ini`, que no se versiona: anótelas también en otro lado.
