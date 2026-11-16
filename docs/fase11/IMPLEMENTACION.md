# Fase 11: la OLED duerme y saluda

Cuaderno de implementación (como `docs/fase10/`). Pedido: Santiago, 16-nov. Dos cosas que el front ya tenía o que la fase 8 dejó pedidas, y que la matera no hacía:

1. **De noche la planta duerme.** Desde la fase 8 el diagnóstico trae `periodo` (`DIA` o `NOCHE`), y la sección "Qué deben hacer el front y el firmware" de `docs/fase8/` pedía dormir a la mascota. La OLED seguía mostrando la planta despierta y los lux como un número más.
2. **El saludo.** El front saluda al tocar la mascota (bloque `SALUDO` de `js/componentes/mascota-tiempo.js`, F47 del front), con la nota "pendiente de calcar en el firmware". Ahora lo hace la OLED con una pulsación corta del botón BOOT.

Regla de Santiago (12-nov): la mascota se ve exactamente igual en la OLED y en el front. Mismas poses, mismas animaciones, mismos tiempos. Los números se definieron aquí, en el firmware, y el front los calca (F49 del front). No cambia el contrato HTTP, ni una línea de Java, ni la base.

## Qué hay

| Pieza | Dónde |
|---|---|
| Los números y las reglas puras: saludo, respiración, zzz, pulsación corta | `firmware/src/nucleo/fito_nucleo.{h,c}` (`FITO_SALUDO_DY`, `FITO_SALUDO_OJOS`, `fito_saludo_cuadro`, `fito_respira_dy`, `fito_zzz_cuadro`, `fito_pulsacion_corta`) |
| Sus pruebas sin placa | `firmware/test/arnes.c` (`saludo`, `respira`, `zzz`, `pulsacion`), `firmware/test/probar_nucleo.py` (sección 9) |
| El girasol dormido | `firmware/sprites/dibujar.py` (`girasol("dormido")`), `sprites/arte/girasol_dormido.txt`, `src/sprites.h` |
| La planta dormida, el zzz, "duerme" y el saludo en la OLED | `firmware/src/pantalla.{h,cpp}` (`lNoche`, `duermeAhora`, `dyExtra`, `ZZZ`, `pintarZzz`, `CARA_GIRASOL_DORMIDO`, `pantalla::saludar`; `pantalla::lecturas` recibe `noche`) |
| Leer `periodo` de la respuesta 201 | `firmware/src/cliente_api.{h,cpp}` (`api::enviarLectura(..., bool &noche)`), `firmware/src/main.cpp` (`ultimaNoche`, junto a `ultimoEstado`) |
| Pulsación corta del BOOT | `firmware/src/boton.{h,cpp}` (`boton::ciclo(cumplido, corta)`), `configuracion.h` (`BOTON_CORTA_MS`), `main.cpp` (`atenderBoton`) |
| El espejo en Python y los cuadros para comparar | `firmware/sprites/previsualizar.py` (`Lecturas(..., noche=, saludos=)`, `noche_y_saludo()`) |

## Los números

Todo en píxeles del sprite de 48x64 y en milisegundos de `millis()`. Los mismos en `fito_nucleo.c`, `pantalla.cpp`, `previsualizar.py` y, en el front, `mascota-tiempo.js` (`NOCHE`, `SALUDO`) y `mascota-poses.js` (`GIRASOL_DORMIDO`).

**Planta dormida** (cuando el último diagnóstico dijo `NOCHE`):

| Qué | Valor |
|---|---|
| Ojos | factor de parpadeo 0: el ojo queda en 1 px de alto (`max(1, lround(alto * 0))`), 5 px de ancho en la cara grande y 3 en la de la matera |
| Boca, cejas | las del estado (con su transición de 400 ms); sin lágrima |
| Balanceo, parpadeo, idle | ninguno: seno 0, sin corona que gira, sin cabeceo ni mirada, sin flor del cactus, sin inflarse la suculenta, helecho en su cuadro 0, sin mordisco |
| Respiración | el cuerpo entero (lo que está sobre la matera, y la cara grande con él) sube 1 px de 750 a 2249 ms de cada 3000 ms; el resto del periodo, 0. Es `lround(0.5 - 0.5 cos(2 pi t / 3000))` con el borde en entero |
| zzz | tres cuadros de 1000 ms: una zeta, dos, tres, y vuelve. Zetas de 3, 4 y 5 px con la esquina en (37, 10), (40, 5) y (43, 0). Trazo de 1 px (arriba, diagonal, abajo) y, en la OLED, un borde apagado de 1 px para leerse sobre las hojas. No respira ni se balancea |
| Cuerpo | el sprite del estado (cuadro 0). Carnívora saludable: `carni_cerrada` (ojos en la fila 16). Girasol saludable: `girasol_dormido`, cabeza en (23, 21) con radio 10, 12 pétalos girados 8 grados y `caida` 0,25; la cara va en (23, 21) |
| Fila de la luz en la OLED | el icono del sol y "duerme" (fuente chica, centrado en la fila) en vez del número de lux |

**Saludo** (pulsación corta de BOOT), copiado del bloque `SALUDO` del front tal cual:

| Cuadro | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `DY` (px, negativo arriba) | -2 | -4 | -5 | -5 | -4 | -2 | 0 | +1 | 0 | 0 | 0 | 0 |
| `OJOS` (factor de parpadeo) | 1 | 1 | 0,5 | 0 | 0,5 | 1 | 0,5 | 0 | 0,5 | 1 | 1 | 1 |

50 ms por cuadro, 600 ms en total. Cara contenta (`CARA_NIVEL[0]`) sin transición, en cualquier estado. El balanceo sigue.

## Decisiones

**D160. La noche la decide el servidor; el firmware solo la lee.** `api::enviarLectura` lee `diagnostico.periodo` de la respuesta 201 y devuelve `noche = true` solo si dice exactamente `"NOCHE"`. `main.cpp` lo guarda en `ultimaNoche` en el mismo lugar donde guarda `ultimoEstado` y lo pasa a `pantalla::lecturas`. Sin el campo (un servidor anterior a la fase 8) es de día. Descartado: decidir la noche en el chip con el lux del momento o con la hora NTP. Serían dos reglas de noche distintas (la del servidor espera 2 h de oscuridad, D126) y la OLED podría dormir mientras la app dice que es de día, contra la regla de paridad. El firmware no usa MQTT (solo HTTP), así que no hay otro lugar de dónde leerlo.

**D161. Una pose dormida para las seis familias, en el cuerpo de cada una.** Dormir es la misma receta en todas: ojos cerrados, todo quieto, respiración y zzz. Lo que cambia es el cuerpo, que es el del estado en su cuadro 0: una planta seca de noche sigue viéndose seca (el servidor ya no la degrada por luz, pero sí por humedad o temperatura). La boca y las cejas siguen al estado; la lágrima no (dormida no llora). Dos excepciones, porque en saludable el cuerpo de día no se lee como dormido: la carnívora cierra la trampa (`carni_cerrada`, que ya existía) y el girasol baja la cabeza con un sprite nuevo, `girasol_dormido` (384 B de PROGMEM). Se dibujó con `petalos()` y `disco()` como los demás para que el front lo calque con los mismos números (`P.GIRASOL_DORMIDO`). Descartado: usar `girasol_riesgo_0` de noche (le faltan pétalos: se vería mal, no dormido) y un sprite dormido por familia (seis sprites más para algo que los ojos cerrados ya dicen en cuatro de ellas).

**D162. La respiración es una regla entera.** Sube 1 px en la mitad central del periodo de 3 s (750 a 2249 ms). Es lo mismo que `lround(0.5 - 0.5 cos)`, salvo en los dos bordes exactos, donde el coseno en coma flotante cae a un lado u otro según el lenguaje. Con la regla entera la OLED, `previsualizar.py` y el front dan el mismo píxel en cada milisegundo, y `probar_nucleo.py` lo verifica contra el coseno en 298 instantes. Se mueve todo lo que está sobre la matera, como el salto del saludo y el cabeceo del girasol: la matera y la cara de la matera (común y helecho) no.

**D163. El zzz va fijo en la esquina de arriba a la derecha.** Es la zona más libre de los 18 sprites que se ven de noche (medido sobre los `.txt`: de 5 a 35 filas vacías entre x 34 y 47) y queda lejos del texto, que empieza en x 50. No se ancla a la cabeza de cada familia: con un solo lugar es la misma animación para todas y no hay que mantener una tabla por familia. En la OLED cada zeta lleva un borde apagado de 1 px porque en el girasol y el helecho cae sobre hojas; en el front no hace falta (es vector de color). El zzz va a 1 s por cuadro para ir al paso de la respiración (3 cuadros = 3 s).

**D164. La OLED dice "duerme" en la fila de la luz y no marca la luz.** Así no parece que la planta tenga un problema por falta de luz a las 10 pm. En grande no cabe (son 71 px y hay 67) y con los lux al lado ("duerme 3lx") el número se confunde con la unidad; la app muestra los lux de ahora y la luz del día. La etiqueta de abajo sigue siendo el estado del servidor, que de noche ya no se degrada por luz (D121). El estado del parámetro de luz (`BAJO` si el día fue malo) no se muestra en la OLED, como antes.

**D165. Orden de prioridad: aviso de sensores, saludo, noche.** Con el aviso de la fase 10 activo ("no te siento ...") la planta no duerme: el aviso necesita la cara preocupada (`nivelVisible()`) y dormirla lo taparía. El saludo gana a la noche: la planta se despierta, salta con la cara contenta y vuelve a dormir al terminar. La carnívora dormida no muerde, ni sola ni por lectura nueva (`pantalla::mordisco` sale si `lNoche`). Al despertar con el día, el reloj del parpadeo y del mordisco siguió corriendo, así que no hay saltos.

**D166. El saludo de la OLED es el bloque `SALUDO` del front, copiado.** `FITO_SALUDO_DY` y `FITO_SALUDO_OJOS` son los dos arreglos del front, número por número, y `probar_nucleo.py` los fija. En `pintarLecturas`, el cuadro del saludo pone `dyExtra` (que cada familia suma al `dy` de `cuerpo()` y de la cara grande, y a la flor del cactus) y reemplaza el factor de parpadeo; la cara es `CARA_NIVEL[0]` sin pasar por la transición. En el girasol el salto reemplaza al cabeceo (la mirada sigue), igual que en el front. El reloj del parpadeo normal sigue corriendo durante el saludo. No hay un saludo por familia: un salto y un parpadeo sirven para todas (F47 del front).

**D167. Pulsación corta: soltar BOOT entre 30 ms y 1 s.** `boton::ciclo` avisa `corta` en el ciclo en que se suelta, si se sostuvo entre `FITO_BOTON_REBOTE_MS` (30 ms, para el rebote) y `BOTON_CORTA_MS` (1000 ms). Desde 1 s empieza la cuenta regresiva del reinicio en la OLED (antes era un `1000` suelto en `atenderBoton`, ahora es esta constante), y a los 5 s (`BOTON_REINICIO_MS`) se borra la red, como siempre. Un `static_assert` impide que la corta pase al reinicio. Soltar entre 1 y 5 s cancela la cuenta y no saluda. El saludo solo se pinta en la pantalla de lecturas (`modo == Lecturas`): en el QR, el portal o un error no hace nada. No habla con el servidor. Descartado: mandar `POST /plantas/{id}/mascota/acciones` con `ACARICIAR` (fase 9). La matera no tiene el id de la planta ni un token de usuario, solo el del dispositivo; queda en límites.

**D168. Las reglas en el núcleo, con pruebas; el dibujo, con espejo.** Lo que decide un número (cuadro del saludo, respiración, zzz, pulsación corta) está en `fito_nucleo.c`, que compila con `gcc` sin placa y se prueba en `probar_nucleo.py` contra los valores esperados y contra la fórmula del coseno. Lo que dibuja está en `pantalla.cpp` y tiene su espejo a mano en `previsualizar.py`, como desde la fase 7. `noche_y_saludo()` guarda además la mascota sola (48x64, `mascota/dormida_<familia>_<estado>.png` y `mascota/saludo_<familia>_<cuadro>.png`) para que `tools/comparar-mascotas.py --oled` del front la ponga junto a la suya.

**D169. Mismos instantes en los dos lados para comparar.** La dormida se compara a los 2500 ms (respiración abajo y tres zetas) y el saludo empieza a los 2500 ms, un cuadro cada 50 ms. Sin parpadeos ni mordiscos alrededor, así lo único que difiere entre la OLED y el front es el trazo: el sprite es de 1 bit en la rejilla y el front es vector. Coincidencia de silueta (IoU de las filas 0 a 45): dormida 0,77 en promedio (cactus 0,88 a 0,89; helecho 0,51 a 0,55, por sus líneas de 1 px, igual que de día en F45 del front), saludo 0,74 en promedio en 72 cuadros. Imágenes en el front: `docs/mascotas/dormida.png`, `docs/mascotas/saludo.png` y `docs/mascotas/metricas-noche-saludo.txt`.

## Pruebas

- `python3 firmware/test/probar_nucleo.py`: 498 comprobaciones, ninguna falla (eran 139; la sección 9 agrega 359: los 12 cuadros del saludo en tres instantes cada uno y su fin, la respiración en sus bordes y contra el coseno en 298 instantes, el zzz con desborde de `millis()` y la pulsación corta en sus bordes).
- `pio run -e esp32dev` con `local.ini` de `local.ini.ejemplo` (`sensores = reales`, la misma configuración que la placa): compila sin errores. Flash **86,4 %** (1.131.973 de 1.310.720 bytes; antes 1.130.537, +1.436 B: el sprite del girasol dormido son 384). RAM 16,0 % (52.588 bytes). No se flasheó ni se tocó `/dev/ttyUSB0`.
- `python3 firmware/sprites/previsualizar.py /tmp/previa`: además de lo de siempre, `40_dormida_<familia>_<estado>.png` (18), `41_saludo_<familia>_<cuadro>.png` (cuadros 0, 3 y 7), `anim_dormida_<familia>.gif`, `anim_noche_saludo.gif` (de noche, el girasol saluda dos veces y se vuelve a dormir) y `anim_anochece.gif` (el cactus se duerme a los 3 s) y la carpeta `mascota/` con los cuadros sueltos.
- Falta: verlo en la OLED real y pulsar el botón en la placa.

## Límites

- La noche llega con la siguiente respuesta 201 (cada 5 s con red). Sin red, la OLED se queda en el último periodo que supo.
- Una planta 2 h a oscuras de día también duerme (límite de la fase 8: sin reloj ni ubicación no se distingue la noche de un armario).
- El saludo de la OLED no le avisa al servidor: la mascota de la fase 9 no se entera de que la saludaron en la matera (D167).
- `previsualizar.py` sigue siendo un espejo a mano; las reglas con números sí tienen prueba (núcleo), el dibujo no.
- Con el aviso de sensores no duerme (D165), aunque sea de noche.

## Para el documento de arquitectura

La fase no toca el backend: el firmware empieza a usar un campo que el contrato ya traía desde la fase 8 (`diagnostico.periodo`). El cambio vive en la presentación del chip (`pantalla.cpp`) y en la lógica pura del firmware (`fito_nucleo.c`), que se prueba sin placa. El front calca los mismos números en su propio componente; la paridad se verifica con una herramienta que pone los cuadros de los dos lados juntos.
