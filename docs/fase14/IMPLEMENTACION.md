# Fase 14: la mascota completa en la OLED (firmware y simulador)

Cuaderno de implementación (como `docs/fase11/`). El profesor aprobó la mascota y pidió construirla completa. Regla fija de Santiago (12-nov): **lo que se ve en el front se ve igual en la OLED**: mismos personajes, poses, animaciones y tiempos. Esta fase es el lado de la placa; el front se hará después **calcando los números de este cuaderno**. No toca el backend, ni el dominio, ni el front: solo `firmware/`, `simulador/` y `docs/fase14/`. El servidor lo hace otro agente en paralelo (D180 a D189) con el contrato de abajo, que aquí se programó tal cual.

## Qué hay

| Pieza | Dónde |
|---|---|
| Parseo de la clave `mascota` de la 201 (con filtro de ArduinoJson) y la caricia `POST .../mascota/acciones` | `firmware/src/cliente_api.{h,cpp}` (`enviarLectura(..., EstadoMascota &)`, `acariciar`), `firmware/src/mascota.h` (`EstadoMascota`) |
| Lógica pura: textos a números, cola de eventos de a uno, qué fila mostrar, cuadros de cada animación, barra | `firmware/src/nucleo/fito_mascota.{h,c}` |
| Sus pruebas sin placa | `firmware/test/arnes.c` (`texto`, `cola`, `cuadro`, `desmayo`, `animacion`, `barra`), `firmware/test/probar_nucleo.py` (secciones 10 y 11) |
| Frases con personalidad | `firmware/sprites/frases.json` (fuente de verdad, copiada de la entrega de la U), `firmware/sprites/generar_frases.py` → `firmware/src/frases.h` |
| Dibujos nuevos (efectos, ojos en "x", accesorios) | `firmware/sprites/dibujar_mascota.py` → `firmware/sprites/arte/*.txt` → `generar.py` → `firmware/src/sprites.h` |
| La fila de frases, la barra, los efectos, el desmayo y los accesorios en la OLED | `firmware/src/pantalla.{h,cpp}` (`pantalla::mascota`, `pintarAccesorio`, `pintarEfecto`, `pintarBarraVitalidad`, `ANCLA_ACCESORIO`) |
| BOOT corto = acariciar de verdad | `firmware/src/main.cpp` (`acariciarDeVerdad`) |
| El espejo en Python y las previas | `firmware/sprites/previsualizar.py` (`mascota_completa()`), `docs/fase14/previas/` |
| Simulador | `simulador/simulador_esp32.py` (`texto_mascota`, `acariciar`, `--acariciar`), `simulador/README.md` |

### El contrato que se programó

La 201 de la lectura trae, si el servidor es nuevo, `"mascota": {"vitalidad": 64, "animo": "FELIZ", "racha": 3, "accesorio": "SOMBRERO", "eventos": [{"tipo": "REGADA", "ocurridoEn": "..."}, ...]}`. `animo` ∈ FELIZ, CANSADA, SEDIENTA, AHOGADA, CON_FRIO, CON_CALOR, A_OSCURAS, DESLUMBRADA, DESMAYADA, DORMIDA, SIN_DATOS. `accesorio` ∈ NINGUNO, SOMBRERO, MONO, GAFAS. `eventos` son los ocurridos desde la lectura anterior de esa matera, del más antiguo al más reciente, máximo 10; tipos: REGADA, SALIO_AL_SOL, DESMAYO, REVIVIO, ACARICIADA, RACHA, RIEGO_DECLARADO, RIEGO_CONFIRMADO, RIEGO_DESMENTIDO. Sin la clave `mascota` (servidor viejo) la OLED se ve como en la fase 11. La caricia es `POST /api/v1/dispositivos/{serial}/mascota/acciones` con el token de dispositivo y `{"tipo":"ACARICIAR"}`: 201, o 200 si es repetida dentro de 10 s; 401 se trata como en las lecturas.

## Los números que el front copia

Todo en píxeles del sprite de 48x64 (la zona de la mascota) o de la OLED de 128x64, y en milisegundos. Las constantes de tiempo están en `firmware/src/nucleo/fito_mascota.h`; las de posición, en `firmware/src/pantalla.cpp`.

### Constantes de tiempo y de cuadros

| Nombre | Valor | Para qué |
|---|---|---|
| `FRASE_ALTERNA_MS` (`FITO_FRASE_ALTERNA_MS`) | 3000 | sin evento, la fila de abajo alterna cada 3 s: estado del servidor (primeros 3 s del ciclo) y frase del ánimo |
| `FRASE_EVENTO_MS` (`FITO_FRASE_EVENTO_MS`) | 3000 | la frase de un evento se muestra 3 s y vuelve al ciclo; es también el turno de cada evento en la cola |
| `COLA_EV_MAX` | 10 | eventos que pueden esperar; con más, se pierde el más antiguo de los que esperan |
| `RAYOS_CUADRO_MS` / cuadros / vueltas | 250 / 3 / 2 | SALIO_AL_SOL: `rayos_0..2`, 1500 ms |
| `CORAZON_CUADRO_MS` / cuadros / vueltas | 300 / 2 / 3 | ACARICIADA: `corazon_0` (chico), `corazon_1` (grande), 3 latidos, 1800 ms; más el saludo en los primeros 600 ms |
| `DESTELLOS_CUADRO_MS` / cuadros / vueltas | 200 / 3 / 2 | REVIVIO: `destellos_0..2`, 1200 ms; más el saludo en los primeros 600 ms |
| `ESTRELLA_CUADRO_MS` / cuadros / vueltas | 300 / 3 / 2 | RACHA: `estrella_0..2`, 1800 ms |
| `DESMAYO_CUADRO_MS` / cuadros | 100 / 6 | DESMAYO: el cuerpo baja `DESMAYO_DY = 0, 0, 1, 1, 2, 2` px en 600 ms |
| `DESMAYADA_DY` | 2 | px que queda más abajo mientras el ánimo sea DESMAYADA (tras la caída y también si se llega ya desmayada) |
| Duración de la parte animada | SALIO_AL_SOL 1500, ACARICIADA 1800, REVIVIO 1200, RACHA 1800, DESMAYO 600; REGADA, RIEGO_CONFIRMADO y RIEGO_DESMENTIDO 0 | de noche, la mascota se despierta solo ese tiempo (`fito_ev_animacion_ms`) |
| Saludo (fase 11, sin cambios) | 12 cuadros de 50 ms, `DY` -2 -4 -5 -5 -4 -2 0 1 0 0 0 0, ojos 1 1 .5 0 .5 1 .5 0 .5 1 1 1 | lo reusan ACARICIADA y REVIVIO |
| Gota de riego (fase 7, sin cambios) | 500 ms de caída, 1600 ms de gusto | REGADA la dispara en la suculenta, como la subida de humedad |

### Constantes de posición

| Nombre | Valor | Para qué |
|---|---|---|
| `EFECTO_X`, `EFECTO_Y` | 37, 0 | esquina superior izquierda de los sprites de 11x11 de los efectos (donde va el zzz). El corazón de 9x8 va en (38, 1) |
| Aureola | negra de 1 px (los 8 vecinos del sprite) y el sprite encima en blanco | efectos y accesorios, para que se lean sobre hojas y pétalos |
| `BARRA_X`, `BARRA_Y`, `BARRA_W` | 50, 45, 78 | barra de vitalidad: una línea de 1 px; el largo relleno es `(vitalidad * 78 + 50) / 100` y el resto va punteado (un punto de por medio) |
| Fila de abajo | texto de 6x8 px en (50, 56), hasta 13 caracteres | el estado o la frase |
| Ojos en "x" | `ojo_x_5` en (ojoCentroX - 2, caraY - 2) en la cara grande; `ojo_x_3` en (17, 55) y (28, 55) en la matera; negro sobre el cuerpo | pose de desmayo |

### Los sprites nuevos

Todos 1 bit, `#` = encendido, `.` = transparente, en `firmware/sprites/arte/`. Los efectos y los accesorios se dibujan con aureola negra.

| Sprite | Tamaño | Dónde va |
|---|---|---|
| `rayos_0`, `rayos_1`, `rayos_2` | 11x11 | (37, 0): disco de 3x3 con rayos rectos, diagonales y ambos |
| `corazon_0`, `corazon_1` | 9x8 | (38, 1) |
| `destellos_0..2` | 11x11 | (37, 0): destellos de cuatro puntas que cambian de lugar |
| `estrella_0..2` | 11x11 | (37, 0): chica, grande, grande hueca |
| `ojo_x_5`, `ojo_x_3` | 5x5, 3x3 | sobre cada ojo, en negro |
| `accesorio_sombrero` | 16x8 | ancla del sombrero |
| `accesorio_mono` | 10x8 | ancla del moño (un lado, arriba) |
| `accesorio_gafas` | 14x6 | dos lentes de 6x6 con aro de 2 px (el centro lo rellena la aureola: se ven como gafas de sol) y puente de 2 px |
| `accesorio_gafas_ancha` | 24x7 | solo la carnívora (sus ojos están a 18 px uno del otro) |

### Anclas de los accesorios

`ANCLA_ACCESORIO[familia][nivel][accesorio]` = (dx, dy): la esquina superior izquierda del sprite es el **punto de la cara + (dx, dy)**. Nivel: 0 saludable, 1 en riesgo, 2 crítico, 3 dormida (solo el girasol cambia de cuerpo dormido; las otras familias repiten su nivel 0). Accesorio: sombrero, moño, gafas. El punto de la cara es el que ya usa cada familia: `CARA_GIRASOL` (24,17) (28,22) (31,29) y `CARA_GIRASOL_DORMIDO` (23,21); `CARA_CACTUS` (24,22) (24,23) (24,29); `CARA_SUCULENTA` (24,36) (24,37) (24,39); la carnívora, (24, yo) con yo = (32 - abertura)/2 saludable, 25 en riesgo, 30 crítica, 16 dormida; la comun y el helecho, `CARA_MATERA` (23, 56) (los ojos están en la matera; el sombrero y el moño van arriba de la planta, por eso sus dy son tan negativos).

| Familia | Nivel | Sombrero | Moño | Gafas |
|---|---|---|---|---|
| comun | saludable / dormida | (-8,-56) | (2,-39) | (-6,-3) |
| comun | en riesgo | (-8,-47) | (2,-40) | (-6,-3) |
| comun | crítico | (-8,-32) | (2,-32) | (-6,-3) |
| carnívora | saludable | (-8,-9) | (2,-9) | (-12,-3) |
| carnívora | en riesgo / crítico | (-8,-18) | (2,-17) | (-12,-3) |
| carnívora | dormida | (-8,-14) | (2,-13) | (-12,-3) |
| girasol | saludable | (-8,-17) | (2,-17) | (-7,-3) |
| girasol | en riesgo | (-8,-20) | (2,-17) | (-7,-3) |
| girasol | crítico | (-8,-19) | (2,-12) | (-7,-3) |
| girasol | dormida | (-8,-21) | (2,-18) | (-7,-3) |
| cactus | saludable / en riesgo / dormida | (-8,-19) | (2,-16) | (-7,-3) |
| cactus | crítico | (-8,-18) | (2,-15) | (-7,-3) |
| suculenta | saludable / dormida | (-8,-28) | (2,-22) | (-7,-3) |
| suculenta | en riesgo | (-8,-22) | (2,-18) | (-7,-3) |
| suculenta | crítico | (-8,-14) | (2,-11) | (-7,-3) |
| helecho | saludable / dormida | (-8,-56) | (2,-41) | (-6,-3) |
| helecho | en riesgo | (-8,-43) | (2,-48) | (-6,-3) |
| helecho | crítico | (-8,-36) | (2,-35) | (-6,-3) |

Cómo se mueven: lo que queda sobre la matera (centro del sprite en y < 46) se balancea con `desv(centroY, seno, amp)` y sube o baja con el salto y la respiración (`dy`); lo que queda en la matera (las gafas de la comun y el helecho) no se mueve, igual que su cara. El sombrero se calculó con `y = (punta del cuerpo en el centro de la cara) - 6` (se apoya con 2 px de traslape sobre la cabeza) y se recorta a y >= 0: en el girasol saludable y la carnívora saludable no hay espacio sobre la cabeza y el sombrero tapa un poco de ella. El moño va 2 px a la derecha del punto de la cara y 5 px sobre el cuerpo a ese lado.

### Frases

`firmware/sprites/frases.json` es la fuente de verdad (el front la lee con `git show`): 6 familias (girasol, cactus, suculenta, helecho, carnivora, generica) x 11 ánimos y x 8 eventos, hasta 13 caracteres ASCII. La familia de la OLED es la de `familiaDe`; la planta común usa `generica`. RIEGO_DECLARADO no tiene frase (D192).

## Decisiones

**D190. Los eventos se reproducen de a uno, cada uno un turno fijo de 3000 ms, en orden.** `fito_cola_ev` es un anillo de 10. Cada evento ocupa `FRASE_EVENTO_MS` (3000 ms): la frase se muestra todo el turno y la animación (la más larga dura 1800 ms) cabe adentro. Con 10 de golpe suenan los diez en orden, 30 s, y nada se pierde; con más de 10 esperando, se pierde el más antiguo (el que está sonando nunca se corta) y se cuenta en `descartados`. Un bucle lento no recupera tiempo: el siguiente arranca cuando el bucle despierta. Alternativas descartadas: reproducir solo el último (se pierde la historia que el servidor se molestó en mandar) y acortar los turnos según cuántos hay (el front tendría que calcar una regla más y las animaciones dejarían de durar lo que dicen). Pasa poco en la práctica: el servidor manda los eventos desde la lectura anterior, o sea de a cuatro o cinco como mucho cada 5 s.

**D191. La fila de abajo alterna cada 3000 ms entre el estado y la frase del ánimo; el ciclo arranca por el estado y se reinicia al terminar el último evento.** Sin la clave `mascota` (o con un ánimo desconocido) siempre es el estado. Mientras suena un evento manda su frase. El aviso de sensores (fase 10) sigue mandando sobre todo: "REVISA SENSOR". Descartado: una segunda fila (no cabe: el nombre ocupa la anterior) y deslizar el texto (13 caracteres caben justos).

**D192. RIEGO_DECLARADO se ignora; los tipos que este firmware no conoce también.** El JSON de frases no trae frase de ese evento (la declaración sale de la app y la confirmación o el desmentido llegan después, esos sí con frase y sin animación). Un `tipo` nuevo del servidor da -1 y no entra a la cola; un ánimo nuevo no tiene frase y la fila se queda en el estado. Descartado: una frase genérica para lo desconocido (inventar texto que el front no tiene rompe la paridad).

**D193. La vitalidad es una línea de 78x1 px en (50, 45) y solo se pinta mientras la fila de texto muestra una frase.** Las tres filas de lecturas llegan hasta y = 43, el nombre empieza en y = 47: la única holgura son las filas 44 a 46, y la línea de 1 px en la 45 deja una libre arriba y otra abajo (con 2 px de alto, los dígitos de la temperatura y el nombre quedaban pegados a la barra; se vio en las previas). Se pinta con las frases (del ánimo y de los eventos) y no con el estado: ahí es cuando la mascota "habla" y el dato se lee junto a lo que dice. Descartado: la barra siempre (la línea fija estorba al leer el nombre) y una barra dentro de la zona de la mascota (x < 50: la matera y las hojas la tapan).

**D194. El desmayo es la pose crítica con ojos en "x", persistente mientras el ánimo sea DESMAYADA.** El evento DESMAYO anima la caída (0, 0, 1, 1, 2, 2 px en 600 ms); desde ahí, y también si se llega ya desmayada (arranque o servidor reiniciado), el cuerpo queda 2 px más abajo, sin balanceo, parpadeo, lágrima, mordisco ni respiración. Es un nivel visible 2 (cuerpo, cejas y boca críticos) con los ojos de `ojo_x_5`/`ojo_x_3`. Ganan el saludo (si la acarician desmayada, salta 600 ms y vuelve a la pose) y, entre sí, la pose gana a la noche (no hay zzz: no es sueño). Descartado: que la desmayada duerma con zzz (no es lo mismo y el front tiene un `DESMAYADA` propio) y que la caída vuelva a 0 al terminar (se vería que "se levanta" sin que nadie la riegue).

**D195. Los efectos van todos en la esquina del zzz, con aureola, y no cambian por familia.** Rayos, corazón, destellos y estrella ocupan (37, 0) con 11x11 (el corazón 9x8 en (38, 1)); hay lugar sobre las hojas de todas las familias porque el zzz ya lo usaba. La aureola negra de 1 px los separa de lo que haya debajo (la trampa de la carnívora llega hasta ahí). Descartado: efectos "alrededor de la cabeza" que se mueven con cada familia: 6 familias x 4 niveles de posiciones más que el front tendría que calcar.

**D196. ACARICIADA y REVIVIO son el saludo existente más su efecto; solo la parte animada despierta de noche.** Al arrancar el evento se dispara el mismo `saludando` de la fase 11 (el bloque SALUDO de 600 ms, que ya despertaba de noche); el efecto sigue 1800 ms (corazón) o 1200 ms (destellos). SALIO_AL_SOL, RACHA y DESMAYO despiertan la mascota mientras dura su animación (1500, 1800, 600 ms) y vuelve a dormir; REGADA y los de solo frase la dejan dormida (REGADA ya tenía su gota, que es de la suculenta, y se dispara aunque duerma). Descartado: despertarla los 3000 ms de cada turno (una mascota que casi nunca duerme si hay eventos seguidos) y no reproducir nada de noche (el servidor sí los cuenta).

**D197. Los accesorios son sprites de 1 bit con aureola y una tabla de anclas por familia, nivel y accesorio.** 4 sprites (los tres pedidos más `accesorio_gafas_ancha` para la carnívora, cuyos ojos están a 18 px: con las gafas normales quedaban entre los ojos), 6 familias x 4 niveles x 3 accesorios de (dx, dy) respecto al punto de la cara. Las gafas son gruesas, de aro de 2 px, y su centro se rellena con la aureola: se leen como gafas de sol y son el único accesorio que tapa los ojos, que es lo que se pidió. Se ven dormida y desmayada porque el ancla sigue al cuerpo y al salto. Revisado en las previas de las tres hojas de contacto (familia x nivel): ni el sombrero ni el moño cubren los ojos. Descartado: un sprite por familia (3 x 6 = 18 dibujos más) y dibujar los accesorios con primitivas en el firmware (el front no los podría calcar).

**D198. El BOOT corto saluda al instante y además acaricia de verdad.** El saludo local sigue siendo inmediato (la pulsación no espera a la red) y enseguida sale `POST .../mascota/acciones {"tipo":"ACARICIAR"}`, solo en OPERANDO, con red y token; si no, queda solo el saludo y se anota en el registro. Bloquea el bucle lo mismo que una lectura (`TIMEOUT_HTTP_MS`); el saludo sigue por tiempo, así que se retoma al volver. El registro dice `caricia -> HTTP 201`, `HTTP 200 (repetida, dentro de 10 s)` o el error; un 401 descarta el token como en las lecturas. No se reintenta. Consecuencia que se ve: la caricia vuelve como evento ACARICIADA en la siguiente lectura (hasta 5 s después) y reproduce saludo + corazón: es la confirmación del servidor. Descartado: ocultar ese segundo saludo (la matera no distingue su propia caricia de la de la app).

**D199. La lógica pura va en C con pruebas; el JSON se filtra al parsear; las frases se generan.** `fito_mascota.{h,c}` (sin Arduino) tiene textos a números, la cola, la fila, los cuadros y la barra, y se prueba con guiones a mano y al azar contra un modelo independiente en Python. `cliente_api` deserializa con un filtro de ArduinoJson (apodo, estado, periodo, especie, rangos, y de `mascota` vitalidad, ánimo, accesorio y el `tipo` de cada evento): el documento deja de guardar las horas y las recomendaciones, así que los 10 eventos no suben el uso de RAM (52.588 a 52.660 B en total). `generar_frases.py` produce `frases.h` desde el JSON y falla si falta una frase, pasa de 13 caracteres o no es ASCII; `probar_nucleo.py` exige que `frases.h` esté al día. Descartado: parsear sin filtro (un documento de varios KB por lectura) y guardar el texto de cada evento en la cola (10 x 17 B de más sin ganar nada).

## Pruebas

- **Compilación** (`pio run -e esp32dev`, `local.ini` con `sensores = reales`, sin flashear ni abrir `/dev/ttyUSB0`):

  | | Flash | RAM |
  |---|---|---|
  | Antes (`develop`) | 86,4 % (1.132.273 de 1.310.720 B) | 16,0 % (52.588 de 327.680 B) |
  | Después | **87,2 %** (1.143.557 B) | 16,1 % (52.660 B) |
  | Diferencia | +11.284 B (+0,9 puntos) | +72 B |

  Lejos del 93 % de tope: no hubo que recortar. Lo grande son las frases (1.680 B) y el código de la mascota; los 17 sprites nuevos pesan unos 300 B.
- **`python3 firmware/test/probar_nucleo.py`**: **829 comprobaciones, 0 fallas** (antes, 498). Las 331 nuevas: textos del servidor a números (con los valores desconocidos), la cola con guiones a mano (sin eventos, uno, tres, diez de golpe en orden y 30 s, once de golpe con descarte, llegan diez mientras suena otro, RIEGO_DECLARADO y desconocidos, bucle lento, desborde de `millis()`) y 60 guiones al azar de 40 pasos contra un modelo en Python, los cuadros de rayos, corazón, destellos y estrella cada 50 ms y en sus bordes, la caída del desmayo, las duraciones de cada animación (todas caben en 3000 ms), la barra, que `frases.h` esté al día con `frases.json` y que el generador rechace 14 caracteres, no ASCII, ánimos, eventos o familias que faltan o sobran, y el simulador (línea de la mascota, caricia 200, 201 y 401, sin token).
- **Previas** (`python3 firmware/sprites/previsualizar.py /ruta`, función `mascota_completa`): las tres hojas de contacto de los accesorios (6 familias x saludable, en riesgo, crítico y dormida), la frase con la barra, un cuadro de cada evento, el cactus desmayado, y GIF de cada evento, del desmayo, de REVIVIO de noche, de diez eventos seguidos y de los accesorios con balanceo y con el mordisco. Revisadas a ojo: los accesorios no tapan los ojos salvo las gafas; la primera versión de la barra (2 px de alto en la fila 44) tocaba los números de la temperatura y se pasó a 1 px en la fila 45. Copias en `docs/fase14/previas/`: `50_accesorio_sombrero.png`, `50_accesorio_mono.png`, `50_accesorio_gafas.png`, `51_frase_animo_girasol.png`, `52_evento_*.png`, `53_desmayada_cactus.png`.
- **Falta:** verlo en la OLED real (no se flasheó) y contra un servidor que ya mande `mascota` (el del otro agente no estaba en este árbol; el contrato se programó tal cual y se probó con 201 de ejemplo).

## Límites

- No se probó en la placa ni contra el servidor nuevo: lo verificado es el núcleo en C, la compilación y el espejo en Python. El filtro de ArduinoJson y la lectura de `mascota` quedan por ver con una respuesta real.
- La caricia del BOOT bloquea el bucle hasta una respuesta o el timeout (como una lectura); durante ese rato la OLED no repinta.
- El evento de la propia caricia vuelve como segundo saludo hasta 5 s después (D198).
- Los eventos solo avanzan mientras se pinta la pantalla de lecturas: si la matera está en "sin red" o en el aviso de reclamo, esperan en la cola (hasta 10).
- Sombrero y moño: con la cabeza pegada al borde de arriba (girasol y carnívora saludables) el sombrero se recorta a y >= 0 y tapa un poco de la cabeza. Las gafas de sol cubren también las cejas.
- Con el aviso de sensores (fase 10) la mascota sigue con sus eventos, pero no se ve la fila de frases (manda "REVISA SENSOR").
- La barra solo se ve con una frase; con el estado, la vitalidad no se muestra.
- `previsualizar.py` sigue siendo un espejo a mano del dibujo: los números sí tienen prueba (núcleo), el trazo no.

## Qué debe hacer el front

Calcar, en este orden, lo que está en "Los números que el front copia":

1. **Frases.** Leer `firmware/sprites/frases.json` con `git show` (o copiarlo): la fila de texto alterna cada `FRASE_ALTERNA_MS = 3000` entre el estado y la frase del ánimo, empezando por el estado; la frase del evento manda `FRASE_EVENTO_MS = 3000` y vuelve al ciclo (que arranca de nuevo por el estado). Familias de `familiaDe`; la común usa `generica`.
2. **Cola de eventos.** De a uno, en orden de llegada, un turno de 3000 ms cada uno (la animación adentro, la frase todo el turno); máximo 10 esperando (se pierde el más antiguo); RIEGO_DECLARADO sin frase ni animación y sin turno. Con noche, la mascota se despierta solo lo que dura la parte animada (tabla de duraciones).
3. **Animaciones.** SALIO_AL_SOL 3 cuadros x 250 ms x 2 vueltas; ACARICIADA saludo + corazón 2 cuadros x 300 ms x 3 latidos; REVIVIO saludo + destellos 3 x 200 x 2; RACHA estrella 3 x 300 x 2; DESMAYO caída 0 0 1 1 2 2 px en 600 ms con ojos "x" y, mientras el ánimo sea DESMAYADA, quedarse 2 px abajo, con ojos "x", sin balanceo, parpadeo, lágrima ni zzz (pose crítica). RIEGO_CONFIRMADO y RIEGO_DESMENTIDO solo frase. REGADA: la gota de siempre. Los dibujos están en `arte/*.txt` (efectos 11x11 en la esquina de arriba a la derecha, con aureola).
4. **Vitalidad.** Barra de 78x1 px en (50, 45) de la OLED, relleno `(vitalidad * 78 + 50) / 100`, el resto punteado; solo mientras se muestra una frase.
5. **Accesorios.** Los tres sprites (más las gafas anchas de la carnívora) con aureola negra, en el punto de la cara de cada familia + el (dx, dy) de la tabla de anclas, con el mismo balanceo y salto que el cuerpo (lo que está sobre la matera), también dormida y desmayada. Gafas: el único accesorio que tapa los ojos.
6. **Hora de comparar.** `previsualizar.py` deja recortes de 48x64 de las tres hojas de accesorios en `mascota/acc_<accesorio>_<familia>_<0..3>.png` (0 a 2 por nivel, 3 dormida), a los 2500 ms de una escena tranquila.

## Para el documento de arquitectura

La fase no toca el backend ni el dominio: el firmware empieza a usar una clave del contrato (`mascota` en la 201) y un endpoint (`POST .../mascota/acciones`) que la fase 9 del servidor ya ofrecía a usuarios y que ahora también acepta el token de dispositivo. El cambio vive en la presentación del chip (`pantalla.cpp`) y en la lógica pura (`fito_mascota.c`, probada sin placa contra un modelo independiente); la fuente de las frases es un JSON versionado en el repo, compartido con el front, del que se genera una tabla PROGMEM.
