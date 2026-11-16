# Fase 9: la planta como mascota

Cuaderno de implementación (como `docs/fase7/`). Pedido: la planta como tamagotchi en el servidor. Vitalidad, eventos (te regaron, saliste al sol, te desmayaste, reviviste), racha, ánimo y dos acciones del dueño (acariciar y "la regué"). Todo es un contexto nuevo, `mascota`, que **consume** el historial de diagnósticos por los puertos que ya existían y produce su propio estado. El dominio de diagnóstico no cambió: `git diff --stat origin/develop -- src/main/java/com/vivero/fitodiagnostico/dominio` da vacío.

La idea y el orden vienen de `SPEC-FASE7-TAMAGOTCHI.md` (carpeta de la entrega en AIOS). La fase 7 puso las caras en la OLED; esta pone la memoria en el servidor.

## Dónde vive y por qué no en el dominio

Pregunta de sustentación: *"¿En qué capa pondrías la gamificación y por qué no en el dominio de la planta?"*

La mascota vive en su propio contexto, `com.vivero.fitodiagnostico.mascota`, con modelo, puertos y servicios, y su único contacto con el diagnóstico es leer. El dominio de diagnóstico responde una pregunta de agronomía: con estos tres valores y esta especie, ¿la planta está bien? Esa respuesta tiene que ser la misma hoy, mañana y con o sin juego encima. La vitalidad, la racha o el desmayo son una **política de presentación y de enganche** sobre el historial: cambian cada vez que se ajusta la diversión (una tasa, un umbral, un evento nuevo) y nada de eso debe poder mover un rango, un BAJO/OPTIMO/ALTO o el estado global. Si la mascota estuviera en el dominio, ajustar cuánto baja la vitalidad por hora sería tocar la misma capa que decide si una planta está CRITICA, y el diagnóstico pasaría a depender de reglas de juego. Por eso la flecha va en un solo sentido: la mascota conoce `EstadoGlobal`, `Diagnostico` y `ResultadoParametro`; el dominio y la aplicación del diagnóstico no conocen a la mascota. Lo comprueba ArchUnit (`el_nucleo_no_conoce_a_la_mascota`): si mañana se borra el paquete `mascota` con su composición y sus adaptadores, el diagnóstico compila y sus pruebas pasan igual.

## Qué hay

| Pieza | Dónde |
|---|---|
| Reglas puras: vitalidad, desmayo, revivir, racha | `mascota/modelo/ReglasDeVitalidad.java` |
| REGADA y SALIO_AL_SOL | `mascota/modelo/DetectorDeSaltos.java` (mediana en `Mediana.java`; cada lectura del historial se reduce a `LecturaReciente`) |
| Ánimo | `mascota/modelo/MapaDeAnimo.java`, `Animo.java` |
| Confirmar o desmentir "la regué" | `mascota/modelo/ConfirmadorDeRiego.java`, `AccionDeMascota.java` |
| Umbrales y tasas | `mascota/modelo/ParametrosDeMascota.java` (constantes) y `config/PropiedadesDeMascota.java` (`fitodiagnostico.mascota.*`) |
| Puertos de entrada | `mascota/puerto/entrada/`: `AlimentarMascotaPort`, `AvanzarMascotasPort`, `ConsultarMascotaPort`, `ActuarSobreMascotaPort` |
| Puertos de salida | `mascota/puerto/salida/`: `RepositorioMascotas`, `RepositorioEventosDeMascota`, `RepositorioAccionesDeMascota`, `PublicadorDeMascota` |
| Casos de uso | `mascota/aplicacion/ServicioMascota.java` (escribe) y `ServicioConsultaDeMascota.java` (solo lee) |
| Costura con las mediciones | `aplicacion/ObservadorDeMediciones.java` (puerto de aplicación) y el puente en `config/ConfiguracionMascota.java` |
| REST | `adaptador/entrada/rest/MascotaController.java` y sus DTO `RespuestaMascota`, `RespuestaEventoDeMascota`, `RespuestaAccionDeMascota`, `SolicitudAccionDeMascota` |
| Tarea periódica | `adaptador/entrada/programador/TicDeMascota.java` |
| Persistencia | `adaptador/salida/jpa/AdaptadorMascotaJpa.java` y tres entidades; migraciones V16 a V19 en `h2/` y `postgresql/` |
| Avisos | `adaptador/salida/notificacion/PublicadorDeMascotaPorWebSocket.java`, `PublicadorDeMascotaTrasCommit.java`; `adaptador/salida/mqtt/PublicadorDeMascotaMqtt.java` |
| Composición | `config/ConfiguracionMascota.java` |

```
  POST .../mediciones ─┐
  lectura del chip  ───┼─▶ ServicioMediciones ─(commit)─▶ ObservadorDeMediciones (puente en config)
                       │                                     │
                       │                                     ▼  REQUIRES_NEW
  TicDeMascota (1 min) ─────────────────────────────▶ ServicioMascota ──▶ RepositorioMascotas / Eventos / Acciones (JPA)
  POST .../mascota/acciones ────────────────────────▶      │
                                                           └─(commit)─▶ PublicadorDeMascota ─▶ WebSocket (front)
  GET .../mascota, .../mascota/eventos ─▶ ServicioConsultaDeMascota                       └──▶ MQTT (matera)
```

## Eventos y umbrales

Valores por defecto. Todos son provisionales hasta calibrar con la sonda real (sección "Cómo calibrar").

| Evento | Cuándo | Umbral por defecto | Detalle que guarda |
|---|---|---|---|
| `REGADA` | La humedad del sustrato de una lectura (candidata) y de la siguiente (confirmación) están al menos X puntos sobre la mediana de la hora anterior a la candidata, y la lectura justo antes de la candidata no lo estaba | X = 15 puntos; mínimo 3 lecturas en la ventana; 30 min entre dos REGADA | `magnitud`, `referencia`, `umbral`, `valor`, `confirmacion`, `confirmadaEn` |
| `SALIO_AL_SOL` | La misma regla con la luz | umbral = max(mediana x 2, mediana + 2000 lux); 30 min entre dos | ídem |
| `DESMAYO` | La vitalidad llega a 0 | - | `estado` en que llegó a 0 |
| `REVIVIO` | Desmayada y SALUDABLE sin interrupción durante un rato | 10 min; revive con al menos 20 de vitalidad | `vitalidad` |
| `ACARICIADA` | El dueño la acaricia | - | vacío |
| `RACHA` | Suma un día completo sin CRITICO | 24 h corridas | `dias` |
| `RIEGO_DECLARADO` | El dueño dice "la regué" | - | `accionId`, `venceEn` |
| `RIEGO_CONFIRMADO` | El sensor confirma el riego declarado | plazo 15 min | `accionId`, `motivo` (`REGADA_DETECTADA` o `HUMEDAD_SUBIO`), referencia |
| `RIEGO_DESMENTIDO` | Venció el plazo sin confirmación | 15 min | `accionId`, `motivo` (`SIN_LECTURAS` o `LA_HUMEDAD_NO_SUBIO`) |

| Vitalidad | Valor por defecto |
|---|---|
| Inicial (primera lectura) | 80 |
| En CRITICO | -20 por hora (100 a 0 en 5 h) |
| EN_RIESGO | -5 por hora (100 a 0 en 20 h) |
| SALUDABLE | +10 por hora (0 a 100 en 10 h) |
| Sin lecturas | se extrapola el último estado hasta 2 h después de la última lectura; después se congela |

## Ánimo

Sale del último diagnóstico y de la vitalidad. No se guarda: se calcula al consultar y al avisar.

| Ánimo | Cuándo | Precedencia |
|---|---|---|
| `DESMAYADA` | vitalidad en 0 y todavía no revive | 1, gana sobre todo |
| `SIN_DATOS` | la planta nunca se midió | 2 |
| `SEDIENTA` / `AHOGADA` | humedad del sustrato BAJO / ALTO | 3: si fallan varios, gana el de mayor desviación relativa (la del dominio, fracción del ancho del rango); a igual desviación, humedad, luz, temperatura |
| `A_OSCURAS` / `DESLUMBRADA` | luz BAJO / ALTO | 3 |
| `CON_FRIO` / `CON_CALOR` | temperatura BAJO / ALTO | 3 |
| `CANSADA` | todo en rango pero vitalidad menor que 30 | 4 |
| `FELIZ` | todo en rango y vitalidad 30 o más | 4 |

La consulta devuelve además la `causa`: el parámetro, la dirección y la desviación, para que el front y la OLED gradúen la cara ("un poco sedienta" contra "muy sedienta").

## Contrato

Mismas reglas de acceso que la planta: dueño e invitados consultan; solo el dueño actúa (invitado 403 `OPERACION_NO_PERMITIDA`); ajeno o inexistente 404 `PLANTA_NO_ENCONTRADA`; sin token 401. Con token de dispositivo, 403 como en cualquier ruta que no sea la de lecturas.

### `GET /api/v1/plantas/{id}/mascota`

```bash
curl $B/plantas/$PLANTA_ID/mascota -H "Authorization: Bearer $TOKEN"
```

```json
{
  "plantaId": "2b9f7c1e-5d0a-4e8b-9a51-0c3e7f6d1a22",
  "hayLecturas": true,
  "vitalidad": 64,
  "desmayada": false,
  "animo": "SEDIENTA",
  "causa": { "parametro": "humedad", "direccion": "BAJO", "desviacion": 0.27 },
  "estadoDelDiagnostico": "EN_RIESGO",
  "racha": { "dias": 3, "desde": "2026-11-13T07:41:10Z" },
  "actualizadaEn": "2026-11-16T15:02:00Z",
  "ultimosEventos": [
    { "id": "8d6c...", "tipo": "RIEGO_DECLARADO", "ocurridoEn": "2026-11-16T15:01:40Z",
      "detalle": { "accionId": "f3a1...", "venceEn": "2026-11-16T15:16:40Z" } },
    { "id": "51e0...", "tipo": "ACARICIADA", "ocurridoEn": "2026-11-16T14:58:03Z", "detalle": {} },
    { "id": "0b77...", "tipo": "RACHA", "ocurridoEn": "2026-11-16T07:41:10Z", "detalle": { "dias": "3" } }
  ],
  "accionPendiente": {
    "id": "f3a1...", "tipo": "LA_REGUE", "estado": "PENDIENTE",
    "creadaEn": "2026-11-16T15:01:40Z", "venceEn": "2026-11-16T15:16:40Z", "resueltaEn": null
  }
}
```

Sin lecturas responde 200 con `hayLecturas: false`, `vitalidad: 80`, `animo: "SIN_DATOS"`, `causa`, `estadoDelDiagnostico` y `actualizadaEn` en `null`, racha 0 y sin eventos (como el diagnóstico vigente, RN9). `causa` es `null` cuando el ánimo no viene de un parámetro. `ultimosEventos` trae los 5 más recientes por `ocurridoEn`. Los valores del `detalle` son siempre texto.

### `GET /api/v1/plantas/{id}/mascota/eventos?pagina=0&tamano=20`

La línea de tiempo, del más reciente al más antiguo. Misma forma de página que el historial de diagnósticos; `tamano` de 1 a 100, fuera de eso 400.

```json
{
  "contenido": [ { "id": "...", "tipo": "REGADA", "ocurridoEn": "2026-11-16T14:40:05Z",
                   "detalle": { "magnitud": "humedad", "referencia": "31.0", "umbral": "46.0", "valor": "52.5",
                                "confirmacion": "53.0", "confirmadaEn": "2026-11-16T14:40:10Z" } } ],
  "pagina": 0, "tamano": 20, "total": 1, "totalDePaginas": 1
}
```

### `POST /api/v1/plantas/{id}/mascota/acciones`

```bash
curl -X POST $B/plantas/$PLANTA_ID/mascota/acciones -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"tipo": "LA_REGUE"}'
```

| `tipo` | Respuesta |
|---|---|
| `ACARICIAR` | 201 `{"id": "<id del evento>", "tipo": "ACARICIAR", "estado": "HECHA", "creadaEn": "...", "venceEn": null, "resueltaEn": null}` |
| `LA_REGUE` | 202 `{"id": "<id de la acción>", "tipo": "LA_REGUE", "estado": "PENDIENTE", "creadaEn": "...", "venceEn": "+15 min", "resueltaEn": null}`. Si el sensor ya había visto el riego, 201 con `"estado": "CONFIRMADA"`. Repetirlo mientras hay una pendiente devuelve la misma (202) |
| otro, vacío o sin `tipo` | 400 `PARAMETRO_INVALIDO` |

El resultado de "la regué" llega después como evento `RIEGO_CONFIRMADO` o `RIEGO_DESMENTIDO` por WebSocket, y en la consulta `accionPendiente` vuelve a `null`.

### Vista en vivo (WebSocket)

Mismo destino que los avisos de la planta: `/user/queue/plantas/{id}` (suscribirse exige poder leerla). Un mensaje por evento:

```json
{
  "tipo": "EVENTO_DE_MASCOTA",
  "mensaje": "«Pepa» recibió agua.",
  "datos": { "plantaId": "...", "evento": "REGADA", "eventoId": "...", "ocurridoEn": "2026-11-16T14:40:05Z",
             "vitalidad": "64", "animo": "FELIZ" },
  "detalle": { "referencia": "31.0", "valor": "52.5", "...": "..." }
}
```

### Matera (MQTT)

Solo con `FITO_MQTT_HABILITADO=true`. Tópico `fito/dispositivos/{serial}/mascota` (al lado de `.../lecturas` y `.../respuestas`), uno por cada matera vinculada a la planta, QoS 1, sin retener:

```json
{"evento":"REGADA","ocurridoEn":"2026-11-16T14:40:05Z","vitalidad":64,"animo":"FELIZ","detalle":{"referencia":"31.0"}}
```

### Propiedades

| Propiedad (`fitodiagnostico.mascota.`) | Por defecto |
|---|---|
| `vitalidad-inicial`, `vitalidad-al-revivir`, `vitalidad-cansada` | 80, 20, 30 |
| `perdida-por-hora-en-critico`, `perdida-por-hora-en-riesgo`, `ganancia-por-hora-saludable` | 20, 5, 10 |
| `tiempo-para-revivir`, `ventana-sin-lecturas`, `duracion-del-dia-de-racha` | 10m, 2h, 24h |
| `subida-minima-de-riego`, `factor-de-sol`, `subida-minima-de-sol` | 15, 2.0, 2000 |
| `ventana-de-referencia`, `muestras-minimas-de-referencia`, `lecturas-a-revisar` | 1h, 3, 60 |
| `enfriamiento-de-eventos`, `plazo-para-confirmar-riego` | 30m, 15m |
| `tic.habilitado`, `tic.intervalo` | true, PT1M |

## Decisiones

**D130. La mascota es un contexto propio, `com.vivero.fitodiagnostico.mascota`, y no `aplicacion.mascota`.** Tiene `modelo` (reglas puras), `puerto.entrada`, `puerto.salida` y `aplicacion` (servicios). El pedido sugería `aplicacion.mascota`, pero ArchUnit ya prohíbe que un adaptador dependa de cualquier cosa bajo `..aplicacion..` (fase 2: "los adaptadores llaman a puertos, no a servicios"). Con los puertos dentro de `aplicacion.mascota`, el controlador REST y el adaptador JPA violarían esa regla, y la regla no se relaja. Los puertos tampoco pueden ir en `dominio.puerto`, porque esta fase no toca el dominio. Así que el contexto tiene su propio hexágono pequeño al lado del grande. Los adaptadores siguen en el árbol `adaptador/` de siempre (no dentro de `mascota/`), para que les apliquen las mismas reglas de islas (REST no conoce JPA, MQTT no conoce WebSocket). Seis reglas nuevas de ArchUnit: el núcleo no conoce a la mascota, el modelo de la mascota es puro (solo `dominio.modelo` y `java`), sus puertos son interfaces, sus puertos de entrada los implementa su aplicación, los de salida un adaptador de salida, y la mascota no depende de adaptadores ni de `config`. Descartado: un `dominio.gamificacion` como decía el plan de evolución de `ARQUITECTURA.md`; llamarlo dominio invita a confundirlo con el del diagnóstico, y la sustentación pide justo lo contrario.

**D131. La vitalidad se integra por tramos con el reloj del servidor, y nunca muere.** Entre la última actualización y ahora, la planta estuvo en el último estado que dijo el sensor; cada estado tiene una tasa por hora (D139). La integración usa la hora del servidor y no la `marcaDeTiempo` del chip, para que una lectura atrasada (cola MQTT) no reescriba el pasado. Sin lecturas nuevas se extrapola hasta 2 horas y ahí se congela: sin datos no se castiga (una matera apagada no mata a la mascota). En 0 la mascota se desmaya una sola vez (evento con la hora exacta en que llegó a 0) y se queda en 0. Revive tras 10 minutos seguidos SALUDABLE, con un piso de 20 para que la siguiente lectura EN_RIESGO no la tumbe al instante. No hay muerte: perder la mascota por una semana de viaje castiga al usuario sin ayudar a la planta.

**D132. La racha cuenta bloques de 24 horas corridas sin CRITICO, no días de calendario.** Empieza con la primera lectura que no es CRITICO; entrar en CRITICO la pone en 0. Hay un evento RACHA cada vez que suma, con la hora en que se cumplió el bloque. Romperla no emite evento (se ve en la consulta: `racha.dias` vuelve a 0). Días de calendario exigirían una zona horaria por usuario que no existe en el modelo. Un hueco sin lecturas no la rompe: la racha mide tiempo sin CRITICO **conocido**.

**D133. Nueve tipos de evento, con detalle de texto a texto.** Tabla arriba. Además de los seis pedidos se agregaron `RIEGO_DECLARADO`, `RIEGO_CONFIRMADO` y `RIEGO_DESMENTIDO`, para que el "la regué" quede en la línea de tiempo y llegue al front y a la matera por el mismo camino que todo lo demás. El detalle es `Map<String, String>` ordenado por clave: lo lee una persona en la base y lo lee una máquina en el front, sin tipos dinámicos.

**D134. REGADA: salto sobre la mediana, confirmado, en el borde de subida y con enfriamiento.** Es la regla candidata de la spec: dos lecturas seguidas con la humedad al menos 15 puntos sobre la mediana de la última hora. Las tres defensas ante una señal equivocada: (1) la lectura físicamente imposible ya la rechaza el servidor con 400 y nunca llega al historial; (2) la mediana, y no el promedio, hace que un pico aislado en la ventana no mueva la referencia; (3) una sola lectura nunca dispara, la siguiente tiene que confirmar, y un valor que sube y baja se ignora. Se agregó "la lectura anterior a la candidata estaba bajo el umbral" para disparar en el borde y no en la meseta de después del riego, y 30 minutos de enfriamiento como segunda barrera. Con menos de 3 lecturas en la ventana no hay detección: no hay contra qué comparar. Se ordena por la hora en que se tomó la lectura (`tomadaEn`), así que una cola de lecturas que llega desordenada se evalúa en el orden en que pasó.

**D135. SALIO_AL_SOL: la misma regla con un umbral doble.** La luz tiene que duplicarse **y** subir 2000 lux sobre la mediana. Solo el factor haría "sol" de 10 a 25 lux de noche; solo la suma haría "sol" de 30000 a 32000 lux a mediodía. El día y la noche no se implementan aquí: los puso en el dominio la fase 8 (`Diagnostico.periodo()`, D121). Hay dos comentarios donde la mascota lo consumirá: en `DetectorDeSaltos.sol` (descartar el salto del amanecer, candidata DIA con referencia NOCHE) y en `MapaDeAnimo.animoDe` (luz BAJO de NOCHE será DORMIDA y no A_OSCURAS).

**D136. El ánimo es un mapa en el modelo de la mascota y no se guarda.** El dominio ya dice qué parámetro falla, en qué dirección y cuánto (`desviacionRelativa`); la mascota lo traduce a una cara. Si fallan varios gana el que más se desvía, el mismo criterio que usa el dominio para decidir el estado, así que la cara y el estado no se contradicen. CANSADA existe para que la vitalidad se note en la cara: todo en rango pero recién salida de un desmayo no es FELIZ. No se persiste porque es función pura de dos cosas que sí están guardadas; guardarlo sería tener dos verdades.

**D137. Acariciar es solo un evento: no cura.** Registra ACARICIADA, se empuja a la matera y al front, y nada más. Si acariciar subiera la vitalidad, el juego podría tapar una planta enferma, y la vitalidad dejaría de decir algo sobre la planta real. La vitalidad sale solo del sensor.

**D138. "La regué" queda PENDIENTE y el sensor la confirma o la desmiente.** Al declararlo se guarda la mediana de humedad de la última hora como referencia y un plazo de 15 minutos. Se confirma por dos caminos: hubo una REGADA entre 15 minutos antes de declarar y el vencimiento (el dueño suele avisar después de regar), o dos lecturas seguidas tomadas después de declarar quedan 15 puntos sobre la referencia (cubre el caso en que la REGADA no se emitió por el enfriamiento). Si vence sin confirmación, DESMENTIDA, con el motivo: no llegaron lecturas o la humedad no subió. Responde 202 mientras está pendiente, porque el resultado llega después. Repetirlo con una pendiente devuelve la misma (idempotente): decirlo dos veces no abre dos esperas.

**D139. Umbrales y tasas como constantes nombradas, configurables por propiedades.** `ParametrosDeMascota` es un record con una constante por valor (con su porqué en el Javadoc) y `POR_DEFECTO`. `PropiedadesDeMascota` (en `config`) arranca cada campo con esa constante y Spring la sobreescribe con `fitodiagnostico.mascota.*`. Las reglas reciben el record y se prueban sin Spring con los valores por defecto o con otros. El record valida rangos al construirse: una configuración absurda no arranca.

**D140. El disparador es un puerto de aplicación en `ServicioMediciones`, y el puente vive en la composición.** `aplicacion.ObservadorDeMediciones` ("se registró una lectura") es una interfaz del núcleo; `ServicioMediciones` recibe la lista de observadores y llama a cada uno después de guardar y avisar. No sabe que la mascota existe. El puente que la conecta está en `ConfiguracionMascota`: espera al commit de la lectura y entonces llama a `AlimentarMascotaPort`, que corre en su propia transacción (`REQUIRES_NEW`). Así una falla de la mascota nunca deshace una lectura ni le cambia la respuesta al dispositivo. Descartados: (1) `ApplicationEventPublisher` con `@TransactionalEventListener`, que ArchUnit prohíbe en la aplicación desde la D57; (2) reusar el `Notificador` con un tipo de aviso nuevo, porque `TipoDeAviso` vive en el dominio y el aviso no trae los valores de la lectura; (3) llamar a la mascota desde `ServicioDiagnostico`, que es el diagnóstico suelto y no toca historial. Cambio en el núcleo: un constructor más en `ServicioMediciones` (el de antes sigue, sin observadores) y `AutorizadorDePlantas` pasa a público (D143). Ni una línea del dominio.

**D141. Una tarea periódica cada minuto, porque el tiempo pasa aunque no lleguen lecturas.** Sin ella, una planta CRITICA con la matera apagada no se desmayaría nunca, la racha solo sumaría con la siguiente lectura y un "la regué" sin lecturas no vencería jamás. `TicDeMascota` es un adaptador de entrada (quien llama es el reloj, no una persona) que recorre las mascotas y avanza cada una en su propia transacción: una planta que falla se registra y no deshace las demás. Es ligera: integra, resuelve la acción pendiente si la hay, y escribe solo si la vitalidad cambió o hubo eventos (una mascota SALUDABLE con 100 no escribe nada). Lee el historial solo si hay un "la regué" pendiente. Se apaga con `fitodiagnostico.mascota.tic.habilitado=false`; el perfil de pruebas la apaga y las pruebas que necesitan el paso del tiempo llaman al puerto con un reloj manual. Descartado: extrapolar al consultar. Rompe "consultar nunca escribe" o deja los desmayos sin evento.

**D142. Avisos por los dos canales que ya existían, después del commit.** Vista en vivo: el mismo destino `/user/queue/plantas/{id}` y el mismo sobre `{tipo, mensaje, datos}` que los avisos de la planta, más un campo `detalle`, con `tipo: "EVENTO_DE_MASCOTA"`. La suscripción ya está protegida por `ProtectorDeCanalStomp`, así que no hubo que tocar seguridad. Matera: `fito/dispositivos/{serial}/mascota`, siguiendo la convención de la fase 4, un mensaje por matera vinculada; reusa la conexión del publicador de respuestas (una conexión menos al broker) y solo existe si MQTT está encendido. Un decorador, `PublicadorDeMascotaTrasCommit`, reparte a los canales después del commit de la mascota (mismo argumento de la D57) y aísla las fallas de cada uno. Conoce solo el puerto, no las clases de los canales, por eso vive en `adaptador.salida.notificacion` sin depender de `adaptador.salida.mqtt`. El MQTT se prueba sin broker: el adaptador recibe un `MessageHandler` y la prueba le pasa uno que captura.

**D143. Las reglas de acceso son las de la planta, reusadas.** `AutorizadorDePlantas` pasó de visibilidad de paquete a público para que los servicios de la mascota lo usen: dueño e invitados leen, solo el dueño actúa, un ajeno recibe el mismo 404 que si la planta no existiera. Copiarlo habría dejado dos lugares que decidir quién ve qué.

**D144. Las tablas de la mascota tienen `ON DELETE CASCADE` hacia `planta`, al revés que la D37.** La D37 pone la cascada en `CascadaDeEliminacion` porque borrar una planta tiene efectos de negocio (avisar a los invitados, desvincular dispositivos). La mascota es una proyección del historial sin nada que avisar al borrarse. Con la cascada en la aplicación, `CascadaDeEliminacion` tendría que conocer a la mascota, que es justo la dependencia que la fase evita. Efecto secundario útil: las pruebas existentes que limpian con `delete from planta` siguen funcionando sin saber que hay tablas nuevas.

**D145. Tres tablas y una migración de índices, V16 a V19.** V15 es de la fase 8 (luz acumulada y `periodo`). `mascota` (una fila por planta, clave `planta_id`), `evento_mascota` (solo se agrega) y `accion_mascota`. El detalle del evento es JSON en un `VARCHAR(1000)`: es para mostrar, nadie consulta por él con SQL, y H2 y PostgreSQL lo tratan igual (el criterio contrario de la D39 aplicaba a los valores del diagnóstico, que sí se consultan). V19 crea `ix_evento_mascota_linea (planta_id, ocurrido_en DESC, registrado_en DESC)` para la línea de tiempo y `ix_accion_mascota_estado (planta_id, estado)` para "la pendiente de esta planta". Los CHECK de tipo y estado siguen la costumbre de V7: agregar un tipo de evento exige una migración, a propósito.

**D146. Consultar la mascota no escribe ni extrapola.** `ServicioConsultaDeMascota` es `readOnly`, no tiene el publicador y devuelve lo guardado. La vitalidad mostrada tiene a lo sumo un minuto de atraso (el tic). Sin lecturas responde 200 con `hayLecturas: false`: no es un error.

**D147. La lectura y el tic no se pisan: `SELECT ... FOR UPDATE`.** Los dos leen, calculan y guardan la misma fila. `RepositorioMascotas.paraActualizar` bloquea la fila hasta el fin de la transacción. Descartado: `@Version` optimista, porque el conflicto tiraría la lectura de la mascota y sus eventos.

**D148. Las lecturas se leen del historial que ya existía, una página por lectura nueva.** La mascota no guarda lecturas propias: pide al `RepositorioDiagnosticos` las últimas 60 (configurable, hasta 100, el máximo de una página del puerto) y filtra la última hora. Con el chip a una lectura cada 5 segundos (`INTERVALO_LECTURA_MS`), 60 lecturas son 5 minutos, que alcanzan para un riego (el salto es inmediato). Costo: una consulta de 60 filas con sus parámetros por cada lectura. Es la evidencia de que la mascota consume el historial. Si pesa, el siguiente paso es un puerto de lectura más fino (solo humedad y luz) y no un buffer propio.

**D149. Dos relojes, cada uno para lo suyo.** La vitalidad, la racha, el desmayo y los plazos de "la regué" corren con el reloj del servidor (`Clock` inyectado, probado con `RelojManual`). Los saltos y la confirmación por lecturas comparan la hora en que se tomó cada lectura. Un chip con la hora corrida mueve dónde cae una REGADA en la línea de tiempo, pero no cuánta vitalidad pierde la planta.

## Cómo calibrar con la sonda real

Los umbrales de arriba son razonados, no medidos. Para fijarlos:

1. **Ruido de la humedad.** Con la matera quieta una hora, `GET /api/v1/plantas/{id}/diagnosticos?tamano=100` y mirar cuánto se mueve la humedad entre lecturas. `subida-minima-de-riego` tiene que quedar holgado sobre ese ruido (tres o cuatro veces la diferencia máxima entre lecturas seguidas).
2. **Un riego real.** Regar con medio vaso y anotar la humedad antes, a los 5 s, a los 30 s y a los 5 min. Si la sonda tarda en subir (el agua tiene que llegar al sensor), subir `enfriamiento-de-eventos` o aceptar que la REGADA cae una o dos lecturas después del riego. Si sube menos de 15 puntos con un riego normal, bajar `subida-minima-de-riego`.
3. **Sol.** Sacar la matera de la sombra al sol y entrarla, con el BH1750. Ver el salto en lux y ajustar `factor-de-sol` y `subida-minima-de-sol`. Recordar que el BH1750 satura en 65535 lux.
4. **Tasas.** Decidir cuánto tiempo en CRITICO es "peligro real" para la especie más delicada del catálogo (el helecho se seca antes que el cactus) y ajustar `perdida-por-hora-en-critico` para que el desmayo llegue un poco antes.
5. Fijar los valores en `application.yml`, por argumento (`--fitodiagnostico.mascota.subida-minima-de-riego=12`) o por variable de entorno con el binding relajado de Spring (`FITODIAGNOSTICO_MASCOTA_SUBIDAMINIMADERIEGO=12`), y anotar en este documento la fecha y la sonda con que se midió.

## Pruebas

- Reglas puras, sin Spring: `ReglasDeVitalidadDeMascotaTest` (11: tasas, tope, ventana sin lecturas, desmayo con su hora, nunca muere, revivir y su piso, recaída, racha que suma y se rompe, hora anterior, configurables), `DetectorDeSaltosDeMascotaTest` (11: riego confirmado, una sola lectura no dispara, pico que sube y baja, meseta, enfriamiento, pocas lecturas, ventana, orden de llegada, sol, subidas pequeñas, referencia), `MapaDeAnimoDeMascotaTest` (9: los seis parámetro-dirección, el peor gana, feliz y cansada, desmayada y sin datos), `ConfirmadorDeRiegoDeMascotaTest` (7).
- Aplicación con dobles en memoria y el `ServicioMediciones` real conectado por el observador: `ServicioMascotaTest` (8: primera lectura, riego avisado a personas y matera, el tic desmaya sin lecturas y respeta la ventana, acariciar y sus permisos, "la regué" confirmada y desmentida, consulta con permisos, planta borrada).
- Adaptadores: `PublicadorDeMascotaPorWebSocketTest` (destino, sobre y reparto con un canal roto), `PublicadorDeMascotaMqttTest` (tópico y carga, sin broker).
- Integración con el contexto real, JPA y Flyway: `MascotaIntegracionTest` (7: sin lecturas no escribe, el ánimo sale del diagnóstico, accesos dueño/invitado/ajeno/sin token, acariciar llega a la vista en vivo, 400, "la regué" confirmada por dos mediciones, la cascada al borrar la planta).
- ArchUnit: las seis reglas nuevas en `ArquitecturaTest`.
- Comando: `MAVEN_OPTS=-Xmx512m ./mvnw test -Dtest='*Mascota*,ArquitecturaTest'` (91 pruebas, más `ServicioMedicionesTest`). Suite completa sobre `develop` con la fase 8: 972 pruebas, 0 fallas.

## Límites

- Los umbrales son provisionales (sección anterior).
- La mascota todavía no consume el día y la noche: de noche puede salir A_OSCURAS y el amanecer puede dar un SALIO_AL_SOL. `Diagnostico.periodo()` ya existe (fase 8, D121); falta copiarlo en `LecturaReciente` y usarlo en los dos puntos marcados (D135). Se dejó fuera a propósito: el pedido de esta fase era no implementar día/noche.
- Acariciar no tiene límite de frecuencia: alguien que toque mil veces genera mil eventos y mil mensajes MQTT. Si molesta, un enfriamiento de unos segundos en `ServicioMascota.actuar`.
- La primera fila de una mascota la crean sin bloqueo dos lecturas simultáneas de la misma planta; la segunda fallaría por clave duplicada y se perdería su paso (queda en el log). Un chip manda en serie, así que no se espera.
- La vitalidad de la consulta tiene hasta un minuto de atraso (el tic, D146).
- Con lecturas cada 5 s la ventana efectiva de la mediana son las últimas 60 lecturas, unos 5 minutos, no una hora (D148).
- V15 (`diagnostico_luz_acumulada`, fase 8) ya está en `develop` y esta rama va encima, así que la secuencia queda V14, V15, V16 a V19 sin huecos.
- El firmware y el front no consumen nada de esto todavía (siguiente sección).

## Qué falta afuera

**Front.** Suscribirse a `/user/queue/plantas/{id}` (hoy el front solo escucha `/user/queue/avisos`) y atender `tipo: "EVENTO_DE_MASCOTA"`: animar según `datos.evento` y refrescar vitalidad y ánimo con `datos.vitalidad` y `datos.animo`. En el detalle de la planta: `GET .../mascota` para la barra de vitalidad, la cara (`animo` y `causa`), la racha y el estado de "la regué"; botones "acariciar" (o tocar la planta) y "la regué" contra `POST .../mascota/acciones`; la línea de tiempo con `GET .../mascota/eventos`. Las caras tienen que ser las mismas que la OLED (regla de paridad del 12-nov).

**Firmware.** Suscribirse a `fito/dispositivos/{serial}/mascota` y animar: gota en REGADA (hoy la detecta el chip solo, D114; el servidor pasa a ser la verdad y el chip puede quedarse con su detección rápida solo para la animación inmediata), sol, desmayo y revivir, corazón en ACARICIADA. Mostrar la vitalidad si cabe. El botón BOOT como "acariciar" necesita que el chip pueda actuar: hoy el endpoint es de persona y el token de dispositivo no lo abre. Habría que decidir si la matera publica una caricia por MQTT o si se abre una ruta de dispositivo.
