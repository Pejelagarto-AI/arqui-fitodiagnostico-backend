# Fase 13: la mascota completa (lado servidor)

Cuaderno de implementación (como `docs/fase12/`). Pedido: el profesor aprobó la mascota como contexto de aplicación y pidió construirla completa; esta fase cierra lo que faltaba en el servidor para que la matera (fase 14, firmware) y el tablero del front la usen sin parches. Cuatro piezas, todas fuera de `dominio/` (`git diff --stat develop -- src/main/java/com/vivero/fitodiagnostico/dominio` da vacío) y sin que el núcleo (`dominio` y `aplicacion` del diagnóstico) conozca la mascota. El contrato es fijo porque el firmware se escribe en paralelo contra él.

## Qué hay

| Pieza | Dónde | Capa |
|---|---|---|
| A. La respuesta 201 de la lectura lleva la mascota | `adaptador/entrada/dispositivo/RespuestaLectura.java`, `LecturasDeDispositivoController.java`; `mascota/aplicacion/ServicioVistasDeMascota.java`, `ConsultarMascotaDeMateraPort`; `RepositorioEventosDeMascota.registradosDespuesDe` | entrada, mascota, salida |
| B. La matera acaricia | `adaptador/entrada/dispositivo/MascotaDeDispositivoController.java` (+ 2 DTO), `ActuarDesdeLaMateraPort`, `ServicioMascota.acariciar`, `ConfiguracionSeguridad` | entrada, mascota |
| C. Accesorio por planta | `V23` (h2 y postgresql), `mascota/modelo/Accesorio.java`, `EstadoDeMascota`, `MascotaEntity`, `CambiarAccesorioPort`, `ServicioMascota.cambiar`, `MascotaController` (`PATCH`), `PublicadorDeMascota.publicarActualizacion` | mascota, entrada, salida |
| D. El tablero en una llamada | `adaptador/entrada/rest/TableroDePlantas.java`, `RespuestaPlanta`, `ConsultarResumenDeMascotasPort`, `salud/ConsultarSaludDePlantasPort`, `RepositorioDeSalud.dePlantas`, `UltimosDiagnosticosEnLote`, `AdaptadorDiagnosticoJpa`, `AdaptadorSaludJpa`, `AdaptadorMascotaJpa.deLasPlantas` | entrada, mascota, salud, salida |

## Contrato

**A. `POST /api/v1/dispositivos/{serial}/lecturas` (201).** Igual que antes más una clave `mascota`, siempre presente:

```json
{
  "id": "...", "plantaId": "...", "origen": "DISPOSITIVO", "apodo": "Pepa", "diagnostico": { "...": "..." },
  "mascota": {
    "vitalidad": 64, "animo": "FELIZ", "racha": 3, "accesorio": "SOMBRERO",
    "eventos": [
      {"tipo": "REGADA", "ocurridoEn": "2026-11-16T14:40:05Z"},
      {"tipo": "ACARICIADA", "ocurridoEn": "2026-11-16T14:40:21Z"}
    ]
  }
}
```

`eventos` son los de la mascota con `registradoEn` posterior a la `ultimaLecturaEn` anterior de esa matera (la que guardaba el contexto `salud` antes de esta lectura), incluidos los que esta lectura acaba de producir; del más antiguo al más reciente, máximo 10. Sin lectura anterior (matera nueva o recién reclamada, D173): los de los últimos 60 s. Sin fila de mascota: `vitalidad` la inicial (80), `animo: "SIN_DATOS"`, `racha: 0`, `accesorio: "NINGUNO"`, `eventos: []`. Los tipos posibles son los de `TipoDeEvento`: `REGADA`, `SALIO_AL_SOL`, `DESMAYO`, `REVIVIO`, `ACARICIADA`, `RACHA`, `RIEGO_DECLARADO`, `RIEGO_CONFIRMADO`, `RIEGO_DESMENTIDO`.

**B. `POST /api/v1/dispositivos/{serial}/mascota/acciones`**, token de dispositivo (mismo alcance `lecturas:reportar`):

```json
// petición
{"tipo": "ACARICIAR"}
// 201 (caricia nueva) o 200 (llegó antes de 10 s de la anterior: id y ocurridoEn son los de la última)
{"id": "7c1e...", "tipo": "ACARICIADA", "ocurridoEn": "2026-11-16T14:41:02Z", "vitalidad": 64, "animo": "FELIZ", "repetida": false}
```

401 sin token o si la matera ya no está vinculada a la planta del token; 403 con token de persona, de otro dispositivo o sin el alcance; 400 `PARAMETRO_INVALIDO` con `LA_REGUE` (lo declara una persona), con otro valor o sin `tipo`. El evento se guarda con `detalle.origen = "MATERA"`; el aviso en vivo `EVENTO_DE_MASCOTA` lleva `datos.origen = "MATERA"` (las caricias del front, `"PERSONA"`).

**C. `PATCH /api/v1/plantas/{id}/mascota`**, token de persona:

```json
// petición
{"accesorio": "SOMBRERO"}
// 200: la misma vista que GET .../mascota, que ahora trae "accesorio"
{"plantaId": "...", "hayLecturas": true, "vitalidad": 64, "animo": "FELIZ", "racha": {"dias": 3, "desde": "..."}, "accesorio": "SOMBRERO", "...": "..."}
```

Valores `NINGUNO`, `SOMBRERO`, `MONO`, `GAFAS` (sin distinguir mayúsculas); otro valor o ausente, 400 `PARAMETRO_INVALIDO`. Solo el dueño: invitado 403 `OPERACION_NO_PERMITIDA`, ajeno o inexistente 404 `PLANTA_NO_ENCONTRADA`. Aviso por `/user/queue/plantas/{id}` a dueño e invitados:

```json
{"tipo": "MASCOTA_ACTUALIZADA", "mensaje": "«Pepa» cambió de accesorio.", "datos": {"plantaId": "...", "accesorio": "SOMBRERO"}, "detalle": {}}
```

**D. `GET /api/v1/plantas` y `GET /api/v1/plantas/compartidas`.** Cada elemento gana dos claves, `null` cuando no hay:

```json
{"id": "...", "apodo": "Pepa", "...": "...",
 "mascota": {"vitalidad": 64, "animo": "FELIZ", "racha": 3, "accesorio": "NINGUNO"},
 "salud":   {"estado": "FALLA", "mensaje": "no te siento la temperatura"}}
```

`salud` es la peor entre las materas de la planta (`FALLA`, luego `SIN_NOTICIAS`, luego `OK`), con la frase de D158 tal como la da el contexto `salud`.

## Decisiones

**D180. La lectura responde con la mascota, y "desde la lectura anterior" se mide por `registrado_en`.** La OLED no tiene MQTT en la demo (D142 lo dejó para quien lo encienda); por HTTP se entera en cada lectura, que ya es el latido. `LecturasDeDispositivoController` pregunta al contexto `salud` la `ultimaLecturaEn` de esa matera ANTES de registrar la lectura, registra, anota la lectura en `salud` y entonces pide `ConsultarMascotaDeMateraPort.paraLaMatera(planta, anterior)`. Garantía de orden, sin tocar nada asíncrono: la mascota se alimenta en un `afterCommit` síncrono de la transacción de `registrar` (D140), que corre antes de que `registrar` devuelva el control; cuando el controlador consulta, los eventos de esta lectura ya existen (lo prueba `elEventoQueProdujoLaMismaLecturaYaVieneEnSuRespuesta`: la REGADA llega en el 201 de la lectura que la confirma, y no se repite en la siguiente). Se filtra por `registrado_en` (la hora en que el servidor anotó el evento) y no por `ocurrido_en`: una REGADA ocurre a la hora de la lectura que mostró el salto, y una REGADA anotada ahora con `ocurrido_en` de hace dos minutos se perdería si la matera filtrara por la hora del hecho. El umbral es estricto (`>`): un evento anotado en el mismo instante en que quedó la lectura anterior ya salió en esa. Con más de 10 eventos nuevos salen los 10 más recientes, ordenados del más antiguo al más reciente (la OLED anima en ese orden); los más viejos se pierden para la matera, no para la línea de tiempo. Si la consulta de la mascota falla, el 201 sale igual con una mascota sin noticias y el error queda en el log: la lectura ya quedó guardada y un 500 haría que el chip la reenviara. Para servir el filtro se agrega el índice `ix_evento_mascota_registro (planta_id, registrado_en DESC)` en V23 (el de V19 ordena por `ocurrido_en`). Descartado: devolver `ocurridoEn >` anterior (pierde las REGADA tardías); guardar en la matera "el último evento visto" (otro estado en el chip para algo que el servidor ya sabe); una consulta aparte `GET .../mascota/eventos` cada lectura (una llamada más cada 5 s por matera).

**D181. La caricia desde la matera: misma regla que la de la persona, otro origen.** Una ruta nueva bajo `/dispositivos/{serial}`, con el mismo token, alcance y comprobaciones que las lecturas y la salud (alcance, `sub` igual al serial, vínculo vigente con la planta del token: 401 si ya no). `ConfiguracionSeguridad` suma `POST /api/v1/dispositivos/*/mascota/acciones` al único matcher que acepta token de dispositivo; en el resto de rutas ese token sigue dando 403. El caso de uso es `ActuarDesdeLaMateraPort.acariciar(planta, serial)`, que comparte con `actuar(...ACARICIAR)` el mismo método privado: el enfriamiento de 10 s es de la PLANTA (D172), así que una caricia del front y otra de la matera dentro de 10 s son "la misma" y la segunda responde 200 con los datos de la primera. El evento guarda `detalle.origen` (`MATERA` o `PERSONA`; el detalle es JSON libre, sin migración) y el aviso en vivo lo copia a `datos.origen`. No se publica por MQTT a la matera que la originó: `registrarYPublicar` recibe los seriales a excluir y los quita del aviso (las otras materas de la misma planta sí lo reciben). Si la planta no tiene fila de mascota, la caricia funciona igual (D172 ya lo permitía) y no crea la fila. Descartado: llamar a `actuar` con un usuario ficticio "matera" (rompería `AutorizadorDePlantas` y mentiría sobre quién actúa); dejar que `LA_REGUE` entre por esta ruta (declarar un riego es una afirmación de una persona, D138); un `origen` en una columna de `evento_mascota` (cambia V17 para un dato que solo se muestra).

**D182. El accesorio es una columna de `mascota`, no un evento.** V23 agrega `accesorio VARCHAR(16) NOT NULL DEFAULT 'NINGUNO'` con `CHECK (accesorio IN ('NINGUNO','SOMBRERO','MONO','GAFAS'))` a la tabla que ya existe (en `h2/` y `postgresql/`, mismo texto salvo el orden de `NOT NULL DEFAULT`). `EstadoDeMascota` gana el componente `accesorio` (nulo se lee `NINGUNO`) y conserva un constructor de la aridad anterior para no reescribir las pruebas viejas; las tres copias de `ReglasDeVitalidad` lo arrastran, así que ni una lectura ni el tic lo borran (lo prueba `unaLecturaNuevaNoBorraElAccesorio` y la integración). `ServicioMascota.cambiar` autoriza con `paraModificar` (dueño; invitado 403, ajeno 404) y toma la fila con `paraActualizar` (D147), de modo que no se pisa con una lectura a la vez. Sin fila (planta sin lecturas) la crea con `EstadoDeMascota.nueva`, es decir con la vitalidad inicial, igual que la crea la primera lectura (que después la encuentra y sigue desde ahí); lo que no se hace es inventar un `ultimoEstado` ni una lectura. Poner el accesorio que ya tiene no escribe ni avisa (idempotente). No entra en la línea de tiempo: no es algo que le pase a la mascota sino algo que el dueño decide, y el tipo de evento sería otro valor en el `CHECK` de V17. Descartado: tabla `accesorio_mascota` aparte (una fila más por mascota para un valor de 16 caracteres, y JOIN en cada consulta); guardarlo en `planta` (es del `dominio/`, que esta fase no toca, y la mascota no debe empujar columnas al agregado de la planta); dejar el PATCH devolver 204 (el front necesita la vista para refrescar la tarjeta sin otra llamada).

**D183. Dónde viven los puertos nuevos, para que `ArquitecturaTest` siga sin excepciones.** Las dos reglas que mandan: un adaptador no depende de nada que viva en `..aplicacion..` (incluida `mascota.aplicacion`), y el núcleo no nombra `mascota`. Por eso: (1) los controladores hablan con cuatro puertos de entrada nuevos de `mascota.puerto.entrada` (`ConsultarMascotaDeMateraPort`, `ConsultarResumenDeMascotasPort`, `ActuarDesdeLaMateraPort`, `CambiarAccesorioPort`) y uno de `salud` (`ConsultarSaludDePlantasPort`); (2) las implementa `ServicioVistasDeMascota` (lectura, sin autorización de personas), `ServicioMascota` (escritura) y `ServicioSalud`; (3) lo que necesita SQL nuevo (último diagnóstico por lote, saludes por lote, mascotas por lote, eventos por registro) son puertos de SALIDA del propio contexto (`UltimosDiagnosticosEnLote`, `RepositorioDeSalud.dePlantas`, `RepositorioMascotas.deLasPlantas`, `RepositorioEventosDeMascota.registradosDespuesDe`) que implementan los adaptadores JPA. `RepositorioDiagnosticos`, `RepositorioDispositivos` y los demás puertos del `dominio/` no se tocan: `AdaptadorDiagnosticoJpa` simplemente implementa también `UltimosDiagnosticosEnLote`. La composición de la mascota con la salud (el tablero) la hace el adaptador REST, `TableroDePlantas`, que solo conoce puertos. Se separó `ServicioVistasDeMascota` de `ServicioConsultaDeMascota` (la consulta del dueño) porque estas dos vistas no autorizan personas (el vínculo del dispositivo y el listado ya filtraron) y no conviene que el servicio con autorización tenga un método que se salte `paraLeer`. Descartado: agregar métodos por lote a `RepositorioDiagnosticos`/`RepositorioDispositivos` (son del dominio); un servicio de aplicación del tablero que junte mascota y salud (el núcleo no puede nombrar `mascota`, y `mascota` no debe nombrar `salud` solo por esto); `@Transactional` en el controlador.

**D184. `MASCOTA_ACTUALIZADA` es un aviso aparte, y `origen` solo viaja donde existe.** Cambiar el accesorio avisa por el mismo canal en vivo (`/user/queue/plantas/{id}`) pero con `tipo: "MASCOTA_ACTUALIZADA"` y `datos.accesorio`, para que otra pestaña se actualice; no es `EVENTO_DE_MASCOTA` porque no hay evento (ni `eventoId` ni `ocurridoEn` que un cliente espere). `PublicadorDeMascota` gana un método `publicarActualizacion` con implementación vacía por defecto: lo implementan el canal WebSocket y el decorador de tras-commit (que lo entrega después del commit, igual que los eventos, para que un PATCH revertido no avise); el canal MQTT no lo implementa a propósito, porque la matera se entera por su siguiente lectura (A) y el firmware no suscribe a MQTT en la demo. `datos.origen` se copia del `detalle` del evento solo cuando el evento lo trae (hoy, `ACARICIADA`); no se inventa un `"SISTEMA"` para REGADA o RACHA, porque sería una afirmación que el servidor no guardó. Descartado: reusar `EVENTO_DE_MASCOTA` con un `evento: "ACCESORIO"` (un valor de `TipoDeEvento` nuevo, otro `CHECK`, y que el front lo muestre en la línea de tiempo); `origen` obligatorio en todos los avisos (un cambio para los clientes que ya leen `datos` sin necesidad).

**D185. El tablero se arma por lote: constante en consultas, no una por tarjeta.** `GET /plantas` y `.../compartidas` llaman a `TableroDePlantas.armar`, que pide los ids de las plantas ya autorizadas y hace: una consulta de mascotas (`findAllById`), una del último diagnóstico por planta (subconsulta `max(registrado_en)` sobre `ix_diagnostico_historial`, con las colecciones del diagnóstico cargadas en lote por el `SUBSELECT` de la fase 6) para calcular el ánimo, y una de saludes (`DispositivoSaludEntity` unida a `DispositivoEntity` por serial con `planta_id in (...)`). El ánimo se calcula igual que en `GET .../mascota`: con `MapaDeAnimo` sobre el último diagnóstico. La peor salud se elige en `ServicioSalud.laPeorDeLasPlantas` (`FALLA` < `SIN_NOTICIAS` < `OK`; a igual gravedad, el serial menor, para que la respuesta sea estable) y la frase sale de `SaludDelDispositivo.mensaje` con el reloj del servidor, igual que en `GET .../dispositivos`. Una planta sin fila de mascota o sin ninguna matera que haya dejado noticia da `null`; una planta con matera sin ninguna noticia también. Medición (`elNumeroDeSentenciasDelListadoNoCreceConLaCantidadDePlantas`, H2 con Flyway, estadísticas de Hibernate): el listado de 1 planta con mascota y salud ejecuta 7 sentencias y el de 7 plantas, cada una con matera, lectura y mascota, también 7; si alguien reintroduce un N+1 la prueba falla. Las otras respuestas que devuelven una planta (`POST`, `GET /{id}`, `PUT`, aceptar invitación) traen las dos claves con valor `null`: no arman tablero porque no hay lista; el front que quiera la mascota de una planta sigue usando `GET .../mascota`. Descartado: un endpoint nuevo `GET /plantas/tablero` (el front ya llama a `GET /plantas`; la fase 12 quitó un N+1 de ese listado y no se vuelve a agregar otro al otro lado); que el controlador llame a `ConsultarMascotaPort.consultar` por planta (justo el N+1 que se quiere evitar, con autorización repetida); poner `mascota` y `salud` dentro de `Planta` (dominio).

## Pruebas

- **Mascota, sin Spring:** `ServicioMascotaDeMateraTest` (14): sin fila la vista es la inicial; solo salen los eventos registrados después de la lectura anterior (y la frontera es estricta); máximo 10 del más antiguo al más reciente quedándose con los últimos; sin lectura anterior, los últimos 60 s; la caricia de la matera guarda `origen: MATERA`, no se le avisa a esa matera y sí a las personas; la de la persona guarda `PERSONA` y sí llega a la matera; el enfriamiento es de la planta (caricia del front y luego de la matera: `repetida`); caricia de una planta borrada, 404; el accesorio crea la fila con la vitalidad inicial, avisa sin crear evento, no repite si no cambia, es solo del dueño (403 al invitado, 404 al ajeno) y una lectura posterior no lo borra; el resumen por lote solo trae las plantas con mascota.
- **Salud:** `ServicioSaludTest` (2 nuevas): la peor salud gana `FALLA` sobre `SIN_NOTICIAS` sobre `OK`, una planta sin materas no aparece; con dos materas sin falla gana la que calló.
- **Canal en vivo:** `PublicadorDeMascotaPorWebSocketTest` (3 nuevas): `datos.origen` en la caricia y ausente en los demás; `MASCOTA_ACTUALIZADA` por persona; la actualización espera al commit y un canal roto no frena al otro.
- **Integración (Spring + seguridad + Flyway H2, `MascotaCompletaIntegracionTest`, 17):** A (el 201 trae `mascota`; la REGADA que produce la propia lectura viene en esa respuesta y no se repite; la caricia del front llega una sola vez), B (201, 200 `repetida`, origen guardado, aviso en vivo con origen y sin la matera originante, 401 sin token y con matera desvinculada, 403 con persona y con otro dispositivo, 400 con `LA_REGUE`, `{}` y un tipo raro), C (200 con `accesorio`, crea la fila con vitalidad 80, 403 al invitado, 404 al ajeno, 400 con `CORONA` y con `{}`, el accesorio sale en la siguiente lectura y no la borran las lecturas, aviso `MASCOTA_ACTUALIZADA`), D (una planta con mascota y salud y otra sin nada: `null`; la peor salud entre dos materas; las compartidas; y que las sentencias no crecen con las plantas).
- **Arquitectura:** `ArquitecturaTest` sin reglas ni excepciones nuevas.
- Las pruebas web que construyen `PlantasController` e `InvitacionesController` (`PlantasControllerTest`, `InvitacionesControllerTest`, `SeguridadCompartirYAvisosWebTest`, `TipoDeContenidoWebTest`) agregan dos `@MockBean` de los puertos nuevos. `MascotaEnMemoria` y `SaludEnMemoria` (dobles) implementan los métodos nuevos de los puertos.
- Suite completa (`MAVEN_OPTS=-Xmx512m ./mvnw -q test`): 1084 pruebas, 0 fallas, 0 errores, 0 omitidas.

## Límites

- **Más de 10 eventos entre dos lecturas:** la matera ve solo los 10 más recientes. Con lecturas cada 5 s es inalcanzable; tras una caída larga de red se pierden las animaciones viejas, no los eventos.
- **Dos relojes del servidor:** la ventana "desde la lectura anterior" compara `registrado_en` del evento con la `ultima_lectura_en` de `salud`, ambas con el reloj del servidor; con varias réplicas del backend sin reloj sincronizado un evento podría salir dos veces o ninguna.
- **Matera sin lecturas pero viva (salud en FALLA, D156):** no manda lecturas y por lo tanto no recibe eventos hasta que se recupere; al volver, la ventana abarca todo lo que pasó (con el tope de 10).
- **Carrera inofensiva al crear la fila:** si el dueño fija el accesorio de una planta sin fila a la vez que llega su primera lectura, una de las dos transacciones puede chocar con la clave primaria y fallar (la lectura de la mascota se registra en el log y la lectura ya quedó; el PATCH responde 500 y se reintenta). No hay bloqueo posible sobre una fila que no existe (mismo límite que D172).
- **El tablero calcula el ánimo con el último diagnóstico, no con la vitalidad extrapolada** (igual que `GET .../mascota`, D136): la tarea periódica la mantiene al día con un minuto de retraso.
- **Un empate en `registrado_en` entre dos diagnósticos de una planta** se resuelve por id en el lote y por `registrado_en, id` en `ultimoDe`: pueden elegir distinto en ese caso rarísimo y el ánimo mostrado variar una lectura.
- **El `PATCH` con la planta recién borrada:** 404 como el resto.
- **MQTT:** el canal `fito/dispositivos/{serial}/mascota` no cambia ni publica `MASCOTA_ACTUALIZADA`; es solo para los eventos.
- **`GET /plantas/{id}` y las demás respuestas de planta** traen `mascota: null` y `salud: null` aunque la planta tenga (D185). Si el front las necesita ahí, es un cambio chico en esos controladores.

## Qué cambia para el front y el firmware

**Front**
- `GET /plantas` y `GET /plantas/compartidas`: leer `mascota` y `salud` de cada planta y dejar de pedir `GET .../mascota` y `GET .../dispositivos` por tarjeta. `null` = "sin datos" (tarjeta sin mascota o sin chip de salud). Las otras respuestas de planta traen las dos claves en `null`.
- `GET /plantas/{id}/mascota` trae `accesorio`. Nuevo `PATCH` para cambiarlo (200 con la vista; 403 al invitado: ocultar el selector).
- Aviso nuevo `tipo: "MASCOTA_ACTUALIZADA"` (`datos.accesorio`) por `/user/queue/plantas/{id}`: actualizar el accesorio sin recargar. No es un evento: no entra a la línea de tiempo.
- `EVENTO_DE_MASCOTA` de una caricia trae `datos.origen` (`MATERA` o `PERSONA`): se puede animar distinto "la acarició la matera". El front que ya acariciaba no cambia.

**Firmware (fase 14)**
- Leer `mascota` de cada 201 de `POST .../lecturas`: `vitalidad`, `animo`, `racha`, `accesorio` para dibujar, y `eventos` (en orden, del más antiguo al más reciente) para animarlos una vez cada uno; la matera no necesita recordar cuáles ya vio (el servidor filtra por su lectura anterior). Los tipos son los de `TipoDeEvento` (arriba); ignorar los que no conozca.
- Acariciar con el botón: `POST .../mascota/acciones {"tipo":"ACARICIAR"}` con su token. `201` o `200` son éxito; `repetida: true` no suma animación. No debe esperar el mensaje MQTT de esa caricia: no llega.
- Un 401 aquí significa lo mismo que en las lecturas: ya no está vinculada, pedir credencial de nuevo.
