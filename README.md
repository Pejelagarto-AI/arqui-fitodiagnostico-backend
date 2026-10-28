# Fitodiagnóstico API

API REST que recibe una especie vegetal y tres lecturas ambientales (humedad del sustrato, luz y temperatura), las clasifica contra el rango óptimo de esa especie y devuelve el estado de la planta con recomendaciones.

**Front web:** https://github.com/Pejelagarto-AI/arqui-fitodiagnostico-frontend — cliente independiente, servido desde otro origen.

## Requisitos y ejecución

Solo Java 21. El wrapper trae Maven, no hace falta instalarlo.

Desde el corte 2 la aplicación **no arranca sin `FITO_JWT_SECRETO`** (ningún secreto vive en el repositorio fuera del perfil de pruebas):

```bash
export FITO_JWT_SECRETO="$(openssl rand -base64 48)"   # firma de los JWT, mínimo 32 bytes
export FITO_ADMIN_CORREO="admin@tu-dominio.co"          # primer administrador: solo se usa
export FITO_ADMIN_CONTRASENA="una-contrasena-larga"     # si todavía no existe ninguno
./mvnw spring-boot:run     # http://localhost:8080
```

### Fuente de la tabla de referencia (fase 2)

La tabla de especies se lee de la base de datos o del CSV, **elegido por configuración** y sin tocar código:

```bash
./mvnw spring-boot:run                              # bd (por defecto): la base es la fuente y se administra con la API
FITO_CATALOGO_FUENTE=csv ./mvnw spring-boot:run     # csv: se lee src/main/resources/referencia/especies.csv
```

- `bd`: el administrador crea, edita, desactiva y reactiva especies. El CSV solo siembra la tabla `especie` al arrancar si está vacía.
- `csv`: el catálogo es de solo lectura; cualquier alta, edición o (des)activación responde `409 CATALOGO_SOLO_LECTURA`. La tabla `especie` se reescribe con el CSV en cada arranque porque la planta guarda una clave foránea a ella.

Detalle de variables y decisiones en [`docs/corte2/IMPLEMENTACION.md`](docs/corte2/IMPLEMENTACION.md) y [`docs/fase2/IMPLEMENTACION.md`](docs/fase2/IMPLEMENTACION.md).

**`fitodiagnostico.cors.origenes`** — lista separada por comas de orígenes con permiso CORS sobre `/api/**` y de los que se acepta el handshake del WebSocket (`/ws`). Por defecto `http://localhost:5500,http://127.0.0.1:5500` (el front servido con `python3 -m http.server 5500`).

### Correo y enlaces (corte 2, bloque 4)

Las invitaciones y la recuperación de contraseña mandan correos, y los avisos de cambio de estado también. Todo es opcional y por variable de entorno; los valores por defecto apuntan a [Mailpit](https://github.com/axllent/mailpit), un servidor SMTP de desarrollo que muestra los correos en una bandeja web:

```bash
docker run -d --rm --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit   # bandeja: http://localhost:8025
./mvnw spring-boot:run                                                          # ya envía a localhost:1025
```

| Variable | Para qué | Por defecto |
|---|---|---|
| `FITO_SMTP_HOST`, `FITO_SMTP_PUERTO` | servidor SMTP | `localhost`, `1025` |
| `FITO_SMTP_USUARIO`, `FITO_SMTP_CONTRASENA` | credenciales SMTP (vacías = sin autenticación) | vacías |
| `FITO_SMTP_AUTH`, `FITO_SMTP_STARTTLS` | `true` si el servidor lo exige | `false` |
| `FITO_CORREO_REMITENTE` | dirección "De:" | `no-responder@fitodiagnostico.local` |
| `FITO_CORREO_ASINCRONO` | enviar en otro hilo | `true` |
| `FITO_FRONT_URL` | base de los enlaces de los correos (`/invitacion?token=...`, `/restablecer?token=...`) | `http://localhost:5500` |

Sin servidor SMTP la aplicación arranca y funciona; cada correo falla con un `WARN` y nada más. Las pruebas no envían nada: el perfil `test` reemplaza el servidor por un doble que captura los mensajes.

## Cómo ejecutar el back, el front y el simulador

| Pieza | Comando | Dónde |
|---|---|---|
| **Back** (este repo) | `./mvnw spring-boot:run` (con `FITO_JWT_SECRETO`, ver arriba) | http://localhost:8080 |
| **Front** ([repo del front](https://github.com/Pejelagarto-AI/arqui-fitodiagnostico-frontend)) | desde su carpeta: `python3 -m http.server 5500` | http://localhost:5500 (el origen que el back acepta por defecto) |
| **Simulador del ESP32** | `FITO_TOKEN_DISPOSITIVO=<token> python3 simulador/simulador_esp32.py` | solo Python 3, ver [`simulador/README.md`](simulador/README.md) |

### Token de dispositivo (fase 2)

El dueño de una planta autoriza un dispositivo y recibe un token de **alcance limitado**: sirve únicamente para reportar lecturas de esa planta y se rechaza (403) en cualquier otro endpoint. Un token de persona, al revés, no abre el endpoint del dispositivo.

```bash
B=http://localhost:8080/api/v1

# 1. El dueño (JWT de usuario) emite el token del dispositivo. 201; 403 si es invitado; 404 si la planta es ajena.
curl -X POST $B/plantas/$PLANTA_ID/dispositivos \
  -H "Authorization: Bearer $TOKEN_DE_USUARIO" -H "Content-Type: application/json" \
  -d '{"dispositivoId": "esp32-01"}'
# {"dispositivoId":"esp32-01","plantaId":"...","token":"eyJ...","expira":"2027-10-19T15:00:00Z"}

# 2. El dispositivo reporta una lectura con ese token. La planta sale del token. 201 con el diagnóstico.
curl -X POST $B/dispositivos/esp32-01/lecturas \
  -H "Authorization: Bearer $TOKEN_DEL_DISPOSITIVO" -H "Content-Type: application/json" \
  -d '{"marcaDeTiempo": "2026-10-19T14:30:05Z", "humedad": 55, "luz": 700, "temperatura": 22}'

# 3. El dueño ve la lectura como el diagnóstico vigente de su planta (origen DISPOSITIVO).
curl $B/plantas/$PLANTA_ID/diagnostico -H "Authorization: Bearer $TOKEN_DE_USUARIO"
```

Errores del dispositivo: `400 PARAMETRO_INVALIDO` (campo ausente, valor no numérico, fuera del rango físico del sensor, fecha mal formada), `401` sin token, `403` con un token de persona o de otro dispositivo. El token caduca a los 365 días (`FITO_JWT_DISPOSITIVO_EXPIRACION`, ISO-8601) y **no se puede revocar de forma individual**: no se guardan dispositivos en la base.

## Endpoints

Con token de dispositivo solo se abre `POST /api/v1/dispositivos/{id}/lecturas`; en todas las demás rutas (también las públicas) ese token responde 403. Públicos (sin token): `POST /api/v1/diagnosticos`, `GET /api/v1/especies`, `POST /api/v1/auth/registro`, `POST /api/v1/auth/sesion`, `POST /api/v1/auth/recuperacion`, `POST /api/v1/auth/restablecimiento`, `POST /api/v1/invitaciones/aceptar-con-registro` y el handshake `/ws` (que se autentica en el primer frame STOMP). El resto exige `Authorization: Bearer <token>`; `/api/v1/admin/**` exige además el rol `ADMINISTRADOR`. Los 401 y 403 también son JSON con el cuerpo uniforme de errores.

| Módulo | Rutas (corte 2, bloques 1, 3 y 4) |
|---|---|
| Cuentas | `POST /api/v1/auth/registro`, `POST /api/v1/auth/sesion`, `GET/PUT/DELETE /api/v1/cuenta`, `PUT /api/v1/cuenta/contrasena` |
| Administración de usuarios | `GET /api/v1/admin/usuarios?q=`, `GET/PUT/DELETE /api/v1/admin/usuarios/{id}`, `PUT/DELETE /api/v1/admin/usuarios/{id}/rol-administrador` |
| Catálogo de especies | `GET/POST /api/v1/admin/especies`, `PUT /api/v1/admin/especies/{nombre}`, `POST /api/v1/admin/especies/{nombre}/desactivar`, `POST .../reactivar` |
| Plantas | `POST/GET /api/v1/plantas`, `GET/PUT/DELETE /api/v1/plantas/{id}` |
| Mediciones y diagnóstico de una planta | `POST /api/v1/plantas/{id}/mediciones` (solo el dueño, origen `MANUAL`), `GET /api/v1/plantas/{id}/diagnostico` (el vigente; sin diagnósticos responde 200 con `hayDiagnostico: false`), `GET /api/v1/plantas/{id}/diagnosticos?pagina=0&tamano=20` (historial paginado, el más reciente primero; `tamano` de 1 a 100) |

| Recuperación de contraseña | `POST /api/v1/auth/recuperacion {correo}` (202, el mismo cuerpo exista o no el correo), `POST /api/v1/auth/restablecimiento {token, contrasenaNueva}` (204; 410 si el enlace ya se usó, caducó o la contraseña cambió) |
| Compartir plantas (dueño) | `POST/GET /api/v1/plantas/{id}/invitaciones` (genera un enlace de un solo uso con caducidad de 7 días; el token se entrega solo en esta respuesta; `{"correo"}` opcional lo envía por correo), `DELETE .../invitaciones/{invitacionId}`, `GET .../invitados`, `DELETE .../invitados/{invitadoId}` (el invitado recibe `ACCESO_PERDIDO`) |
| Compartir plantas (invitado) | `GET /api/v1/plantas/compartidas`, `DELETE /api/v1/plantas/compartidas/{id}` (renunciar), `POST /api/v1/invitaciones/aceptar {token}`, `POST /api/v1/invitaciones/aceptar-con-registro {token, nombre, correo, contrasena}` (público: el visitante sin cuenta se registra y acepta en un solo paso) |
| Solicitudes de especie | `POST/GET /api/v1/solicitudes-especie` (pedir y ver las propias); administrador: `GET /api/v1/admin/solicitudes-especie`, `POST .../{id}/aprobar`, `POST .../{id}/rechazar` (con `{"respuesta"}`; aprobar no da de alta la especie, eso es `POST /admin/especies`) |
| Dispositivos (fase 2) | `POST /api/v1/plantas/{id}/dispositivos {dispositivoId}` (solo el dueño; devuelve el token del dispositivo), `POST /api/v1/dispositivos/{dispositivoId}/lecturas {marcaDeTiempo, humedad, luz, temperatura}` (solo con el token de dispositivo; origen `DISPOSITIVO`) |
| Vista en vivo | `ws://host/ws` (STOMP sobre WebSocket, sin SockJS). Primer frame `CONNECT` con `Authorization: Bearer <JWT>` (el mismo de la API). Suscripciones: `/user/queue/plantas/{id}` (`DIAGNOSTICO_NUEVO` y `CAMBIO_DE_ESTADO`; solo si se puede leer esa planta) y `/user/queue/avisos` (`ACCESO_PERDIDO`, `SOLICITUD_RESUELTA`). El cliente no puede enviar |

`GET /api/v1/especies` devuelve solo las especies activas; las desactivadas siguen siendo diagnosticables.

Plantas: cada cliente ve y modifica solo las suyas (un administrador también). Una planta ajena a la que no se tiene acceso responde **404**, igual que una inexistente, para no revelar qué ids existen. Un invitado (acceso de lectura que se obtiene aceptando un enlace de invitación) puede consultar pero no editar, eliminar, compartir ni medir (403). Registrar una medición guarda el diagnóstico en el historial, avisa un `DIAGNOSTICO_NUEVO` a la vista en vivo del dueño y de los invitados y, si el estado global cambió respecto del anterior, un `CAMBIO_DE_ESTADO` por registro, correo y vista en vivo. **Los avisos salen después del commit**: si la transacción se revierte, no salen. Eliminar una planta, o una cuenta, elimina sus plantas, su historial, sus accesos, sus invitaciones pendientes y las solicitudes de la cuenta (y avisa a los invitados). Consultar nunca escribe. `POST /api/v1/diagnosticos` (el diagnóstico suelto, sin cuenta) no cambió.

Las salidas de abajo (corte 1) fueron capturadas con la app corriendo.

### `GET /api/v1/especies`

```bash
curl http://localhost:8080/api/v1/especies
```

```json
[
  {
    "nombre": "sansevieria",
    "rangos": {
      "humedad": { "min": 20.0, "max": 45.0, "unidad": "%" },
      "luz": { "min": 200.0, "max": 1500.0, "unidad": "lux" },
      "temperatura": { "min": 15.0, "max": 29.0, "unidad": "°C" }
    }
  }
]
```

(devuelve las 5 especies del Anexo B, ordenadas por nombre; se recorta a una para el ejemplo)

### `POST /api/v1/diagnosticos`

```bash
curl -X POST http://localhost:8080/api/v1/diagnosticos \
  -H "Content-Type: application/json" \
  -d '{"especie":"sansevieria","humedad":15,"luz":800,"temperatura":22}'
```

```json
{
  "especie": "sansevieria",
  "estado": "EN_RIESGO",
  "parametros": [
    { "nombre": "humedad", "valor": 15.0, "unidad": "%", "rangoOptimo": [20.0, 45.0], "estado": "BAJO" },
    { "nombre": "luz", "valor": 800.0, "unidad": "lux", "rangoOptimo": [200.0, 1500.0], "estado": "OPTIMO" },
    { "nombre": "temperatura", "valor": 22.0, "unidad": "°C", "rangoOptimo": [15.0, 29.0], "estado": "OPTIMO" }
  ],
  "recomendaciones": [
    "Humedad del sustrato 15 % por debajo del rango óptimo 20–45 %: regar moderadamente."
  ]
}
```

Estados posibles: `SALUDABLE`, `EN_RIESGO`, `CRITICO` (regla por desviación, umbral 25 % del ancho del rango). Un parámetro dentro de rango es `OPTIMO`; fuera, `BAJO` o `ALTO`.

### Errores

Todos los errores tienen la forma `{"error", "mensaje", "detalle"}`.

| HTTP | `error` | Cuándo |
|---|---|---|
| 415 | `TIPO_DE_CONTENIDO_NO_SOPORTADO` | el cuerpo no se envió como `application/json` |
| 400 | `PARAMETRO_INVALIDO` | campo ausente/en blanco, valor no numérico, JSON malformado, o lectura físicamente imposible (p. ej. humedad negativa) |
| 404 | `ESPECIE_NO_SOPORTADA` | la especie no existe en la tabla de referencia |
| 401 | `NO_AUTENTICADO` / `CREDENCIALES_INVALIDAS` | sin token o token inválido / correo o contraseña incorrectos |
| 403 | `ACCESO_DENEGADO` | autenticado pero sin el rol que exige la ruta |
| 404 | `USUARIO_NO_ENCONTRADO` | no existe ese usuario |
| 404 | `PLANTA_NO_ENCONTRADA` | la planta no existe **o** no es tuya ni compartida contigo (mismo cuerpo en ambos casos) |
| 403 | `OPERACION_NO_PERMITIDA` | un invitado intenta editar, eliminar o medir una planta ajena que sí puede ver |
| 409 | `CORREO_YA_REGISTRADO` / `ULTIMO_ADMINISTRADOR` / `ESPECIE_YA_EXISTE` / `ESPECIE_DESCONTINUADA` | conflicto con el estado actual (`ESPECIE_DESCONTINUADA`: registrar una planta, o cambiarla, a una especie ya desactivada) |
| 404 | `INVITACION_NO_ENCONTRADA` / `INVITADO_NO_ENCONTRADO` / `SOLICITUD_NO_ENCONTRADA` | el token no corresponde a ninguna invitación / ese usuario no es invitado de la planta / no existe la solicitud |
| 410 | `INVITACION_YA_ACEPTADA` / `INVITACION_CADUCADA` / `INVITACION_REVOCADA` / `RESTABLECIMIENTO_NO_VIGENTE` | el enlace existió pero ya no sirve (un enlace de invitación se usa una sola vez y caduca; el de restablecimiento caduca a la hora y se invalida si la contraseña cambia) |
| 409 | `CATALOGO_SOLO_LECTURA` | modificar el catálogo cuando la fuente es el CSV (`FITO_CATALOGO_FUENTE=csv`) |
| 409 | `SOLICITUD_YA_RESUELTA` | la solicitud ya fue aprobada o rechazada |
| 404 | `RECURSO_NO_ENCONTRADO` | ruta inexistente |
| 405 | `METODO_NO_PERMITIDO` | método HTTP no soportado por el recurso |
| 500 | `ERROR_INTERNO` | cualquier otra excepción; nunca expone traza ni SQL |

Ejemplo real, especie inexistente:

```bash
curl -X POST http://localhost:8080/api/v1/diagnosticos -H "Content-Type: application/json" \
  -d '{"especie":"cactus","humedad":30,"luz":800,"temperatura":22}'
# HTTP 404
{"error":"ESPECIE_NO_SOPORTADA","mensaje":"no existe una especie registrada con nombre 'cactus'","detalle":{"especie":"cactus"}}
```

## Pruebas

```bash
./mvnw verify     # 718 pruebas (113 antes del corte 2, 442 antes del bloque 4, 663 antes de la fase 2)
```

| Grupo | Qué cubre |
|---|---|
| Dominio (JUnit puro) | modelo (`Especie`, `Usuario`, `Credencial`, `Rango`, ...) y servicios (`PoliticaDeAdministradores`, clasificador, evaluador, ...) |
| Aplicación | `ServicioDiagnostico`, `ServicioCatalogo`, `ServicioCuentas`, `ServicioAdministracion`, `ServicioPlantas`, `ServicioMediciones`, `ServicioConsultaDeDiagnosticos`, `ServicioInvitaciones`, `ServicioRecuperacion`, `ServicioSolicitudes` y la cascada de eliminación con dobles en memoria de los puertos (sin Spring ni base) |
| Contrato de puertos | `ContratoFuenteDeEspeciesTest` (lectura y escritura) contra el adaptador JPA y un doble en memoria: el puerto es sustituible (LSP) |
| Adaptadores de salida | adaptadores JPA de especies, usuarios, plantas, accesos, historial, invitaciones, restablecimientos y solicitudes con Flyway real (V1 a V10), BCrypt, JWT, generador de tokens, sembradores, y los notificadores (registro, correo, WebSocket, compuesto, después del commit) |
| Adaptadores de entrada | `@WebMvcTest` de controladores, seguridad (401/403/rutas públicas), CORS; `SinHtmlTest` con el contenedor real; `FlujoDeCuentasIntegracionTest`, `FlujoDePlantasIntegracionTest`, `FlujoDeCompartirIntegracionTest` (compartir, recuperación y solicitudes con correo capturado) y `VistaEnVivoIntegracionTest` (cliente STOMP real) de punta a punta |
| ArchUnit | 26 reglas de `ArquitecturaTest` (9 nuevas de la fase 2: el dominio solo depende de dominio y `java.*`, los puertos son interfaces, entrada y salida no se conocen, los adaptadores no dependen de `config` ni de servicios de aplicación). De las 17 anteriores: las 8 del corte 1, 2 del bloque 1 (el dominio no conoce seguridad/cifrado ni WebSocket), 2 del bloque 3 (consultar un diagnóstico no puede producirlo ni avisarlo; las políticas de dominio no llaman puertos) y 5 del bloque 4 (el dominio no conoce el correo; correo y WebSocket solo en adaptadores; la aplicación no sincroniza transacciones; las entidades no guardan tokens en claro; la entrada no depende de la salida) |

`scripts/probar-dominio-sin-infraestructura.sh` copia el repo a un directorio temporal, borra por completo los paquetes `adaptador` y `config` (main y test) y corre solo las pruebas de dominio y aplicación sobre esa copia mutilada. Demuestra —no solo argumenta— que el dominio no necesita adaptadores para compilar ni para pasar sus pruebas.

## Tabla de referencia

Fuente: Anexo B del enunciado del proyecto de corte.

| Especie | Humedad (%) | Luz (lux) | Temperatura (°C) |
|---|---|---|---|
| sansevieria | 20–45 | 200–1500 | 15–29 |
| potos | 40–70 | 300–1200 | 18–30 |
| suculenta | 10–30 | 800–2500 | 15–32 |
| helecho | 60–85 | 150–800 | 16–26 |
| lavanda | 25–50 | 1000–3000 | 15–30 |

## Documentación

- [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md) — diagramas, responsabilidades por capa, justificación SOLID, plan de evolución y decisiones.
- [`docs/corte2/IMPLEMENTACION.md`](docs/corte2/IMPLEMENTACION.md) — corte 2, bloques 1 (cuentas, administración, catálogo), 3 (plantas, mediciones, cambio de estado, cascadas) y 4 (compartir plantas, recuperación de contraseña, correo, vista en vivo por WebSocket, solicitudes de especie): mapa de archivos, decisiones D20+, límites conocidos y preguntas de mutación.
- [`docs/fase2/IMPLEMENTACION.md`](docs/fase2/IMPLEMENTACION.md) — fase 2 (arquitectura hexagonal): mapa de paquetes, tabla de puertos y adaptadores, dónde se emite y verifica el JWT, la prueba de cambio, el análisis MQTT y las decisiones D63 a D73.
- [`docs/BITACORA-IA.md`](docs/BITACORA-IA.md) — bitácora de uso de IA en el proyecto.

---

**Santiago Ortegón** · **Santiago Castellanos**

Arquitectura de Software · U. Sergio Arboleda · 2026-2
