# Fase 7: la planta como mascota

Cuaderno de implementación (como `docs/fase5/`). Pedido: "la planta como tamagotchi". Más plantas con cara y ánimo en la OLED, en el espíritu de la carnívora de la fase 5. Esta fase agrega cuatro mascotas al firmware (girasol, cactus, suculenta y helecho) y tres especies al CSV del catálogo (girasol, cactus y atrapamoscas). No cambia el contrato HTTP ni una línea de Java de producción. Guía de uso: [`firmware/README.md`](../../firmware/README.md).

## Qué hay

| Pieza | Dónde |
|---|---|
| Arte de las cuatro mascotas (primitivas en PIL, 1 bit) | `firmware/sprites/dibujar.py`: `girasol()`, `cactus()`, `suculenta()`, `helecho()` y sus ayudas (`petalos`, `disco`, `lente`, `fronda`) |
| Cuadros en texto (`#` y `.`) y vista previa x4 | `firmware/sprites/arte/{girasol,cactus,suculenta,helecho}_*.txt`, `firmware/sprites/previa/*.png` |
| Bitmaps PROGMEM generados | `firmware/src/sprites.h` (por `generar.py`, sin cambios en la herramienta) |
| Elección de la mascota, cara e idle | `firmware/src/pantalla.cpp`: `CLAVES[]`, `familiaDe()`, `caraGrande()` con `Geometria`, `pintarGirasol/Cactus/Suculenta/Helecho()` |
| Espejo en Python de la pantalla (PNG y GIF por mascota) | `firmware/sprites/previsualizar.py`: `Lecturas(familia, ...)` y `mascotas()` |
| Especies nuevas | `src/main/resources/referencia/especies.csv`; rangos y fuentes en el README raíz |

Cómo se ve cada una (cara grande negra sobre una zona lisa del dibujo, salvo el helecho, que la lleva en la matera):

| Mascota | Claves en el nombre | Saludable | En riesgo | Crítico | Idle |
|---|---|---|---|---|---|
| Girasol | `girasol` | corona de 12 pétalos y cara sonriente en el disco | cabeza inclinada, faltan pétalos, puntas caídas | la cabeza cuelga del tallo arqueado; un pétalo en el suelo | la corona gira medio pétalo cada 400 ms, la cabeza cabecea 1 px, la cara mira a los lados; se balancea 1,3 veces la planta común |
| Cactus | `cactus`, `cacto` | columna con brazos arriba, espinas y costillas | brazos abajo, sin flor | encogido, brazos colgando, una espina en el suelo | parpadeo; 600 ms después de volver a saludable le crece una flor en la punta (botón y cinco pétalos en 1,5 s); casi no se balancea |
| Suculenta | `suculenta`, `echeveria` | roseta de hojas gruesas con borde | hojas más abiertas y finas | arrugada, puntas caídas, una hoja en el suelo | respira: se infla 500 ms cada 4,5 s; al regarla cae una gota, se infla (o da un saltito si no está saludable) y sonríe |
| Helecho | `helecho` | cinco frondas erguidas de foliolos finos | frondas arqueadas | frondas ralas colgando, un foliolo en el suelo | las frondas se mecen: dos cuadros que alternan cada 450, 700 o 1100 ms según el estado |

## Decisiones

**D110. La mascota la elige el firmware por el nombre de la especie, con una tabla.** `CLAVES[]` en `pantalla.cpp` asocia un texto con una familia (`atrapamoscas` y `carniv` a la carnívora, `girasol`, `cactus`, `cacto`, `suculenta`, `echeveria`, `helecho`). Se busca cada texto dentro del nombre, sin mayúsculas, y gana la primera coincidencia; sin coincidencia queda la planta común. Es la misma regla que ya usaban la carnívora del chip y `esCarnivora()` del front, ahora como tabla. Descartado: un campo `mascota` en la respuesta del servidor. Habría que tocar dominio, contrato y migración para algo que es solo de presentación, y un chip viejo lo ignoraría igual. Costo: una especie llamada "rosa del desierto" no sale como suculenta hasta que alguien agregue la clave.

**D111. Una sola cara grande, parametrizada; la carnívora no cambia.** La cara de la trampa de la fase 5 pasó a `caraGrande(cara, parpadeo, ahora, cx, cy, Geometria, conBoca)`. `Geometria` trae separación de ojos, alto del ojo, media boca, fila de la boca, curva de la sonrisa, fila de la ceja y columna de la lágrima. Hay tres: `GEO_TRAMPA` (la de antes), `GEO_CHICA` (girasol y suculenta: cabe en un disco de radio 9) y `GEO_CACTUS`. Los cuatro estados de expresión, el parpadeo cada 3 a 5 s, la interpolación de 400 ms y la lágrima son los mismos para todas. Prueba de que la carnívora no cambió: `previsualizar.py` del commit anterior y el nuevo dan los mismos PNG y GIF de la carnívora y de la planta común, byte a byte.

**D112. Un sprite por estado; lo continuo, por código.** Cada mascota tiene un cuerpo de 48x64 por estado y solo los cuadros de idle que no se pueden dibujar encima: la corona girada del girasol, la roseta inflada de la suculenta y el segundo cuadro de cada estado del helecho. Lo que crece o se mueve de forma continua va por código: la flor del cactus (`florCactus`, cinco círculos con el centro apagado), la gota del riego (el icono 8x8 que ya existía), el cabeceo y la mirada del girasol. Son 17 cuerpos de 384 bytes: unos 6,5 KB de PROGMEM. Todas respetan `Y_MATERA = 46`, así que el balanceo que dobla el sprite (`cuerpo()`) sirve igual para todas.

**D113. Cada mascota tiene su idle y su balanceo.** `FACTOR_BALANCEO[]` multiplica la amplitud de la planta común: 1,3 el girasol, 1,2 el helecho, 0,4 el cactus y la suculenta (son rígidos). El idle va en una función por familia (`pintarGirasol`, etc.) que dibuja el cuerpo y pone la cara en el centro que dice `CARA_<FAMILIA>[nivel]`, para que la cara siga a la cabeza cuando esta se inclina o cuelga. `lNivelDesde` guarda cuándo se entró al estado actual: el cactus cuenta desde ahí para la flor, así que la flor vuelve a crecer cada vez que la planta se recupera.

**D114. "Regar" es una subida de humedad de 3 puntos entre dos lecturas, detectada en el chip.** `lecturas()` compara la humedad nueva con la anterior; si sube `SUBIDA_RIEGO` (3,0) o más, marca `regadoDesde`. No hay contrato nuevo ni evento de riego en el servidor. Los repintados con la misma lectura (OLED que reaparece, punto de envío) no disparan nada porque la humedad no cambia. Límite: con los sensores simulados, una lectura fuera de rango seguida de una normal también cuenta como riego.

**D115. Arte original, dibujado con primitivas.** Las cuatro mascotas salen de `dibujar.py` (elipses, lentes, curvas cuadráticas) y no de sprites ajenos. El girasol se inspira en el estilo de caricatura de Plants vs. Zombies (cara en el disco, pétalos alrededor) sin copiarlo. Los `.txt` siguen siendo la fuente de verdad y se pueden retocar a mano. `dibujar.py` ahora también borra y reescribe los `.txt` de las cuatro familias nuevas.

**D116. El catálogo crece por el CSV, sin migración.** Se agregan `girasol`, `cactus` y `atrapamoscas` a `especies.csv`. `atrapamoscas` lleva los rangos que ya tiene la base en vivo (60-90 %, 3000-20000 lux, 15-32 °C), para que el CSV y la base digan lo mismo. Las fuentes de los rangos están en la sección "Fuentes de las especies agregadas" del README raíz; donde la fuente no da un número de humedad de sustrato, el rango es criterio propio y se dice. En modo `csv` las tres aparecen al arrancar. En modo `bd` (el de por defecto) el CSV solo siembra si la tabla está vacía: una base nueva arranca con ocho especies, pero una base existente no cambia, y girasol y cactus se dan de alta con `POST /admin/especies`. Descartado: una migración Flyway que las inserte. Chocaría en número de versión con otra rama abierta y escribiría sobre catálogos que el administrador ya editó. Las pruebas que fijaban "cinco especies" ahora dicen ocho, las que nombraban "Anexo B" se renombraron a "especies del CSV", y la que usaba "cactus" como especie inexistente usa "orquidea".

**D117. El front no se toca en esta fase.** Hoy el front dibuja una planta SVG genérica o la carnívora (`esCarnivora()` en `js/componentes/especie.js` y `crearIlustracionPlanta({ carnivora })` en `js/componentes/ilustracion-planta.js`); la expresión la cambia `estadoVisual()` por estado. Para que la mascota se vea también en la app hace falta: (1) `familiaDe(especie)` en `especie.js` con las mismas claves de la D110, y su prueba en `tests/especie.test.mjs`; (2) en `ilustracion-planta.js`, un dibujo SVG por familia (disco y pétalos, columna con brazos, roseta, frondas) que reciba el mismo `estadoVisual`, y la cara (ojos y boca) separada del cuerpo, como ya lo está en la carnívora; (3) cambiar las cuatro llamadas de `esCarnivora` (tablero, detalle, asistente de vinculación y diagnóstico suelto) por la familia, incluido el `cambiaElDibujo` del detalle; (4) el idle en `animaciones.js` (girar la corona, la flor, inflar, mecer) respetando `prefers-reduced-motion`.

## Pruebas

- `pio run -e esp32dev` compila sin advertencias con `local.ini` copiado de `local.ini.ejemplo`. Binario: flash de 1 109 125 a 1 117 121 bytes (+7 996 B; 84,6 % a 85,2 % de la partición de 1,25 MB). RAM estática de 52 004 a 52 012 bytes (+8 B). No se subió a la placa.
- `previsualizar.py` genera `20_<mascota>_<estado>.png` y `anim_<mascota>.gif` (saludable, riesgo, crítico y vuelta a saludable; la suculenta se riega dos veces). Las pantallas de la carnívora y la planta común son idénticas, byte a byte, a las del commit anterior (D111).
- Java: `MAVEN_OPTS=-Xmx512m ./mvnw -Dtest='*Catalogo*,*Especie*,*Csv*,*Referencia*' test` (71 pruebas en 11 clases, todas pasan). Suite completa: 873 pruebas; falló una, `FlujoDePlantasIntegracionTest.laEspecieDebeExistir...`, que usaba "cactus" como especie inexistente. Ahora usa "orquidea" y pasa (15 de 15).
- Falta: verlo en la OLED real. El comportamiento en el chip es el mismo código que espeja `previsualizar.py`, pero el espejo se mantiene a mano.

## Límites

- La mascota depende del nombre de la especie (D110). Una especie sin clave cae en la planta común.
- El riego se infiere de la humedad (D114); con sensores simulados puede dispararse sin riego real.
- `previsualizar.py` es un espejo a mano de `pantalla.cpp`: si se cambia uno, hay que cambiar el otro.
- En modo `bd`, una base existente no recibe girasol ni cactus (D116).
- Los rangos de girasol y cactus son de planta en maceta a pleno sol; el sensor de luz real (BH1750) satura en 65535 lux.
- El front sigue mostrando la planta genérica o la carnívora (D117).

## Cómo agregar la próxima planta

1. **Arte.** En `firmware/sprites/dibujar.py`, una función `mi_planta(estado, cuadro=0)` que devuelva un lienzo de 48x64 por estado (`saludable`, `riesgo`, `critico`). Usar `matera(d, ...)` al final, para que el borde quede en la fila 46 (`Y_MATERA`). Dejar liso, sin detalle, el lugar de la cara: unos 17x13 px para `GEO_CHICA`. Agregar el prefijo a la limpieza de `main()` y los `guardar(...)` de cada cuadro.
2. **Generar.** `python3 firmware/sprites/dibujar.py && python3 firmware/sprites/generar.py`. Mirar `sprites/previa/mi_planta_*.png`.
3. **Firmware.** En `pantalla.cpp`: el arreglo de cuerpos (`const uint8_t *const MI_PLANTA[3]`), un valor nuevo en `enum class Familia`, su clave en `CLAVES[]`, su factor al final de `FACTOR_BALANCEO[]` (mismo orden que el enum), el centro de la cara por nivel (`CARA_MI_PLANTA[3]`), una `pintarMiPlanta()` con su idle y su rama en `pintarLecturas()`. Si la cara no cabe en `GEO_CHICA`, definir otra `Geometria`.
4. **Espejo.** En `previsualizar.py`: el centro de la cara, la entrada en `FACTOR_BALANCEO` y `pintar_mi_planta()`. `mascotas()` la recorre sola. Correr `python3 firmware/sprites/previsualizar.py /tmp/previa` y mirar el GIF.
5. **Compilar** con `pio run -e esp32dev` y anotar cuánto creció el binario.
6. **Catálogo.** Si la especie no existe, agregarla a `especies.csv` con su fuente en el README raíz y ajustar las pruebas que cuentan especies (`AdaptadorEspecieCsvTest`, `ContratoFuenteDeEspeciesTest`, `LectorDeEspeciesCsvTest`, `AdaptadorEspecieJpaTest`, `FuenteCsvIntegracionTest`).
