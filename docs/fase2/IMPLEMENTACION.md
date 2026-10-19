# Fase 2: arquitectura hexagonal (puertos y adaptadores)

Qué se construyó, dónde vive cada pieza y por qué, y qué decisiones se tomaron con la alternativa descartada. Este archivo es el cuaderno de implementación (como `docs/corte2/IMPLEMENTACION.md`); **no es el documento de arquitectura de la entrega**, que escriben los integrantes con su propio diagrama y sus palabras.

Alcance: reorganizar el código en dominio, puertos de entrada y de salida, adaptadores y un único punto de composición; tabla de referencia intercambiable CSV o BD por configuración; token de dispositivo con alcance limitado; adaptador de entrada del ESP32 con su simulador, agregado sin tocar el dominio. Los endpoints del front no cambiaron de ruta.

Commits de la fase (rama `feature/fase2-hexagonal`):

| Commit | Qué hace |
|---|---|
| `3b837b6` | `refactor: estructura hexagonal con puertos de entrada y salida` (sin cambio de comportamiento) |
| `b47b59b` | `feat(dominio)`: la medición registra cuándo se tomó (`tomadaEn`), antes del adaptador a propósito |
| `fb75464` | `feat`: tabla de referencia intercambiable CSV o BD por configuración |
| `c2f9b94` | `feat(seguridad)`: token de dispositivo con alcance limitado a reportar lecturas |
| `8a5bc23` | `feat(dispositivos)`: adaptador de entrada del ESP32 y simulador: **la prueba de cambio** |

## 1. Cómo se ejecuta y se prueba a mano

Variables de entorno, el back y el front: ver el [README](../../README.md). Recorrido de punta a punta con el simulador:

```bash
# 1. back (puerto 8080)
export FITO_JWT_SECRETO="$(openssl rand -base64 48)"
export FITO_ADMIN_CORREO="admin@tu-dominio.co" FITO_ADMIN_CONTRASENA="una-contrasena-larga"
./mvnw spring-boot:run

# 2. un usuario, su planta y el token de su dispositivo
B=http://localhost:8080/api/v1
curl -s -X POST $B/auth/registro -H 'Content-Type: application/json' \
  -d '{"nombre":"Ana","correo":"ana@vivero.co","contrasena":"clave-larga-1"}'
TOKEN=$(curl -s -X POST $B/auth/sesion -H 'Content-Type: application/json' \
  -d '{"correo":"ana@vivero.co","contrasena":"clave-larga-1"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')
PLANTA=$(curl -s -X POST $B/plantas -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"apodo":"Pepa","especie":"potos"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
curl -s -X POST $B/plantas/$PLANTA/dispositivos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"dispositivoId":"esp32-01"}'
#   -> {"dispositivoId":"esp32-01","plantaId":"...","token":"eyJ...","expira":"2027-10-19T..."}

# 3. el simulador, con ese token
export FITO_TOKEN_DISPOSITIVO="eyJ..."
python3 simulador/simulador_esp32.py --intervalo 3

# 4. cambiar la fuente de la tabla de referencia, sin tocar código
FITO_CATALOGO_FUENTE=csv ./mvnw spring-boot:run     # o bd (por defecto)
```

## 2. Mapa de archivos

Regla de lectura: las flechas de dependencia apuntan hacia adentro. `adaptador.*` conoce `dominio` (los puertos); `dominio` no conoce a nadie. `config` es el único que conoce a todos.

```
com.vivero.fitodiagnostico
├── dominio                      el hexágono: reglas y contratos
│   ├── modelo, servicio, excepcion
│   └── puerto
│       ├── entrada              lo que la aplicación OFRECE (casos de uso)
│       └── salida               lo que la aplicación NECESITA (lo implementa un adaptador)
├── aplicacion                   los casos de uso: implementan los puertos de entrada, usan los de salida
├── adaptador
│   ├── entrada                  traducen un estímulo externo a una llamada a un puerto de entrada
│   │   ├── rest  (+ .seguridad, .dto)   el front
│   │   ├── dispositivo                  el ESP32
│   │   ├── websocket                    suscripción a la vista en vivo
│   │   └── arranque                     el primer administrador
│   └── salida                   implementan un puerto de salida con una tecnología
│       ├── jpa   csv   seguridad   notificacion
└── config                       composición: el único sitio que decide qué adaptador va en qué puerto
```

### dominio.puerto.entrada (22 puertos, interfaces pequeñas con nombre de negocio)

| Puerto | Lo implementa | Lo llama (adaptador de entrada) |
|---|---|---|
| `DiagnosticarPort` | `ServicioDiagnostico` | `DiagnosticoController` (RF-F6, público) |
| `RegistrarMedicionManualPort` | `ServicioMediciones` | `MedicionesController` (dueño, origen `MANUAL`) |
| `RegistrarLecturaPort` | `ServicioMediciones` | `LecturasDeDispositivoController` (origen `DISPOSITIVO`) |
| `ConsultarEstadoPort`, `ConsultarHistorialPort` | `ServicioConsultaDeDiagnosticos` | `DiagnosticosDePlantaController` |
| `ConsultarEspeciesPort` | `ServicioCatalogo` | `EspeciesController` |
| `ConsultarCatalogoCompletoPort` | `ServicioCatalogo` | `AdministracionEspeciesController` |
| `AdministrarEspeciesPort`, `AdministrarUsuariosPort` | `ServicioAdministracion` | `AdministracionEspeciesController`, `AdministracionUsuariosController` |
| `RegistrarCuentaPort`, `IniciarSesionPort` | `ServicioCuentas` | `AutenticacionController` |
| `ConsultarCuentaPort` | `ServicioCuentas` | `CuentaController`, `ConvertidorDeJwt`, `ProtectorDeCanalStomp` |
| `GestionarCuentaPort` | `ServicioCuentas` | `CuentaController` |
| `AsegurarAdministradorInicialPort` | `ServicioCuentas` | `SembradorDeAdministrador` |
| `ConsultarPlantasPort`, `GestionarPlantasPort` | `ServicioPlantas` | `PlantasController` (la primera también `ProtectorDeCanalStomp`) |
| `CompartirPlantaPort`, `AceptarInvitacionPort` | `ServicioInvitaciones` | `InvitacionesController` |
| `RecuperarContrasenaPort` | `ServicioRecuperacion` | `RecuperacionController` |
| `SolicitarEspeciePort`, `ResolverSolicitudesDeEspeciePort` | `ServicioSolicitudes` | `SolicitudesEspecieController`, `AdministracionSolicitudesController` |
| `VincularDispositivoPort` | `ServicioDispositivos` | `VinculoDeDispositivosController` |

Los records de resultado (`IniciarSesionPort.Sesion`, `CompartirPlantaPort.InvitacionGenerada`, `AdministrarUsuariosPort.DetalleDeUsuario`...) viven dentro del puerto que los devuelve: el contrato incluye su forma.

### dominio.puerto.salida y sus adaptadores (puerto → adaptadores)

| Puerto de salida | Adaptador(es) | Paquete |
|---|---|---|
| `RangosPorEspecie`, `CatalogoDeEspecies` | `AdaptadorEspecieJpa` **o** `AdaptadorEspecieCsv` (por `fitodiagnostico.catalogo.fuente`) | `salida.jpa` / `salida.csv` |
| `CatalogoEditable` | `AdaptadorEspecieJpa` **o** `CatalogoCsvSoloLectura` (lanza `CatalogoSoloLecturaException`, 409) | `salida.jpa` / `salida.csv` |
| `RepositorioPlantas`, `RepositorioAccesos`, `RepositorioDiagnosticos` | `AdaptadorPlantaJpa`, `AdaptadorAccesoJpa`, `AdaptadorDiagnosticoJpa` | `salida.jpa` |
| `RepositorioUsuarios`, `RepositorioInvitaciones`, `RepositorioRestablecimientos`, `RepositorioSolicitudes` | `AdaptadorUsuarioJpa`, `AdaptadorInvitacionJpa`, `AdaptadorRestablecimientoJpa`, `AdaptadorSolicitudEspecieJpa` | `salida.jpa` |
| `Notificador` | `NotificadorTrasCommit` → `NotificadorCompuesto` → `NotificadorEnRegistro` / `NotificadorPorCorreo` / `NotificadorPorWebSocket` | `salida.notificacion` |
| `CifradorDeContrasena` | `CifradorBcrypt` | `salida.seguridad` |
| `GeneradorDeTokens` | `GeneradorDeTokensSeguro` | `salida.seguridad` |
| `EmisorDeTokens` (sesión de persona) | `EmisorDeTokensJwt` | `salida.seguridad` |
| `EmisorTokenDispositivoPort` (credencial de dispositivo) | `EmisorTokenDispositivoJwt` | `salida.seguridad` |

### Dónde vive cada pieza y por qué

| Archivo / paquete | Por qué vive ahí |
|---|---|
| `dominio.puerto.entrada.*` | Lo que la aplicación ofrece lo define el dominio, no el controlador. Un adaptador nuevo (MQTT, app móvil) llama a estos mismos métodos. Están en `dominio` y no en `aplicacion` para que `dominio` sea el único paquete del que dependen todos. |
| `dominio.puerto.salida.*` | Lo que la aplicación necesita lo define el dominio (inversión de dependencias): la interfaz es suya, la tecnología es de otro. Es el antiguo `dominio.puerto`, movido con `git mv`. |
| `dominio.modelo.TokenDeDispositivo` | Es un valor del negocio (qué dispositivo, de qué planta, hasta cuándo), como `TokenDeSesion`. Que sea un JWT no lo sabe el dominio. |
| `aplicacion.ServicioDispositivos` | La regla "solo el dueño autoriza un dispositivo" es de negocio: usa `AutorizadorDePlantas` y `PoliticaDeAcceso`, igual que editar o eliminar la planta. El token se firma detrás de un puerto. |
| `aplicacion` (en general) | Los casos de uso están dentro del hexágono: orquestan puertos y dominio. Anotados con `@Service`/`@Transactional` de Spring: la regla estricta de "sin Spring" es del paquete `dominio`, no de este. |
| `adaptador.entrada.rest` | Controladores, DTO y manejo de errores del front. Traduce HTTP/JSON a llamadas a puertos. Valida forma; nunca reglas de negocio. |
| `adaptador.entrada.rest.seguridad` | `ConvertidorDeJwt` (del JWT ya verificado a la identidad de la petición) y `ManejadorDeErroresDeSeguridad` (401/403 JSON). Verificar un token es traducir un estímulo externo: es entrada. |
| `adaptador.entrada.dispositivo` | El endpoint del ESP32. Carpeta aparte porque es otro canal con otro token y otro cuerpo; **agregarla fue todo el cambio**. |
| `adaptador.entrada.websocket` | `ProtectorDeCanalStomp` y `HandshakeConErroresJson` autentican y autorizan lo que el cliente envía (CONNECT, SUBSCRIBE) llamando a puertos de entrada: es **entrada**. El empuje de avisos hacia el navegador es otra cosa, `NotificadorPorWebSocket`, que es **salida** (implementa `Notificador`). Mismo transporte, sentidos distintos. |
| `adaptador.entrada.arranque` | `SembradorDeAdministrador` reacciona al arranque y llama a un caso de uso: un adaptador de entrada sin HTTP. |
| `adaptador.salida.jpa` | Entidades, repositorios Spring Data y adaptadores. Solo aquí se nombra JPA o Spring Data (regla ArchUnit). |
| `adaptador.salida.csv` | `LectorDeEspeciesCsv`, `AdaptadorEspecieCsv` (lectura), `CatalogoCsvSoloLectura` (escritura rechazada), `EspejoDeEspecies` y `SembradorDeEspecies` (copian el CSV a la tabla). |
| `adaptador.salida.seguridad` | BCrypt y los dos emisores JWT. Único sitio que nombra la librería de tokens para firmar. |
| `adaptador.salida.notificacion` | Correo, registro, WebSocket (empuje), el compuesto y el decorador "tras commit". `EnlacesDelFront` (URLs de los correos) vive aquí porque solo el correo lo usa. |
| `config` | **Único punto de composición**: `ConfiguracionDominio` (políticas y evaluador), `ConfiguracionCatalogo` (CSV o BD), `ConfiguracionNotificacion`, `ConfiguracionJwt`, `ConfiguracionSeguridad` (la cadena de filtros y los alcances), `ConfiguracionWebSocket`, `ConfiguracionSemilla`. Ningún adaptador depende de `config` (regla ArchUnit). |
| `ConfiguracionCors`, `ConfiguracionContenido` | Se quedan en `adaptador.entrada.rest`: son detalle del protocolo HTTP (cabeceras CORS, negociación de contenido), no cableado entre puertos y adaptadores. |

## 3. Dónde se emite y dónde se verifica el JWT

| Qué | Dónde | Detalle |
|---|---|---|
| Emitir token de **persona** | `EmisorDeTokensJwt` (`salida.seguridad`), detrás de `EmisorDeTokens`, llamado por `ServicioCuentas.iniciarSesion` | `sub` = id de usuario, `tipo=usuario`, caducidad `FITO_JWT_EXPIRACION` (PT2H). Sin roles: se leen de la base en cada petición. |
| Emitir token de **dispositivo** | `EmisorTokenDispositivoJwt` (`salida.seguridad`), detrás de `EmisorTokenDispositivoPort`, llamado por `ServicioDispositivos.vincular` | `sub` = dispositivoId, `tipo=dispositivo`, `scope=lecturas:reportar`, `planta` = id, caducidad `FITO_JWT_DISPOSITIVO_EXPIRACION` (P365D). |
| Firma y caducidad | `ConfiguracionJwt` (`config`): `JwtDecoder` HS256 con emisor `fitodiagnostico` | Mismo secreto y emisor para los dos tipos. |
| Del token a la identidad | `ConvertidorDeJwt` (`entrada.rest.seguridad`) | Persona: consulta al usuario (vía `ConsultarCuentaPort`) y deriva roles. Dispositivo: no toca la base; su única autoridad es `SCOPE_lecturas:reportar`; rechaza tokens incoherentes (sin planta, alcance desconocido). |
| Qué ruta abre cada tipo | `ConfiguracionSeguridad` (`config`) | Token de dispositivo: solo `POST /api/v1/dispositivos/*/lecturas`; en cualquier otra ruta, públicas incluidas, 403 uniforme `ACCESO_DENEGADO`. Token de persona en el endpoint del dispositivo: 403. Sin token: 401. |
| Comprobación fina del dispositivo | `LecturasDeDispositivoController` | Alcance, `sub` = dispositivo de la ruta (si no, 403), planta tomada **del token**. |
| Regla de propiedad | Dominio/aplicación: `PoliticaDeAcceso` + `AutorizadorDePlantas` | "Un usuario solo ve sus plantas", "solo el dueño emite un token de dispositivo". Ningún caso de uso ve un token ni una cabecera: reciben un `UUID` de usuario o la planta ya resuelta. |

El dominio no importa nada de tokens (regla ArchUnit `el_dominio_solo_depende_de_dominio_y_java`).

## 4. La prueba de cambio (commit `8a5bc23`)

Enunciado: el commit que agrega el adaptador de dispositivos y el simulador no modifica ningún archivo del dominio.

```
$ git show --stat 8a5bc23b533bc6979831be4514620f53eea47ecf
 simulador/README.md                                |  59 ++++++
 simulador/simulador_esp32.py                       | 197 +++++++++++++++++
 .../LecturasDeDispositivoController.java           |  68 ++++++
 .../entrada/dispositivo/RespuestaLectura.java      |  40 ++++
 .../entrada/dispositivo/SolicitudLectura.java      |  36 ++++
 .../LecturasDeDispositivoIntegracionTest.java      | 232 +++++++++++++++++++++
 6 files changed, 632 insertions(+)
```

Seis archivos, todos nuevos, ninguno bajo `src/main/.../dominio` ni `src/test/.../dominio`; no cambia ni `aplicacion` ni `config`. Se verifica con `git show --stat 8a5bc23 | grep dominio` (sin salida).

Qué hizo posible que fuera así, y por qué dos cosas de dominio se hicieron **antes**:

- `RegistrarLecturaPort.registrar(plantaId, medicion, origen, tomadaEn)` ya existía con todo lo que un canal necesita aportar. Faltaba el instante: `DiagnosticoDePlanta` no distinguía cuándo se midió de cuándo se registró. Se agregó `tomadaEn` en `b47b59b` (con migración V11), **un commit antes**, porque ese cambio sí es de dominio y la restricción pide que no esté en el commit del adaptador.
- El token y su emisión (`TokenDeDispositivo`, `EmisorTokenDispositivoPort`, `VincularDispositivoPort`, `ServicioDispositivos`) se hicieron en `c2f9b94`: emitir la credencial es un caso de uso nuevo y sí toca dominio. El commit del adaptador solo **verifica** esa credencial en el borde.

## 5. Análisis de cambio: "mañana las lecturas llegan por MQTT en lugar de HTTP"

Archivos que cambian: **solo uno nuevo**, un adaptador de entrada. Por ejemplo `adaptador.entrada.mqtt.SuscriptorDeLecturasMqtt`: se suscribe al tópico, parsea el mensaje (el mismo `marcaDeTiempo`, `humedad`, `luz`, `temperatura`), convierte a `Medicion` y llama a `RegistrarLecturaPort.registrar(plantaId, medicion, DISPOSITIVO, marcaDeTiempo)`.

| Pieza | ¿Cambia? | Por qué |
|---|---|---|
| `dominio.*` (modelo, servicios, puertos) | No | El puerto ya recibe planta, medición, origen e instante. `OrigenDeMedicion.DISPOSITIVO` ya existe; solo si se quisiera distinguir HTTP de MQTT se agregaría un valor al enum (cambio de dominio deliberado). |
| `aplicacion.ServicioMediciones` | No | No sabe por dónde llegó la lectura. |
| `adaptador.entrada.dispositivo` (HTTP) | No | Pueden convivir los dos canales. |
| `config` | Casi: si el cliente MQTT necesita un bean (conexión, broker) se declara aquí; un `@Component` que se suscribe ni eso. |
| `pom.xml` | Sí | La dependencia de la librería MQTT (Eclipse Paho o Spring Integration). |
| Seguridad | Depende | La planta ya no puede salir del token JWT de HTTP: en MQTT la identidad la dan las credenciales del broker o el propio token en el mensaje. Eso se resuelve dentro del adaptador MQTT, y a lo sumo se reutiliza `EmisorTokenDispositivoPort` (el dueño sigue pidiendo un token). El dominio sigue sin saber qué es un token. |

Si en cambio cambiara solo **cómo se identifica** el dispositivo (certificados en lugar de JWT), cambia `ConvertidorDeJwt` / `ConfiguracionSeguridad` y el adaptador de salida que emite credenciales, no el dominio.

## 6. Decisiones técnicas (D63 en adelante)

**D63. Estructura hexagonal completa, con `git mv`.** `dominio.puerto` pasó a `dominio.puerto.salida`; `web` a `adaptador.entrada.rest`; `infraestructura.*` a `adaptador.salida.*` o `adaptador.entrada.*` según el sentido; todo el cableado a `config`. Es un refactor mecánico (la suite quedó verde tras el movimiento, solo con paquetes e imports cambiados, más las reglas ArchUnit nuevas). Descartado: dejar `web` e `infraestructura` y solo agregar `puerto.entrada`: obligaría a explicar que "infraestructura" mezcla entradas y salidas, que es justo lo que el hexágono separa.

**D64. Los puertos de entrada son 22 interfaces pequeñas con nombre de negocio, no una por servicio.** `ServicioCuentas` implementa cinco; `ServicioMediciones`, dos. Cada controlador pide solo lo que usa (ISP): `DiagnosticosDePlantaController` no ve `registrar`, y `ConvertidorDeJwt` pide `ConsultarCuentaPort` y no puede iniciar sesión por error. Descartado: una interfaz por servicio (`ServicioPlantasPort` con cinco métodos): cambiaría el tipo del controlador sin reducir lo que ve.

**D65. Los records de resultado se mudaron al puerto que los devuelve** (`IniciarSesionPort.Sesion`, etc.), porque el controlador ya no conoce la clase del servicio y el contrato debe incluir su forma. Descartado: dejarlos en el servicio: el adaptador tendría que importar `aplicacion`, y ArchUnit lo prohíbe.

**D66. `RutasDelFront` es la única clase de `aplicacion` que un adaptador puede nombrar.** Es una tabla de rutas del front, no un caso de uso, y la usan el controlador de invitaciones y el adaptador de correo. Moverla a un adaptador crearía dependencia entre adaptadores. La regla ArchUnit lo dice explícito (`los_adaptadores_dependen_de_puertos_y_no_de_servicios`).

**D67. `tomadaEn` es un campo aparte de `registradoEn`.** `registradoEn` (cuándo entró al historial) sigue ordenando "el vigente"; `tomadaEn` es la hora que informa quien mide. Así un ESP32 con el reloj desfasado, o que reenvía lecturas acumuladas, no altera cuál es el diagnóstico actual. Descartado: usar la marca del dispositivo como `registradoEn`: una hora errónea reordenaría el historial. Límite: el servidor no rechaza marcas absurdas (futuras o muy viejas); es una política que se podría agregar en `ServicioMediciones`.

**D68. La fuente del catálogo se elige en un solo sitio, `config.ConfiguracionCatalogo`.** `fitodiagnostico.catalogo.fuente=bd|csv` (por defecto `bd`) activa uno de dos grupos de beans con `@ConditionalOnProperty`. Con `csv`, `CatalogoEditable` queda ligado a `CatalogoCsvSoloLectura`, que lanza una excepción de dominio mapeada a 409 `CATALOGO_SOLO_LECTURA`. Descartado: que el adaptador JPA decida según la propiedad (mezcla dos tecnologías en una clase) y escribir en el CSV del classpath (el cambio se perdería al reiniciar y el administrador creería que quedó guardado).

**D69. En modo `csv` la tabla `especie` sigue existiendo como espejo, reescrito en cada arranque.** La planta guarda una clave foránea a `especie` (V6) y el historial se lee con los rangos de esa fila. Con el CSV como fuente, `EspejoDeEspecies` copia el CSV a la tabla al arrancar para que esa clave tenga a dónde apuntar y para que editar el CSV y reiniciar cambie los rangos de **todo**. El adaptador JPA del espejo se declara con `autowireCandidate = false` para que nadie lo reciba por error como puerto. Es la parte menos elegante de la fase y se dice así: sacar la clave foránea habría exigido una migración de plantas y mover la especie por nombre, un cambio mayor que el objetivo de la fase. Descartado: ignorar la tabla en modo csv (rompe crear plantas por la clave foránea).

**D70. El token de dispositivo es un JWT de alcance limitado, sin persistencia.** Lleva `tipo=dispositivo`, `scope=lecturas:reportar`, `sub` y `planta`; el token de persona lleva `tipo=usuario` (y si falta, se deriva como usuario, para no invalidar sesiones previas). Caducidad larga (P365D, configurable) porque un ESP32 no inicia sesión. **Límite explícito: no hay revocación individual.** Revocar es esperar la caducidad o rotar `FITO_JWT_SECRETO`, que invalida todos los tokens. Descartado: tabla de dispositivos con revocación (más trabajo y un estado más en el servidor, para un requisito que el enunciado no pide); se anota como evolución natural.

**D71. La planta del dispositivo sale del token, no de la ruta ni del cuerpo.** El dueño decide a qué planta sirve cada dispositivo al emitirle el token; el dispositivo no puede cambiarlo. La ruta lleva el `dispositivoId` solo para que el path sea REST y se compruebe contra `sub` (otro `sub`: 403). Descartado: `plantaId` en la ruta: el cliente podría escribir en cualquier planta si el token no la ata.

**D72. Un token de dispositivo se rechaza en cada ruta salvo la suya, incluidas las públicas.** La cadena usa tres `AuthorizationManager` (`SOLO_NO_DISPOSITIVO`, `SOLO_USUARIO_AUTENTICADO`, ...) en vez de `permitAll()` y `authenticated()`. Descartado: dejar pasar el token de dispositivo en las rutas públicas "porque son públicas": el enunciado dice alcance limitado, y un dispositivo no tiene motivo para listar especies ni diagnosticar.

**D73. Dos adaptadores de entrada no se conocen entre sí, ni siquiera para reutilizar un DTO.** `adaptador.entrada.dispositivo` redefine `SolicitudLectura` y `RespuestaLectura` en lugar de importar los DTO de `rest`; los nombres de los claims del token se repiten en `ConvertidorDeJwt` y en el controlador del dispositivo (el contrato lo escribe `EmisorTokenDispositivoJwt`). La duplicación es pequeña y está cubierta por pruebas de integración; la alternativa crearía un acoplamiento que las reglas ArchUnit prohíben (`los_adaptadores_de_entrada_no_se_conocen_entre_si`).

## 7. Límites conocidos (decirlos antes de que los encuentren)

- No hay revocación individual de dispositivos (D70).
- En modo `csv` el administrador no puede crear, editar ni desactivar especies (409), y la tabla `especie` es un espejo del CSV (D69). Una especie que solo existía en la base y no en el CSV queda en la tabla pero deja de ser visible para el dominio.
- `VistaEnVivoIntegracionTest` es intermitente: falla de vez en cuando por un orden de entrega de mensajes del WebSocket (espera `DIAGNOSTICO_NUEVO` y llega antes `CAMBIO_DE_ESTADO`). Ya fallaba así en `develop` antes de esta fase; no se tocó.
- El servidor no valida cuán lejos del presente está `marcaDeTiempo` (D67).
- El broker de la vista en vivo es el simple en memoria: con más de una instancia haría falta uno externo (heredado de D59).

## 8. Pruebas

`./mvnw verify`: **718 pruebas** (el README del corte 2 anotaba 663). ArchUnit pasa de 17 a 26 reglas.

| Capa | Qué se agregó |
|---|---|
| Dominio (JUnit puro, sin Spring, sin archivos ni base) | `DiagnosticoDePlantaTest` (marca de tiempo propia y por defecto), `TokenDeDispositivoTest` (formato del identificador). Las pruebas de lecturas en rango, fuera de rango e inválidas ya existían (`LecturaTest`, `MedicionTest`, `ClasificadorDeParametrosTest`, `EvaluadorDeEstadoTest`). |
| Aplicación (dobles en memoria de los puertos de salida) | `ServicioDispositivosTest` (solo el dueño; invitado 403, ajeno 404, sin emitir nada), `ServicioMedicionesTest` (reloj para lo manual, marca del dispositivo conservada). Dobles: `MundoEnMemoria`, `EmisorTokenDispositivoFijo`, `RelojManual`. |
| Adaptadores | `AdaptadorEspecieCsvTest` (lectura, solo lectura, espejo), `EmisorTokenDispositivoJwtTest`, `ConvertidorDeJwtTest` ampliado (dispositivo, tokens incoherentes, tipo desconocido). |
| Contexto real | `FuenteCsvIntegracionTest` y `FuenteBdPorDefectoIntegracionTest` (arrancan con cada fuente), `TokenDeDispositivoIntegracionTest` (emisión y alcances), `LecturasDeDispositivoIntegracionTest` (el endpoint: 201, 400 de cada tipo, 401, 403, 404). |
| ArchUnit (`ArquitecturaTest`) | `el_dominio_solo_depende_de_dominio_y_java`, `los_puertos_son_interfaces`, `la_entrada_no_depende_de_la_salida` y la inversa, `los_adaptadores_de_salida_no_se_conocen_entre_si`, `los_adaptadores_de_entrada_no_se_conocen_entre_si`, `los_adaptadores_no_dependen_de_la_composicion`, `los_adaptadores_dependen_de_puertos_y_no_de_servicios`, `los_puertos_de_entrada_los_implementa_la_aplicacion`, `los_puertos_de_salida_los_implementa_un_adaptador_de_salida`; las anteriores, adaptadas a los paquetes nuevos. |
| Script | `scripts/probar-dominio-sin-infraestructura.sh`: borra `adaptador` y `config` (main y test) de una copia y corre dominio + aplicación. Pasa. |

Smoke manual: se levantó la aplicación, se emitió un token y el simulador (`--prob-invalida 0.4`) obtuvo `201` con estados `SALUDABLE` y `CRITICO` y `400` uniforme para cada dato inválido.

## 9. Preguntas de mutación: "si mañana cambia X, ¿qué archivos tocas?"

1. **Las lecturas llegan por MQTT.** Un adaptador de entrada nuevo (sección 5). Dominio, aplicación y seguridad de personas intactos.
2. **La universidad exige iniciar sesión con OAuth en vez de usuario y contraseña.** Cambia el adaptador que verifica la identidad: `ConfiguracionJwt` (el decodificador pasa a validar contra el proveedor) y `ConvertidorDeJwt` (mapea el `sub` del proveedor a un `Usuario`). `ServicioCuentas.iniciarSesion`, `EmisorDeTokens` y `CifradorDeContrasena` dejan de usarse o se reducen al alta. Los casos de uso siguen recibiendo un `UUID`.
3. **El catálogo pasa a un servicio externo REST.** Un adaptador de salida nuevo que implementa `RangosPorEspecie` y `CatalogoDeEspecies`, y una rama más en `ConfiguracionCatalogo`. Nada más.
4. **La base pasa de PostgreSQL a otra.** Un perfil y migraciones en `db/migration/{vendor}`; los adaptadores JPA no cambian.
5. **Una app móvil consulta el estado.** Un adaptador de entrada nuevo (o el mismo REST) que llama a `ConsultarEstadoPort`; no hay caso de uso nuevo.
6. **El dispositivo manda también la batería.** El campo viaja en `SolicitudLectura` y `RespuestaLectura`; para guardarlo hace falta un cambio de dominio deliberado (un campo en el modelo y su migración), y se hace en su propio commit.
7. **¿Por qué `EmisorTokenDispositivoPort` es un puerto de salida y no un servicio de la aplicación?** Porque firmar es tecnología (JWT, HS256): la regla "solo el dueño" es del negocio y está en `ServicioDispositivos`; la firma, detrás del puerto.
8. **¿Dónde se rechaza que un dispositivo reporte por otro?** En `LecturasDeDispositivoController` (el `sub` debe ser el dispositivo de la ruta); y que no abra otras rutas, en `ConfiguracionSeguridad`. Ninguna de las dos es del dominio.
