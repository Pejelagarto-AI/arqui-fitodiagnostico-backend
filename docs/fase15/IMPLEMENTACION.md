# Fase 15: planta recién regada

Cuaderno de implementación (como `docs/fase8/`). Pedido: Santiago, 26-nov, durante la prueba con los sensores reales. Con la sonda calibrada, la tierra recién regada marca cerca de 100 % y, escurrida, entre 70 y 90 %. Hasta la fase 14 la humedad se comparaba con la lectura del momento, así que una suculenta (10–30 %) quedaba `CRITICO` y la mascota `AHOGADA` segundos después de cada riego. Es el mismo error que tenía la luz a las 10 pm (fase 8): la lectura del momento no basta, hace falta el historial. Esta fase agrega una regla del dominio para la humedad. El contrato HTTP solo agrega un campo. El firmware no se toca; el front puede aprovecharlo (ver "Qué puede hacer el front").

## Qué hay

| Pieza | Dónde | Capa |
|---|---|---|
| La regla: riego, gracia, recomendación, constantes | `dominio/servicio/ReglaDeHumedadTrasRiego.java` | dominio |
| El diagnóstico dice cuándo se regó | `dominio/modelo/Diagnostico.java` (campo `regadaEn`, método `recienRegada()`) | dominio |
| El evaluador recibe la serie de humedad | `dominio/servicio/EvaluadorDeEstado.java` (sobrecarga `evaluar(especie, medicion, tomadaEn, luzAnterior, humedadAnterior)`) | dominio |
| Trae la serie y se la entrega al evaluador | `aplicacion/ServicioMediciones.java` (`humedadAnteriorA`) | aplicación |
| La mascota no se ahoga recién regada | `mascota/modelo/MapaDeAnimo.java` (`peor`) | contexto de la mascota |
| Columna nueva | `adaptador/salida/jpa/DiagnosticoDePlantaEntity.java`, `MapeadorDiagnosticoDePlanta.java` | infraestructura |
| Migración | `db/migration/{h2,postgresql}/V24__diagnostico_regada_en.sql` | infraestructura |
| Contrato | `adaptador/entrada/rest/dto/RespuestaDiagnostico.java` (`regadaEn`) | presentación |

## La regla

1. **Riego.** Hay riego en una lectura (la candidata) si ella y la siguiente (la confirmación) superan la mediana de la hora anterior en al menos 15 puntos, y la lectura justo antes de la candidata no. Con menos de 3 lecturas en esa hora no se decide nada. Un pico de una sola lectura no cuenta; una subida lenta tampoco.
2. **Gracia.** Si hubo riego en las últimas 24 h, la humedad `ALTO` no entra al estado global. En su lugar, la recomendación dice: *"Humedad del sustrato 97 % por encima del rango óptimo 10–30 %: la regaste hace 3 min y quedó bien hidratada; no volver a regar hasta que baje de 30 %."*
3. **Encharcamiento.** Pasada la gracia, la humedad `ALTO` cuenta como siempre ("suspender el riego y revisar el drenaje"). Regar una tierra que ya estaba húmeda (de 85 a 98 %) no alcanza el umbral: no hay riego y el exceso cuenta.
4. **Sin historial,** todo sigue como antes.

## Decisiones

**D200. La humedad recién regada no cuenta en contra, pero no se esconde.** Igual que la luz de noche (D121): `ReglaDeHumedadTrasRiego.queCuentanParaElEstado` quita la humedad `ALTO` de lo que agrega `ReglaPorDesviacion`, y el parámetro sigue en `parametros` con su `ALTO`. Así el front puede mostrar el 97 % real sin que la planta quede en riesgo. La humedad `BAJO` nunca se tolera: recién regada no puede estar seca, y si lo está es otro problema.

**D201. El riego se detecta con el criterio de la mascota, y la gracia es de 24 h.** El criterio (mediana de la hora anterior + 15 puntos, sostenido en la lectura siguiente) es el de `DetectorDeSaltos` (D134), copiado a propósito: el diagnóstico no puede depender del contexto de la mascota (ArchUnit), y los dos tienen que ver el mismo riego para que la OLED no diga "¡me regaron!" mientras el diagnóstico dice "ahogada". La gracia de 24 h sale de cómo se seca una matera: el agua que sobra drena en minutos, pero el sustrato queda por encima del rango "entre riegos" de las especies durante horas y hasta un día. Un día completo cubre ese secado sin tapar un encharcamiento, que dura días. Las constantes son públicas, como las de la luz.

**D202. `ReglaDeHumedadTrasRiego` es la única clase del dominio que nombra `Magnitud.HUMEDAD`.** El mismo criterio de D124 para la luz. El clasificador, la regla de agregación, el redactor y el detector de cambios siguen sin nombrar magnitudes. `EvaluadorDeEstado` le pregunta a la regla `magnitud()`. Se conservaron los constructores y las firmas anteriores de `EvaluadorDeEstado` y `Diagnostico`, por eso `ConfiguracionDominio` y las pruebas viejas no cambiaron.

**D203. `regadaEn` es parte del diagnóstico y se guarda.** La mascota lee el último diagnóstico de la base (`ServicioMascota`, `ServicioVistasDeMascota`), así que el dato tiene que viajar con él, como `periodo` (D128). El diagnóstico se reconstruye desde su propia fila: si la regla cambia mañana, el de hoy sigue diciendo lo que dijo. Las filas anteriores a V24 se leen sin riego. El contrato agrega `regadaEn` al final (ISO-8601 o `null`), con la regla de D129: solo agregar.

**D204. La mascota no se ahoga recién regada.** `MapaDeAnimo.peor` ignora la humedad `ALTO` cuando el diagnóstico trae `regadaEn`, igual que ignora la luz de noche (D170). Si era lo único que fallaba, el ánimo es `FELIZ` (o `CANSADA`). Si falla otra magnitud, gana esa.

**Recomendación en su lugar.** `EvaluadorDeEstado` pone la recomendación de la humedad tolerada en la posición que habría tenido la de la humedad. El front empareja las recomendaciones con los parámetros fuera de rango por posición (`recomendacionesPorParametro`), y la nota usa la misma forma "descripción valor comparación rango: acción.", así que se muestra con su icono y su detalle sin cambiar el front.

## La prueba de cambio

Dos commits de código, separados a propósito, como en la fase 8:

| Commit | Qué | Archivos |
|---|---|---|
| `feat(dominio)` | La regla nueva | `dominio/modelo/Diagnostico`, `dominio/servicio/EvaluadorDeEstado`, `dominio/servicio/ReglaDeHumedadTrasRiego`. Pruebas: `ReglaDeHumedadTrasRiegoTest`, `EvaluadorConRiegoTest` |
| `feat(aplicacion)` | Usarla, guardarla y mostrarla | `aplicacion/ServicioMediciones`, `adaptador/entrada/rest/dto/RespuestaDiagnostico`, `adaptador/salida/jpa/DiagnosticoDePlantaEntity`, `adaptador/salida/jpa/MapeadorDiagnosticoDePlanta`, `mascota/modelo/MapaDeAnimo`, `V24` (h2 y postgresql). Pruebas: `ServicioMedicionesTest`, `AdaptadorDiagnosticoJpaTest`, `MapaDeAnimoDeMascotaTest` |

El primero compila y pasa las pruebas sin tocar nada fuera del dominio: la aplicación sigue llamando `evaluar(especie, medicion, tomadaEn, luzAnterior)` y obtiene lo de antes. El segundo no cambia ninguna clase del dominio.

Lo que **no** cambió: `Rango`, `Especie`, `Medicion`, `Magnitud`, `ResultadoParametro`, `ClasificadorDeParametros`, `ReglaDeAgregacion`, `ReglaPorDesviacion`, `RedactorDeRecomendaciones`, `ReglaDeLuzAcumulada`, el puerto `RepositorioDiagnosticos` (ya tenía `serieDe` para cualquier magnitud), los controladores, `config/`, el firmware y el front.

## Pruebas

- Dominio, sin servidor ni base, con series simuladas sacadas de la calibración real de la sonda (seca ~20 %, regada ~100 %, ruido ±3): `ReglaDeHumedadTrasRiegoTest` (13): riego de verdad, riego confirmado por la lectura del momento, pico de una lectura, subida lenta, regar tierra ya húmeda, poca referencia, riego de hace 25 h (ya no cuenta) y de hace 23 h (todavía cuenta), dos riegos (gana el último), qué se quita del estado, la `BAJO` nunca se tolera, el texto. `EvaluadorConRiegoTest` (5): recién regada `SALUDABLE` con la humedad en `ALTO`, empapada sin riego `CRITICO`, el orden de las recomendaciones con la luz mala, ya seca hasta el rango, sin historial como antes.
- Aplicación con dobles: `ServicioMedicionesTest` (1 nueva): media hora seca, el riego y la lectura de ahora dan `SALUDABLE` con la recomendación del riego.
- Mascota: `MapaDeAnimoDeMascotaTest` (3 nuevas): recién regada es `FELIZ`; otro parámetro fuera de rango sigue mandando; recién regada pero seca sigue `SEDIENTA`.
- Adaptadores: `AdaptadorDiagnosticoJpaTest` (1 nueva y una aserción más; H2 con Flyway hasta V24): `regadaEn` sobrevive a la base; sin riego se lee `null`.
- Suite completa (`./mvnw test` por paquetes, JDK 21): 1106 pruebas, 1105 pasan. La que falla, `ConfiguracionCorsTest.preflightDesdeElOrigenDelFrontDevuelveElHeaderDeCors`, ya fallaba en `develop`: `ConfiguracionCors` permite `PATCH` y la prueba no lo espera. No es de esta fase y no se tocó.

## Qué puede hacer el front

Nada es obligatorio: la recomendación ya sale bien en "Qué hacer hoy" y el estado ya no se degrada. Opcional: en `voz-planta.js`, con `regadaEn` y la humedad en `ALTO`, cambiar "Tengo demasiada agua" por algo como "Me acabas de regar". En el detalle, mostrar "regada hace X" con `regadaEn`.

## Límites

- El criterio de riego está escrito dos veces: en `DetectorDeSaltos` (mascota) y en `ReglaDeHumedadTrasRiego` (diagnóstico). Si cambia uno, hay que cambiar el otro. Unirlos pide un núcleo compartido que los dos contextos puedan usar sin depender el uno del otro.
- Cada lectura consulta unas 18 000 filas de humedad (25 h a una lectura cada 5 s), porque el resumen por hora de la fase 12 (D175) es solo de la luz y una mediana por hora no sirve para ver una subida de un minuto. Es el costo que tuvo la luz en la fase 8 (D127). Optimización posible: leer solo la última hora y media y heredar el `regadaEn` del diagnóstico anterior mientras esté dentro de la gracia.
- La gracia es igual para todas las especies. Hacerla por especie es una columna del catálogo, como las horas de luz (D125).
- Si la matera estuvo apagada y vuelve con la tierra más húmeda, la subida queda en el hueco y no se ve como riego: el exceso cuenta. Es la salida conservadora.
- Mover la sonda o meterla en un vaso de agua se ve igual que un riego. El "la regué" declarado de la mascota (`RIEGO_DECLARADO`) no entra todavía al diagnóstico.

## Para el documento de arquitectura

Qué cambió en el dominio: la regla de humedad. Antes la humedad se comparaba con la lectura del momento; ahora, si la planta se regó en las últimas 24 h, la humedad por encima del rango no cuenta para el estado y la recomendación dice que quedó hidratada. La regla vive en una clase nueva del dominio, `ReglaDeHumedadTrasRiego`, gemela de `ReglaDeLuzAcumulada`, y el diagnóstico agrega `regadaEn`.

Qué no cambió: el dominio sigue sin obtener datos. La aplicación pide al puerto que ya existía la serie de humedad y se la entrega al evaluador. El clasificador, la regla de agregación, el redactor y el detector de cambios no se tocaron.

Por qué demuestra la hexagonal: la regla entró en un commit que solo toca el dominio y pasa las pruebas solo; llevarla a la base, HTTP y la mascota fue otro commit que no toca el dominio. Es abierto/cerrado: se agregó comportamiento con una clase nueva, sin modificar las reglas existentes.
