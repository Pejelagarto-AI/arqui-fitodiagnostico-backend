# Fase 3: vinculación de dispositivos "a modo Chromecast"

Qué se construyó, dónde vive cada pieza y por qué, y qué decisiones se tomaron con la alternativa descartada. Es el cuaderno de implementación (como `docs/fase2/IMPLEMENTACION.md`); **no es el documento de arquitectura de la entrega**.

**Origen.** La fase 2 obligaba al dueño a pedir un JWT al back, copiarlo del front y pegarlo en el ESP32. El profesor lo calificó como un proceso de vinculación que no es de producto. **Objetivo:** que el humano nunca toque una credencial. El contrato compartido con el front está en el spec (`SPEC-FASE3-RECLAMO-DISPOSITIVO.md`, en la carpeta de la entrega); este archivo documenta cómo quedó implementado en el back.

Commits de la fase (rama `feature/fase3-reclamo-dispositivo`):

| Commit | Qué hace |
|---|---|
| `b7d3d54` | `feat(dominio)`: entidad `Dispositivo`, `Reto`, cinco excepciones, puertos nuevos, `TokenDeDispositivo` con `serial` |
| `c1f2bad` | `feat(dispositivos)`: repositorio JPA y migración V12 de la tabla `dispositivo` |
| `ec92a63` | `feat(dispositivos)`: fábrica (admin) y reclamo por el dueño; se borra el flujo de la fase 2 |
| `746699b` | `feat(dispositivos)`: reto HMAC y credencial para el dispositivo; el token baja a `PT1H` |
| `7a7b436` | `feat(simulador)`: autenticación por serial y secreto con NVS simulada, y `fabrica.py` |

## 1. El flujo

Cada dispositivo sale de "fábrica" con un **serial** impreso y un **secreto** guardado adentro. El dueño escribe el serial en su planta (**reclamo**). El dispositivo, por su cuenta, demuestra al servidor que conoce el secreto (**reto HMAC**) y recibe su token de dispositivo, corto y renovable.

```
FÁBRICA (admin)          DUEÑO (front)                 DISPOSITIVO (ESP32 / simulador)
───────────────          ─────────────                 ───────────────────────────────
POST /admin/dispositivos                               arranca con {serial, secreto} en NVS
→ {serial, secreto}                                    POST /dispositivos/{serial}/reto → {reto}
  (sticker)                                            POST /dispositivos/{serial}/credencial
                                                         {respuesta: HMAC(secreto, reto)}
                                                       → 409 DISPOSITIVO_SIN_RECLAMAR
                                                         ... espera y reintenta cada 5 s ...
                         POST /plantas/{id}/dispositivos
                           {serial}  → 201
                                                       → 200 {token, plantaId, expira}
                                                       guarda token en NVS
                                                       POST /dispositivos/{serial}/lecturas (como hoy)
                                                       al 401 o a <2 min de expirar: reto + credencial
                         GET /plantas/{id}/dispositivos
                         → [{serial, reclamadoEn, ultimaCredencialEn}]
```

## 2. Contrato (prefijo `/api/v1`, errores `{error, mensaje, detalle}`)

| Quién | Ruta | Resultado |
|---|---|---|
| Admin | `POST /admin/dispositivos` `{serial?}` | 201 `{serial, secreto, creadoEn}` (el secreto solo aquí). Sin serial, el servidor genera `fito-XXXXXX` (alfabeto sin 0/O/1/I). 409 `DISPOSITIVO_YA_EXISTE`. 400 si el serial tiene mal formato. |
| Admin | `GET /admin/dispositivos` | 200 `[{serial, estado: SIN_RECLAMAR\|RECLAMADO, plantaId, reclamadoEn, ultimaCredencialEn, creadoEn}]` (nunca el secreto; los campos que no aplican van `null`) |
| Dueño | `POST /plantas/{id}/dispositivos` `{serial}` | **201** `{serial, plantaId, reclamadoEn}`; reclamar de nuevo el mismo serial para la misma planta también responde **201** (idempotente, conserva `reclamadoEn` del primer reclamo). 404 `DISPOSITIVO_NO_ENCONTRADO`, 409 `DISPOSITIVO_YA_RECLAMADO` (de otra planta). Invitado 403, ajeno 404. |
| Dueño | `GET /plantas/{id}/dispositivos` | 200 `[{serial, reclamadoEn, ultimaCredencialEn}]` (`ultimaCredencialEn: null` = "esperando que se conecte") |
| Dueño | `DELETE /plantas/{id}/dispositivos/{serial}` | 204. 404 `DISPOSITIVO_NO_ENCONTRADO` si no está en esa planta. |
| Chip | `POST /dispositivos/{serial}/reto` (público, sin cuerpo) | 200 `{reto, expira}`. 32 bytes aleatorios en base64 estándar (44 caracteres), vale 60 s, un solo uso; pedir otro reemplaza al anterior. 404 si el serial no existe. |
| Chip | `POST /dispositivos/{serial}/credencial` `{respuesta}` (público) | 200 `{token, plantaId, expira}`. 409 `DISPOSITIVO_SIN_RECLAMAR`; 401 `CREDENCIAL_RECHAZADA` (sin reto vigente, vencido, reusado o respuesta que no coincide); 404 si el serial no existe. El reto se consume en todos los casos. |
| Chip | `POST /dispositivos/{serial}/lecturas` | sin cambios (`sub` del token = serial) |

**HMAC.** `respuesta = base64( HMAC-SHA256( clave = bytes UTF-8 del string secreto, mensaje = bytes UTF-8 del string reto ) )`. El secreto es un string base64url de 32 bytes (43 caracteres); se usa el **string** como clave y el **string** del reto como mensaje, sin decodificar nada. Vector de referencia comprobable con `echo -n mensaje | openssl dgst -sha256 -hmac clave -binary | base64`, que da `F+iEeoRjaIivWnq+CRDlCg+ijW8dNZGhdebgH8+AtBI=`.

**Token de dispositivo.** Mismo JWT de la fase 2 (`tipo=dispositivo`, `scope=lecturas:reportar`, `sub=serial`, `planta`). La duración por defecto baja de `P365D` a `PT1H` (`FITO_JWT_DISPOSITIVO_EXPIRACION`).

## 3. Impacto en el dominio

A diferencia de la fase 2 (donde agregar el canal no tocó el dominio), aquí el dominio **sí cambia, y es lo correcto**: hay una regla de negocio nueva (un dispositivo es una entidad con ciclo de vida: nace sin planta, se reclama, se desvincula), no un canal nuevo.

| Pieza | Qué cambió |
|---|---|
| `dominio.modelo.Dispositivo` (nuevo) | Entidad mutable con identidad (el serial). Reglas en sus métodos: `reclamar` (idempotente para la misma planta, falla si es de otra), `desvincular` (borra planta, última credencial y reto), `asignarReto`, `consumirReto` (devuelve el reto solo si estaba vigente y **siempre** lo borra), `registrarCredencial`. El `secreto` es un texto opaco: el dominio no sabe que es una clave HMAC. El `toString` no lo muestra. |
| `dominio.modelo.Reto` (nuevo) | Valor y caducidad. |
| `dominio.excepcion` (5 nuevas) | `DispositivoNoEncontradoException` 404, `DispositivoYaReclamadoException` 409, `DispositivoYaExisteException` 409, `DispositivoSinReclamarException` 409, `CredencialRechazadaException` 401. |
| `TokenDeDispositivo` | `dispositivoId` pasa a `serial` (mismo formato). |
| Puertos de entrada | `RegistrarDispositivoDeFabricaPort`, `ReclamarDispositivoPort`, `AutenticarDispositivoPort`. **Reemplazan** a `VincularDispositivoPort`. Los puertos de entrada pasan de 22 a 24 (+3, -1). |
| Puertos de salida | `RepositorioDispositivos` y `AutenticadorDeDispositivos`. `EmisorTokenDispositivoPort` queda igual. |
| `aplicacion.ServicioDispositivos` | Reescrito: implementa los tres puertos. Un solo servicio porque comparten el repositorio y la regla "un dispositivo tiene un único estado"; cada controlador ve solo su puerto (ISP, D64). |
| `aplicacion.CascadaDeEliminacion` | **Fuera de lo descrito por el spec, pero necesario:** la tabla `dispositivo` tiene clave foránea a `planta` sin `ON DELETE CASCADE` (D37), así que eliminar una planta con dispositivos reclamados fallaba. La cascada ahora los **desvincula** (no los borra: el chip existe y otra planta puede reclamarlo). Cubierto por prueba de aplicación y de integración. |

| Adaptador | Pieza |
|---|---|
| `adaptador.entrada.rest` | `AdminDispositivosController`, `DispositivosDePlantaController` (reemplaza a `VinculoDeDispositivosController`), DTOs `Respuesta{DispositivoDeFabrica, DispositivoAdmin, ReclamoDeDispositivo, DispositivoDePlanta}` y `Solicitud{Dispositivo, DispositivoDeFabrica}`. |
| `adaptador.entrada.dispositivo` | `AutenticacionDeDispositivoController` y sus DTO propios (D73: no se comparten DTO entre adaptadores de entrada). |
| `adaptador.salida.jpa` | `DispositivoEntity`, `DispositivoJpaRepository`, `AdaptadorDispositivoJpa`; migración `V12__dispositivo.sql` en `h2` y `postgresql`. |
| `adaptador.salida.seguridad` | `AutenticadorHmacSha256`. |
| `config.ConfiguracionSeguridad` | Dos rutas públicas nuevas; `/api/v1/admin/dispositivos` ya caía en `/admin/**` (solo ADMINISTRADOR). |

Las 26 reglas de `ArquitecturaTest` siguen verdes, incluida `los_adaptadores_de_entrada_no_se_conocen_entre_si`. El dominio sigue sin importar Spring, JPA ni HTTP.

## 4. Decisiones (D74 en adelante)

**D74. Reto HMAC y no mandar el secreto por la red.** El chip nunca envía su secreto: recibe un reto aleatorio y devuelve una prueba de que lo conoce. Quien escuche el tráfico de una red doméstica sin TLS ve retos y respuestas que no sirven de nuevo (cada reto vale 60 s y **un solo intento**, acierte o no). Detalles que importan: (1) el reto se **gasta antes de decidir**, y ese gasto se conserva aunque la respuesta sea un rechazo, por eso `ServicioDispositivos.credencial` declara `noRollbackFor` para `CredencialRechazadaException` y `DispositivoSinReclamarException` (sin eso, la transacción se desharía junto con el consumo y quien adivina podría reintentar contra el mismo reto); (2) la comparación es en tiempo constante (`MessageDigest.isEqual`); (3) la clave del HMAC es el **string** del secreto y el mensaje el **string** del reto, para que en el ESP32 sea un `mbedtls_md_hmac` sobre dos cadenas ASCII sin decodificar nada. Descartado: que el chip mande el secreto en cada petición (cualquiera en la red lo copia para siempre).

**D75. El secreto se guarda en claro.** El HMAC exige la clave en ambos lados, así que el servidor no puede guardar solo un hash. Una fuga de la base expone secretos que **solo sirven para pedir credencial de reportar lecturas de una planta**, y el dueño los revoca desvinculando. Descartado: guardar un hash del secreto y que el chip lo mande en claro (sin TLS es peor, ver D74). Evolución natural si hace falta: cifrar la columna con una clave de la aplicación.

**D76. El token de dispositivo dura `PT1H`.** Ahora el chip renueva solo, así que ya no hace falta un token de un año. Esto **cierra el límite de la D70** ("sin revocación individual"): desvincular impide renovar y el token vivo muere en como máximo una hora. No hay lista de revocación ni estado extra. Límite aceptado: durante esa hora el token sigue sirviendo para reportar lecturas (el 401 de una lectura no llega hasta que expira). Es configurable con `FITO_JWT_DISPOSITIVO_EXPIRACION`.

**D77. La fábrica es un endpoint de administrador.** Era la única forma de darle serial y secreto a un dispositivo sin un canal manual aparte (script con acceso a la base, semilla en el arranque). Reusa la autenticación y el rol que ya existen (`/admin/**`), queda auditable y se puede llamar desde el front y desde `simulador/fabrica.py`. Descartado: una semilla de seriales en el código o la base (obligaría a tocar la base para fabricar un dispositivo nuevo); un CLI con acceso directo a la base (un camino de escritura más fuera de los puertos).

**D78. El reclamo es idempotente y la respuesta es 201 siempre.** Reclamar otra vez el mismo serial para la misma planta no cambia nada (conserva la fecha del primer reclamo) y responde 201, no 200: el front no tiene que distinguir dos casos de éxito y un doble clic no falla. Un serial de **otra** planta es 409 `DISPOSITIVO_YA_RECLAMADO`: no se "roba" un dispositivo, el primer dueño tiene que desvincularlo.

**D79. Reto y credencial son rutas públicas y abiertas.** `permitAll`, no `SOLO_NO_DISPOSITIVO` como las demás públicas (D72): un chip que renueva "cerca de expirar" aún tiene su token vigente y podría mandarlo; rechazarlo con 403 en su propia ruta sería absurdo. Lo que protege la ruta es el secreto, no la cabecera.

**D80. Desvincular y eliminar la planta limpian el estado de autenticación.** `desvincular()` borra también `ultimaCredencialEn` y el reto pendiente (un dispositivo desvinculado vuelve a "esperando que se conecte" y no puede completar un reto viejo). Eliminar una planta desvincula sus dispositivos en la cascada (ver el impacto en `CascadaDeEliminacion`).

**D81. Detalles de formato.** El reto se entrega en **base64 estándar con relleno** (lo fija el contrato) aunque el generador de valores aleatorios existente (`GeneradorDeTokensSeguro`, reusado a propósito para el secreto, el reto y el serial) produce base64url sin relleno: el servicio lo traduce símbolo a símbolo (`-`→`+`, `_`→`/`, más `=`), sin un puerto nuevo; el chip trata el reto como un texto opaco de todos modos. Los instantes que el servicio guarda y devuelve se truncan a **microsegundos**, la precisión de la columna, para que lo que responde el reclamo sea idéntico a lo que lee el listado. `POST .../credencial` con un serial que no existe responde 404 (el spec solo lo definía para `reto`).

**D82. El simulador encola en la NVS las lecturas que no pudo entregar y las vacía en orden al volver la red.** La cola vive en la misma NVS que el token (clave `pendientes`) porque es lo único que el chip real conserva al reiniciarse: una cola en memoria se perdería justo en el caso que importa (se fue la luz con la red caída). Cada lectura guarda su `marcaDeTiempo` original, así que al entregarla tarde el servidor la registra con su `tomadaEn` verdadero y su `registradoEn` de llegada: es exactamente lo que previó D67, y el diagnóstico vigente sigue siendo el último en llegar, no el que tenga la marca más vieja o más nueva. Al llenarse el tope (`--max-pendientes`, 500, como una flash chica) se descarta la **más vieja**: para diagnosticar una planta importa más el estado reciente que el de hace horas, y una lectura vieja ya no cambia la decisión. Un `400` no se reencola porque no es falta de red: el servidor entendió y rechazó el dato, reenviarlo daría lo mismo para siempre y bloquearía la cola; igual `403` y `404`. Los `5xx` sí se tratan como red caída (el servidor no pudo, el dato quizá es bueno). Si el dueño desvincula mientras no había red, la renovación da `409`, la cola se conserva y se espera el reclamo. Descartado: reordenar o fusionar lecturas en el cliente (el servidor ya es quien decide el vigente) y mandar un lote (no hay endpoint por lote; una a una conserva los códigos por lectura). Límite: el simulador no consolida duplicados si se corta justo entre la respuesta y el borrado de la NVS (a lo más una lectura repetida).

## 5. Límites conocidos (decirlos antes de que los encuentren)

- **Enumeración de seriales.** `POST /dispositivos/{serial}/reto` es público y responde 404 si el serial no existe, así que alguien puede averiguar qué seriales hay. El espacio del serial generado es 32^6 (~10^9) y el reto no da nada sin el secreto, pero no hay límite de intentos (rate limiting no está en el alcance).
- **El 409 se decide antes de verificar la respuesta.** `credencial` responde `DISPOSITIVO_SIN_RECLAMAR` a quien conoce el serial aunque no pruebe el secreto (así lo fija el spec). Revela solo "este serial está o no reclamado".
- **Sin bloqueo de fila al consumir el reto.** Dos `credencial` simultáneas sobre el mismo reto podrían leerlo ambas antes de que alguna lo borre y emitir dos tokens. No es un bypass (las dos presentan una respuesta correcta, o sea, conocen el secreto), solo un token de más.
- **El token vivo sobrevive a desvincular hasta una hora** (D76).
- El secreto en claro en la base (D75).
- `VistaEnVivoIntegracionTest` sigue siendo intermitente (heredado de la fase 2, no se tocó).

## 6. Cómo se prueba a mano

```bash
# 1. back
export FITO_JWT_SECRETO="$(openssl rand -base64 48)"
FITO_ADMIN_CORREO=admin@fito.local FITO_ADMIN_CONTRASENA=admin12345 ./mvnw spring-boot:run

# 2. un usuario y su planta (ver el README: registro, sesión, POST /plantas), y la fábrica
FITO_ADMIN_CORREO=admin@fito.local FITO_ADMIN_CONTRASENA=admin12345 python3 simulador/fabrica.py
#    imprime el sticker: serial fito-XXXXXX, secreto y el comando del simulador

# 3. el simulador ANTES de reclamar: imprime "esperando reclamo del serial fito-XXXXXX... (reintento en 5 s)"
FITO_SERIAL=fito-XXXXXX FITO_SECRETO=... python3 simulador/simulador_esp32.py --intervalo 2

# 4. el dueño reclama el serial; el simulador despierta solo
curl -X POST $B/plantas/$PLANTA/dispositivos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"serial":"fito-XXXXXX"}'
#    el simulador imprime "credencial obtenida..." y empieza a mandar lecturas (201)
curl $B/plantas/$PLANTA/dispositivos -H "Authorization: Bearer $TOKEN"   # ultimaCredencialEn con fecha

# 5. desvincular: 204; al renovar, el simulador vuelve a "esperando reclamo"
curl -X DELETE $B/plantas/$PLANTA/dispositivos/fito-XXXXXX -H "Authorization: Bearer $TOKEN"
```

Se corrió así con el jar empaquetado (`./mvnw -q -DskipTests package` y `java -jar`): el simulador arrancó antes del reclamo y esperó; al reclamar obtuvo credencial y mandó lecturas con `201`; `GET /plantas/{id}/dispositivos` pasó de `ultimaCredencialEn: null` a una fecha; tras `DELETE` (204) y con el token cerca de expirar, el simulador volvió a esperar; un token manipulado en la NVS dio `401`, se descartó y se pidió otro.

## 7. Pruebas

`./mvnw test`: **787 pruebas** (772 tras la tercera etapa, 718 en la fase 2 según su documento), 0 fallos. ArchUnit sigue en 26 reglas.

| Capa | Qué se agregó |
|---|---|
| Dominio (sin Spring) | `DispositivoTest` (nacimiento, reclamo idempotente, ya reclamado, desvincular, reto de un solo uso, vencido, reemplazado, credencial) |
| Aplicación (dobles en memoria) | `ServicioDispositivosTest` (fábrica, solo el dueño, reclamo, 409/401 de la credencial, reto reusado/vencido/reemplazado, desvincular corta la renovación, cascada); dobles `DispositivosEnMemoria` y `AutenticadorDeDispositivosFalso` |
| Adaptadores | `AdaptadorDispositivoJpaTest` (H2 con Flyway real, V12), `AutenticadorHmacSha256Test` (con vector de openssl) |
| Contexto real | `ReclamoDeDispositivoIntegracionTest` (fábrica y reclamo: 201/404/409/403/401/400, listar, desvincular, cascada de la planta) y `AutenticacionDeDispositivoIntegracionTest` (reto + credencial feliz con HMAC calculado aparte, 409 sin reclamar, 401 por respuesta mala, reto expirado o reusado, desvincular, el token sirve para lecturas y no para endpoints de usuario) |
| Ajustadas | `TokenDeDispositivoIntegracionTest` y `LecturasDeDispositivoIntegracionTest` obtienen el token por el puerto `EmisorTokenDispositivoPort` (lo que prueban es el alcance y el endpoint, no la obtención); `MundoEnMemoria` y `CascadaDeEliminacion` reciben el repositorio de dispositivos |

## 8. Preguntas de mutación

1. **El chip deja de usar HTTP y habla MQTT.** Un adaptador de entrada nuevo para el reto y la credencial (los puertos `AutenticarDispositivoPort` y `RegistrarLecturaPort` ya existen); el dominio y el HMAC no cambian.
2. **Cambiar HMAC por firma con certificado.** Solo `AutenticadorDeDispositivos`: se escribe otra implementación. El dominio no sabe cómo se prueba el secreto; la columna `secreto` pasaría a guardar una clave pública, y es el único cambio de significado (por eso D75 describe el secreto como "texto opaco").
3. **Que el dueño pueda renombrar el dispositivo.** Un campo en `Dispositivo` y su migración (cambio de dominio deliberado, en su propio commit).
4. **Revocar de inmediato, sin esperar la hora.** Es la evolución de D76: una lista de tokens revocados consultada por `ConvertidorDeJwt`, o bajar `FITO_JWT_DISPOSITIVO_EXPIRACION`.
