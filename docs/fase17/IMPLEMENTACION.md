# Fase 17: la recomendación de la luz acumulada

Cuaderno de implementación (como `docs/fase15/`). Pedido: Santiago, 27-nov, viendo la demo con la suculenta (luz 800–2500 lux). Llevaba 0,4 h de luz y 535,2 lux efectivos, así que la luz quedaba `BAJO` (correcto), pero la lectura del momento era 1640 lux (le acababan de poner un bombillo). La recomendación decía "Luz 1640 lux por debajo del rango óptimo 800–2500 lux: acercarla a una fuente de luz indirecta." Dos errores: el número no era el que se evaluó (se contradecía: 1640 "por debajo" de 800–2500), y la acción no servía (ahora sí le llega buena luz).

## Qué hay

| Pieza | Dónde | Capa |
|---|---|---|
| La recomendación de la luz acumulada | `dominio/servicio/ReglaDeLuzAcumulada.java` (`recomendacion`) | dominio |
| La pone en la posición de la luz | `dominio/servicio/EvaluadorDeEstado.java` (`recomendaciones`) | dominio |

No hay cableado: la aplicación, el contrato HTTP, la base y la mascota no cambian. Un solo commit de código.

## Los casos

La regla solo actúa si la luz se evaluó `ACUMULADA` y quedó fuera de rango. Con la lectura del momento (`valor`) y el valor evaluado (lux efectivo):

| Caso | Texto |
|---|---|
| BAJO y la lectura < mínimo | `Luz 535.2 lux efectivos por debajo del rango óptimo 800–2500 lux: acercarla a una fuente de luz indirecta; hoy lleva 0.4 h de luz.` |
| BAJO y la lectura >= mínimo | `Luz 535.2 lux efectivos por debajo del rango óptimo 800–2500 lux: ahora le llegan 1640 lux, déjala ahí para que sume la luz que le falta hoy.` |
| ALTO y la lectura <= máximo | `Luz 3000 lux efectivos por encima del rango óptimo 800–2500 lux: ahora le llegan 2000 lux y está bien, mantenla así para que baje el promedio de hoy.` |
| ALTO y la lectura > máximo | `Luz 3000 lux efectivos por encima del rango óptimo 800–2500 lux: protegerla de la luz directa.` |
| Instantánea (sin historial) | como siempre, por `RedactorDeRecomendaciones` |
| De noche | la luz no cuenta, no hay recomendación (como antes) |

Los números salen sin ".0" si son enteros y con un decimal si no.

## Decisiones

**D209. La luz acumulada la redacta la regla de luz.** Igual que D124 (la luz) y D202 (la humedad): `ReglaDeLuzAcumulada` es la única clase que nombra `Magnitud.LUZ`, y la recomendación depende de cosas que solo ella sabe (la exposición, el lux efectivo). `RedactorDeRecomendaciones` no cambia y sigue sin nombrar magnitudes; antes escribía "750 lux de promedio en el día", que ya no sale para la luz.

**D210. Se escribe el valor evaluado, con su unidad "lux efectivos", y la acción mira la lectura del momento.** La frase debe ser coherente con la clasificación: lo que se compara con el rango es el lux efectivo. La lectura del momento solo decide la acción.

**D211. Se reutiliza el mecanismo de la fase 15.** `EvaluadorDeEstado.recomendaciones` ya reemplazaba/insertaba por posición para la humedad tolerada. Ahora la luz acumulada reemplaza el texto del redactor en su posición, y la humedad tolerada, al insertarse, corre la posición de lo que sigue (antes cortaba el recorrido con `break`, y habría dejado sin reemplazar la luz). No hay mecanismo nuevo.

**D212. La acción de siempre se conserva** (`Magnitud.accionSiBajo/accionSiAlto`, "acercarla a una fuente de luz indirecta"), no se inventa una por especie: el catálogo no tiene acciones por especie. Se le agrega "hoy lleva X h de luz" (horas de luz de la exposición), solo en el BAJO con lectura baja.

**D213. Bordes: la lectura decide con los bordes del rango incluidos.** BAJO con lectura >= mínimo es "déjala ahí"; ALTO con lectura <= máximo es "mantenla así". Un BAJO acumulado con lectura por encima del máximo (sol directo recién puesto) entra en "déjala ahí"; es raro y se deja así por simplicidad (ver límites).

**D214. El front parte el texto sin cambios.** Ver abajo.

## Cómo lo pinta el front

`recomendacionEnPalabras` corta en la ÚLTIMA `": "` y toma lo de después como acción; el detalle sale del parámetro (`valor` = lectura del momento, rango, unidad), no del texto. Por eso "lux efectivos" no rompe nada y las acciones nuevas no tienen `": "` dentro. Con la suculenta de la demo:

- Acción: "Ahora le llegan 1640 lux, déjala ahí para que sume la luz que le falta hoy."
- Detalle: "Le llegan 1640 lux; lo ideal es de 800 a 2500 lux."
- Con lectura baja: acción "Acercarla a una fuente de luz indirecta; hoy lleva 0.4 h de luz." y detalle "Le llegan 300 lux; lo ideal es de 800 a 2500 lux."

## Pruebas

`RecomendacionDeLuzAcumuladaTest` (8): los cuatro casos, la posición cuando también falla la humedad, instantánea sin cambios, noche sin recomendación, y el formato de 535.2 y 0.4 h. `EvaluadorConLuzAcumuladaTest`: una aserción actualizada al texto nuevo. Suite completa: 1123 pruebas, todas verdes (develop tenía 1115).

## Límites

- El "lux efectivo" es un concepto interno; para quien lee es "luz del día repartida". El texto lo nombra pero no lo explica; el front puede explicarlo en el detalle.
- BAJO acumulado con lectura sobre el máximo dice "déjala ahí"; lo más honesto sería avisar de la luz directa. Pide un quinto caso.
- Las horas de luz salen de la exposición (umbral de 100 lux, D125), no de un conteo del día calendario.
- La lectura del momento entra en el lux efectivo (es el último tramo), así que el valor evaluado se mueve un poco entre lecturas.

## Para el documento de arquitectura

Qué cambió en el dominio: la recomendación de la luz acumulada. Antes salía del redactor genérico con la lectura del momento mezclada; ahora la redacta `ReglaDeLuzAcumulada`, que conoce la exposición, dice el valor que realmente se evaluó y adapta la acción a lo que está pasando ahora.

Qué no cambió: aplicación, puertos, adaptadores, contrato HTTP y base. El redactor genérico sigue sin nombrar magnitudes.

Por qué demuestra la hexagonal: es una regla de negocio que entra y se prueba solo en el dominio, en un commit sin tocar nada fuera de él, reutilizando el mecanismo de posición de la fase 15.
