# Fase 10: salud de la matera

Cuaderno de implementación (como `docs/fase8/`). Pedido: Santiago, 12-nov. El DHT22 estaba dañado. El firmware lo detectaba por el puerto serie y descartaba todas las lecturas (D98 a D108, `Sensores::leer` devuelve `false`), pero hacia afuera la matera se quedó muda: la OLED quieta en la última lectura buena, la app sin lecturas nuevas y sin explicación. Santiago pidió: "que diga sensor no conectado". Esta fase hace que la matera **diga qué le pasa**, por tres caminos que no dependen entre sí: la OLED (sin servidor), el servidor (cuando la matera habla) y el silencio (cuando no habla).

## Qué hay

| Pieza | Dónde | Capa |
|---|---|---|
| Reglas de cuándo reportar (`fito_salud_toca`) y "hace X" (`fito_hace_cuanto`) | `firmware/src/nucleo/fito_nucleo.{h,c}` | firmware, C puro |
| El puerto `Sensores` dice qué sensor falló y por qué | `firmware/src/sensores.h`, `sensores_compuestos.{h,cpp}` | firmware |
| `POST .../salud` | `firmware/src/cliente_api.{h,cpp}` (`api::enviarSalud`) | firmware |
| Máquina de estados: descarta, avisa en la OLED, reporta | `firmware/src/main.cpp` (`tomarLectura`, `atenderSalud`) | firmware |
| El aviso en la OLED | `firmware/src/pantalla.{h,cpp}` (`sensoresFallan`, `pintarAviso`) | firmware |
| El cuadro sin placa | `firmware/sprites/previsualizar.py` (`avisos()`) | herramienta |
| Tipos y puertos de la salud | `salud/` (`SaludDelDispositivo`, `ReportarSaludPort`, `ConsultarSaludPort`, `RevisarSilencioPort`, `RepositorioDeSalud`, `AvisadorDeSalud`) | núcleo de la feature (fuera de `dominio/`, D150) |
| Los casos de uso | `aplicacion/ServicioSalud.java`, armado en `config/ConfiguracionSalud.java` | aplicación |
| `POST /api/v1/dispositivos/{id}/salud` | `adaptador/entrada/dispositivo/SaludDeDispositivoController.java`, `SolicitudSalud.java` | entrada |
| La revisión de cada minuto | `adaptador/entrada/programado/RevisorDeSilencio.java` | entrada |
| `salud` y `ultimaLecturaEn` en los dispositivos | `adaptador/entrada/rest/dto/RespuestaSalud.java`, `RespuestaDispositivoDePlanta.java`, `RespuestaDispositivoAdmin.java` | entrada |
| La tabla | `db/migration/{h2,postgresql}/V20__dispositivo_salud.sql`, `V21__dispositivo_salud_ultima_lectura.sql`, `adaptador/salida/jpa/DispositivoSaludEntity.java`, `AdaptadorSaludJpa.java` | salida |
| El aviso en vivo | `adaptador/salida/notificacion/AvisadorDeSaludPorWebSocket.java` | salida |
| `--falla` | `simulador/simulador_esp32.py` | simulador |

## Cómo funciona

```
 sensor falla ──▶ main.cpp: lectura descartada ──┬──▶ OLED: "no te siento temperatura" (ya, sin servidor)
                                                  └──▶ fito_salud_toca? ──sí──▶ POST /dispositivos/{id}/salud
                                                                                      │
                          lectura completa ──▶ POST .../lecturas ──▶ ultimaLecturaEn  ▼
                                                                  ServicioSalud ──▶ dispositivo_salud
                                                                      │  (cambió)
                          cada minuto: ¿calló? ──▶ SIN_NOTICIAS ──────┴──▶ WebSocket SALUD_DE_LA_MATERA
```

Tres estados de conjunto: `OK`, `FALLA` (lo dice la matera, con el estado de cada sensor) y `SIN_NOTICIAS` (lo decide el servidor cuando la matera calla). Tres sensores, cada uno `OK` o `FALLA`: `humedad`, `luz`, `temperatura`.

## Decisiones

**D150. La salud del aparato no es una regla del dominio, y el dominio no se toca.** La fase pedía `git diff --stat origin/develop -- src/main/java/com/vivero/fitodiagnostico/dominio` en cero. La salud de la matera no decide nada sobre la planta: no entra al diagnóstico ni a las recomendaciones, y no cambia ninguna regla de clasificación. Por eso no hay clase nueva en `dominio/`, no se agrega valor a `TipoDeAviso` ni método a `Dispositivo`. Los tipos y los puertos viven en un paquete propio, `com.vivero.fitodiagnostico.salud`, que no depende de nada de infraestructura (solo Java). Se eligió un paquete aparte y no `aplicacion/` porque las reglas de ArchUnit (`los_adaptadores_dependen_de_puertos_y_no_de_servicios`) prohíben que un adaptador nombre algo de `..aplicacion..`: los puertos tienen que estar fuera. Consecuencia que hay que decir con franqueza: el aviso por WebSocket no puede pasar por el puerto `Notificador` (su `Aviso` exige un `TipoDeAviso`, que es un enum del dominio). La salud tiene su propio puerto de salida, `AvisadorDeSalud`, y su adaptador empuja `SALUD_DE_LA_MATERA` como texto por el mismo destino por planta (D157). Si más adelante se decide que la salud merece estar en el dominio, el traslado es mecánico: mover `salud/` a `dominio/` y agregar el valor al enum.

**D151. Una tabla aparte, `dispositivo_salud`, con una fila por serial (V20 y V21).** Se descartó agregar columnas a `dispositivo`: `Dispositivo` es del dominio (D150) y la salud nace y muere con sus reportes, no con el reclamo ni la credencial. Sin fila significa "esta matera nunca dejó noticia" (la respuesta trae `salud: null`). V20 crea la tabla con el estado de conjunto, los tres sensores, el detalle, `reportado_en` y `cambio_en`; tiene clave foránea al serial con `ON DELETE CASCADE` para que borrar el dispositivo (las pruebas lo hacen) se lleve su salud. V21 agrega `ultima_lectura_en`, que es cuándo **el servidor** recibió la última lectura (hora del servidor, no la `marcaDeTiempo` de la matera: una matera con la hora mal puesta no puede engañar a la revisión de silencio). Se guarda en la fila de salud y no se reusa `ultimo_contacto_en` de `dispositivo`: ese lo mueven el reto y la credencial, que la matera sigue pidiendo aunque no pueda medir nada (D93 a D95), y no son lecturas.

**D152. La matera dice `OK` o `FALLA` por sensor; el servidor decide `SIN_NOTICIAS`.** Una matera con la CPU colgada no puede reportar que está colgada. Si `SIN_NOTICIAS` lo dijera la matera, el caso peor (que no habla) sería el único sin cobertura. El estado de conjunto es `FALLA` si algún sensor falla, `OK` si ninguno. El cuerpo exige las tres claves (`400` si falta una, si sobra una o si un valor no es `OK` ni `FALLA`): el firmware siempre manda las tres, y exigirlas evita que un cuerpo a medias se lea como "los demás están bien". El `detalle` es opcional y de a lo sumo 200 caracteres; solo se guarda mientras algo falla (al recuperarse se borra, para no mostrar una causa que ya no existe).

**D153. El firmware reporta al cambiar, con un latido de 5 minutos mientras algo falle, y las reglas están en el núcleo en C.** `fito_salud_toca` (con constantes nombradas `FITO_SALUD_LATIDO_MS` = 5 min y `FITO_SALUD_ENTRE_INTENTOS_MS` = 10 s):

| Situación | Reporta |
|---|---|
| Todo bien y nunca reportó nada | Nunca. Una matera sana no gasta una llamada. |
| Cambió el estado de algún sensor respecto a lo último confirmado | Sí, ya (siempre que hayan pasado 10 s del intento anterior) |
| Sigue igual y algo falla | Un latido cada 5 min desde el último confirmado |
| Se recuperó | Sí: manda el estado `OK` una vez; después calla |
| El envío falló (sin red, 5xx) | Reintenta cada 10 s hasta que el servidor confirme (HTTP 200) |

Los 10 s entre intentos son el freno de un sensor que parpadea: sin él, un cable flojo mandaría un reporte por cada cambio. El latido existe por D156: sin él, una matera que reporta "falla" una vez y sigue sana de red pasaría a `SIN_NOTICIAS` a los pocos minutos. Las diferencias de `millis()` son modulares (uint32), así que el desborde a los 49 días no rompe nada. Las reglas están en el núcleo en C, no en `main.cpp`, para probarlas sin placa (ver Pruebas): el simulador tiene una traducción literal (`MemoriaDeSalud`) y `probar_nucleo.py` exige que las dos decidan lo mismo sobre los mismos guiones, incluidos 40 guiones al azar y el desborde de `millis()`.

**D154. La OLED avisa por su cuenta, sin esperar al servidor.** Lo pedido: que se note aunque no haya red, aunque el servidor esté caído o aunque el reporte falle. Cuando `Sensores::leer` devuelve `false`, `tomarLectura` pregunta al adaptador qué sensor falló (`Sensores::fallas`, nuevo en el puerto, con valor por defecto "ninguno" para los simulados) y repinta de inmediato:

```
mascota preocupada     no te siento
(cara y cuerpo de      temperatura
 "en riesgo")          lectura buena
                       hace 3 min
                       Poti
                       REVISA SENSOR
```

Con dos o tres sensores: `no siento` / `mis sensores`. Sin lectura buena desde que prendió: `sin lecturas` / `todavia`. Cosas decididas:
- **Sin artículo en la OLED.** La frase completa ("no te siento la temperatura") son 27 caracteres y la ventana de la derecha admite 13 por línea; se parte en dos líneas y se omite el "la". La app dice la frase completa (D158).
- **"hace X" y no la hora.** La OLED no sabe qué hora local es (solo tiene UTC por NTP) y una hora sin zona engaña. `fito_hace_cuanto` da `hace 45 s`, `hace 3 min`, `hace 2 h`; se recalcula en cada cuadro, así que sigue contando.
- **La mascota está al menos preocupada** (`nivelVisible()`: el nivel de la cara y el cuerpo es `max(nivel, 1)`), aunque el último diagnóstico bueno dijera `SALUDABLE`. El nivel real no cambia: al llegar la siguiente lectura completa (`pantalla::lecturas`) el aviso se apaga solo. Los números de la última lectura no se muestran: un 24,5 °C de hace 20 minutos junto a "no te siento la temperatura" es contradictorio.
- **La pantalla de red caída (`sinRed`) sigue mandando**, como ya hacía: sin Wi-Fi lo más grave es que no hay red.
- El cuadro se ve sin placa: `python3 firmware/sprites/previsualizar.py` genera `30_aviso_temperatura` a `38_aviso_girasol_critico` y `anim_aviso.gif` (espejo de `pintarAviso`).

**D155. Una lectura recibida es prueba de vida y de salud.** `POST .../lecturas` llama a `registrarLectura` después de guardar la lectura: anota `ultimaLecturaEn` y, si la matera estaba en `FALLA` o `SIN_NOTICIAS`, la pasa a `OK` con los tres sensores bien (una lectura completa significa que los tres midieron) y avisa. Así la recuperación no depende de que el reporte `OK` llegue antes que la primera lectura buena. Los dos caminos son idempotentes: el que llega segundo no vuelve a avisar. `registrarLectura` va en el controlador y no dentro de `ServicioMediciones`, que esta fase no toca. Las lecturas que llegan por MQTT (fase 4, apagado por defecto) no llaman a `registrarLectura`: ver Límites.

**D156. `SIN_NOTICIAS`: tres veces el intervalo esperado, con un mínimo de 2 minutos, y revisión cada minuto.** Una tarea (`@Scheduled`, `fixedDelay` de 60 s, primera pasada a los 60 s) recorre los dispositivos vinculados que alguna vez dejaron noticia. Una matera pasa a `SIN_NOTICIAS` si su última noticia (lectura o reporte, la que sea más reciente) tiene más de:
- con salud `OK`: `max(3 x intervalo esperado, mínimo)`. El intervalo por defecto es 5 s (la demo) y el mínimo 2 min, así que el tope real es **2 min**; con un intervalo configurado de 1 min sería 3 min. El mínimo evita spamear cuando el intervalo es de segundos y la red tiene un mal rato.
- con salud `FALLA`: `3 x latido de falla` = **15 min**. Una matera con un sensor dañado no manda lecturas, solo el reporte de salud cada 5 min (D153); su intervalo esperado es ese. Medirla con el tope de 2 min la pondría en `SIN_NOTICIAS` tres de cada cuatro minutos.

Configuración, todas ISO-8601 y con el valor de la demo por defecto: `FITO_SALUD_INTERVALO_ESPERADO` (`PT5S`), `FITO_SALUD_SILENCIO_MINIMO` (`PT2M`), `FITO_SALUD_LATIDO_DE_FALLA` (`PT5M`, tiene que ser el del firmware). Se avisa **una vez** al entrar en `SIN_NOTICIAS`; las pasadas siguientes la saltan. No se vigila una matera sin dueño (no hay a quién avisar) ni una que nunca dejó noticia (ese caso ya lo cubre `ultimaCredencialEn` nulo: "esperando que se conecte"). Cuando vuelve una lectura, sale de `SIN_NOTICIAS` por D155. Un reporte de salud también la saca: `reportar` guarda el estado que la matera dice.

**D157. El aviso por WebSocket: mismo destino por planta, solo cuando algo cambia, después del commit.** `SALUD_DE_LA_MATERA` sale a `/user/queue/plantas/{plantaId}` (el mismo que `DIAGNOSTICO_NUEVO` y `CAMBIO_DE_ESTADO`) al dueño y a los invitados, calculados al emitir (como `ServicioMediciones`: el invitado al que ya le quitaron el acceso no recibe). El front ya está suscrito a ese destino y solo agrega un caso al `switch` de `tipo`; `ProtectorDeCanalStomp` no cambia. La forma es la de `MensajeEnVivo` (`tipo`, `mensaje`, `datos` con valores de texto). Avisa en un cambio real: entra una falla, cambia el conjunto de sensores que fallan, se recupera, entra o sale de `SIN_NOTICIAS`. El latido de 5 minutos con el mismo estado **no** avisa. Sale tras el commit y un fallo del canal no se propaga (mismo patrón que `NotificadorTrasCommit`). No hay correo: una falla de sensor no merece un correo por cada flapeo; si se quiere, es otro canal sobre el mismo puerto.

**D158. La frase la pone el servidor, y la app puede armar la suya con los mismos datos.** `salud.mensaje` (y el `mensaje` del aviso) trae la frase en primera persona, la de la mascota. Para el "hace X" de `SIN_NOTICIAS`, que cambia con el tiempo, la app debe recalcularlo desde `ultimaNoticiaEn` y no repetir el texto que llegó.

| Caso | Frase |
|---|---|
| `OK` | (nada: no se muestra aviso) |
| `FALLA`, solo humedad | no te siento la humedad |
| `FALLA`, solo luz | no te siento la luz |
| `FALLA`, solo temperatura | no te siento la temperatura |
| `FALLA`, dos o tres sensores | no siento mis sensores |
| `SIN_NOTICIAS` | no sé de la matera desde hace 12 min (`hace N min` hasta 59; después `hace N h`) |

**D159. El contrato solo agrega.** Ningún campo existente cambia. `GET /plantas/{id}/dispositivos` y `GET /admin/dispositivos` agregan `ultimaLecturaEn` y `salud` (ambos `null` si la matera nunca dejó noticia). El endpoint nuevo usa el mismo token, alcance y errores que `POST .../lecturas`. Detalle abajo.

## Contrato

### `POST /api/v1/dispositivos/{serial}/salud` (la matera)

Con el token de dispositivo (`Authorization: Bearer ...`), alcance `lecturas:reportar`, igual que `.../lecturas`.

```json
{ "sensores": { "humedad": "OK", "luz": "OK", "temperatura": "FALLA" }, "detalle": "temperatura: dht22 sin respuesta (linea en reposo ALTO)" }
```

Respuesta `200`:

```json
{ "estado": "FALLA", "cambio": true }
```

`estado` es el de conjunto que quedó; `cambio` es `false` en un latido sin novedad. Errores, los mismos de las lecturas: `400 PARAMETRO_INVALIDO` (falta una de las tres claves, sobra una, un valor que no es `OK` ni `FALLA`, `detalle` de más de 200 caracteres, cuerpo ilegible), `401` sin token o si el dispositivo ya no está vinculado a la planta de su token (la matera renueva su credencial, como con una lectura), `403` con un token de persona, de otro dispositivo o sin el alcance. Una vez `OK`, el `detalle` se descarta.

### Dispositivos de una planta (la app)

`GET /api/v1/plantas/{plantaId}/dispositivos` (fase 12, D173: también el invitado, solo lectura; antes solo el dueño) y, con `salud` igual, `GET /api/v1/admin/dispositivos`:

```json
[
  {
    "serial": "fito-YUBUD6",
    "reclamadoEn": "2026-11-12T15:02:11.307412Z",
    "ultimaCredencialEn": "2026-11-16T14:20:00.114209Z",
    "ultimaLecturaEn": "2026-11-16T14:12:40.530118Z",
    "salud": {
      "estado": "FALLA",
      "sensores": { "humedad": "OK", "luz": "OK", "temperatura": "FALLA" },
      "detalle": "temperatura: dht22 sin respuesta (linea en reposo ALTO)",
      "reportadoEn": "2026-11-16T14:20:05.001200Z",
      "cambioEn": "2026-11-16T14:12:45.774001Z",
      "ultimaNoticiaEn": "2026-11-16T14:20:05.001200Z",
      "mensaje": "no te siento la temperatura"
    }
  }
]
```

| Campo | Significado |
|---|---|
| `ultimaLecturaEn` | cuándo el servidor recibió la última lectura. `null`: nunca |
| `salud` | `null`: la matera nunca dejó noticia (mostrar "esperando", como con `ultimaCredencialEn` nulo) |
| `salud.estado` | `OK`, `FALLA` o `SIN_NOTICIAS` |
| `salud.sensores` | `humedad`, `luz`, `temperatura`: `OK` o `FALLA`. En `SIN_NOTICIAS` son lo último que se supo |
| `salud.detalle` | texto técnico para soporte (puede ser `null`); no mostrar a la persona |
| `salud.reportadoEn` | la última vez que la matera habló de su salud |
| `salud.cambioEn` | cuándo cambió el estado o algún sensor |
| `salud.ultimaNoticiaEn` | lo último que se supo (lectura o reporte): de aquí sale el "hace X min" |
| `salud.mensaje` | la frase (D158), calculada al pedir |

### El aviso por WebSocket

Destino `/user/queue/plantas/{plantaId}` (el mismo de `DIAGNOSTICO_NUEVO`). Al entrar en falla:

```json
{
  "tipo": "SALUD_DE_LA_MATERA",
  "mensaje": "no te siento la temperatura",
  "datos": {
    "plantaId": "b2c1c3e0-6a34-4f1b-9f3e-2d4a6a1f0c11",
    "serial": "fito-YUBUD6",
    "estado": "FALLA",
    "humedad": "OK",
    "luz": "OK",
    "temperatura": "FALLA",
    "detalle": "temperatura: dht22 sin respuesta (linea en reposo ALTO)",
    "cambioEn": "2026-11-16T14:12:45.774001Z",
    "ultimaNoticiaEn": "2026-11-16T14:12:45.774001Z"
  }
}
```

Al callar: `"mensaje": "no sé de la matera desde hace 2 min"`, `"estado": "SIN_NOTICIAS"` y los sensores en lo último que se supo. Al volver: `"mensaje": "todos mis sensores responden"`, `"estado": "OK"`. Todos los valores de `datos` son texto, como en los demás avisos; `detalle` solo aparece si hay.

## Qué debe hacer el front

El front no se tocó. Lo que le falta:
- Un caso nuevo `SALUD_DE_LA_MATERA` en el `switch` de `tipo` del mensaje de `/user/queue/plantas/{id}`: guarda `datos` como la salud vigente de ese `serial` (sin volver a pedir nada).
- Al abrir la planta, leer `GET /plantas/{id}/dispositivos` y usar `salud`. Con `estado` distinto de `OK`, un aviso arriba del diagnóstico con la frase de D158 (con `SIN_NOTICIAS`, recalcular el "hace X" desde `ultimaNoticiaEn` cada minuto). Mientras haya aviso, mostrar el diagnóstico como "dato de la última lectura", atenuado: sigue siendo el último que hubo, pero no es de ahora. Paridad con la OLED: mascota preocupada (regla del 12-nov).
- Sin `salud` (`null`): no mostrar aviso; con `ultimaCredencialEn` nulo ya dice "esperando que se conecte".
- El invitado no podía llamar a `GET .../dispositivos` y solo veía la salud por el aviso en vivo; desde la fase 12 (D173) sí puede.

## Pruebas

- **Aplicación, con dobles** (`ServicioSaludTest`, 15): una falla guarda y avisa al dueño y al invitado con la frase; un latido con el mismo estado no vuelve a avisar y no mueve `cambioEn`; dos sensores dan la frase general; recuperarse envía `OK`, borra el detalle y avisa; un primer reporte sano no avisa; una lectura guarda la hora, y si había falla recupera; `SIN_NOTICIAS` a los 2 min (no a los 119 s) aunque tres intervalos sean 15 s; con un intervalo largo manda el triple del intervalo; una matera que sigue reportando su falla cada 5 min no pasa a `SIN_NOTICIAS`, y una que calla 16 min sí (15 no); se avisa una sola vez; una lectura la saca del silencio; no se vigila lo que no tiene dueño ni lo que no dejó noticia; la consulta trae "desde hace 12 min".
- **De punta a punta** (`SaludDeDispositivoIntegracionTest`, 9, con Spring, seguridad, JPA y Flyway H2): el reporte se ve en `GET /plantas/{id}/dispositivos`; sin noticias `salud` y `ultimaLecturaEn` vienen `null`; una lectura marca `ultimaLecturaEn` y recupera; el administrador ve la salud; 401 sin token, 403 con token de persona y con token de otro dispositivo, 401 si ya no está vinculado, 400 por cuerpos inválidos.
- **Arquitectura**: `ArquitecturaTest` sigue pasando con el paquete nuevo (sin excepciones nuevas).
- **Firmware, sin placa** (`python3 firmware/test/probar_nucleo.py`): 139 comprobaciones, ninguna falla. Las 61 nuevas: 10 guiones de `fito_salud_toca` escritos a mano (latido a los 5 min y no antes, recuperación, freno de 10 s, envío fallido, desborde de `millis()`, parpadeo, de uno a dos sensores), 3 comprobaciones de los valores que importan, 40 guiones al azar y 8 de `fito_hace_cuanto`; en cada guion el C y la `MemoriaDeSalud` del simulador deciden lo mismo.
- **Compilación real**: `pio run -e esp32dev` con el `local.ini` de la placa (`sensores = reales`): compila. Flash **86,3 %** (1.130.537 de 1.310.720 bytes; antes 86 %). RAM 16,0 %. No se flasheó ni se tocó `/dev/ttyUSB0`.
- **Simulador**: `--falla gas` es rechazado; el cuerpo del reporte y el latido se verificaron con un transporte falso. No se corrió contra un backend vivo (la fase no arranca el backend).
- **Suite completa** (`MAVEN_OPTS=-Xmx512m ./mvnw test`, una vez, al final, con más de 2000 MB disponibles): 933 pruebas, ninguna falla (eran 909 en la fase 8; la fase 10 agrega 24).

## Límites

- **El invitado no ve la salud al abrir la pantalla.** `GET /plantas/{id}/dispositivos` es solo del dueño (hoy y desde la fase 3). El invitado se entera por el aviso en vivo, pero si abre la app después del aviso no sabe nada. Hacerlo bien es un endpoint de solo lectura para el invitado (o `salud` dentro de `GET /plantas/{id}`): cambio de contrato que no se pidió y quedó fuera. **Resuelto en D173** (fase 12: el invitado lee `GET /plantas/{id}/dispositivos`).
- **MQTT no cuenta como noticia.** Las lecturas que llegan por MQTT (fase 4, apagado por defecto) no llaman a `registrarLectura`; con MQTT prendido, una matera sana por esa vía pasaría a `SIN_NOTICIAS`. El firmware real usa HTTP. Arreglarlo es una línea en `SuscriptorDeLecturasMqtt` y cambiar su constructor y su prueba.
- **Desvincular y reclamar de nuevo no borra la salud.** La fila es del dispositivo, no de la planta. Si se traspasa una matera que estaba en `FALLA`, el dueño nuevo ve la falla vieja hasta la primera lectura o reporte. `ServicioDispositivos` tampoco se tocó. **Resuelto en D173** (fase 12: desvincular borra la fila de salud y su `ultimaLecturaEn`).
- **El latido hace que "falla sostenida" no aparezca como cambio.** Después del primer aviso la app no se entera de nada más hasta que algo cambie o la matera calle. Es lo pedido (no spamear), pero quien abra la app a las 3 a. m. ve el estado vigente solo si lo pide.
- **Una matera apagada y una matera sin Wi-Fi se ven igual** (`SIN_NOTICIAS`). Con Wi-Fi caído la OLED dice "Sin red" y con el aparato apagado no dice nada; el servidor no puede distinguirlos.
- **Sin correo.** Solo WebSocket (D157).
- **El `detalle` es técnico** (por ejemplo "linea en reposo ALTO") y no se debe mostrar a quien usa la matera.
- **Las lecturas pendientes** que el firmware guarda en RAM sin red no cuentan como "última lectura buena" para el "hace X" de la OLED: lo es la última que se **midió**, no la última que se **entregó**.
