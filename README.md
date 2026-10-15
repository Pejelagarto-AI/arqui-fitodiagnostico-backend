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

La base de datos es la única fuente de las especies en ejecución. `src/main/resources/referencia/especies.csv` son datos semilla: se cargan a la base al arrancar solo si la tabla `especie` está vacía (el modo `tabla-referencia=csv` ya no existe). Detalle de variables y decisiones en [`docs/corte2/IMPLEMENTACION.md`](docs/corte2/IMPLEMENTACION.md).

**`fitodiagnostico.cors.origenes`** — lista separada por comas de orígenes con permiso CORS sobre `/api/**`. Por defecto `http://localhost:5500,http://127.0.0.1:5500` (el front servido con `python3 -m http.server 5500`).

## Endpoints

Públicos (sin token): `POST /api/v1/diagnosticos`, `GET /api/v1/especies`, `POST /api/v1/auth/registro`, `POST /api/v1/auth/sesion`. El resto exige `Authorization: Bearer <token>`; `/api/v1/admin/**` exige además el rol `ADMINISTRADOR`. Los 401 y 403 también son JSON con el cuerpo uniforme de errores.

| Módulo | Rutas (corte 2, bloque 1) |
|---|---|
| Cuentas | `POST /api/v1/auth/registro`, `POST /api/v1/auth/sesion`, `GET/PUT/DELETE /api/v1/cuenta`, `PUT /api/v1/cuenta/contrasena` |
| Administración de usuarios | `GET /api/v1/admin/usuarios?q=`, `GET/PUT/DELETE /api/v1/admin/usuarios/{id}`, `PUT/DELETE /api/v1/admin/usuarios/{id}/rol-administrador` |
| Catálogo de especies | `GET/POST /api/v1/admin/especies`, `PUT /api/v1/admin/especies/{nombre}`, `POST /api/v1/admin/especies/{nombre}/desactivar`, `POST .../reactivar` |

`GET /api/v1/especies` devuelve solo las especies activas; las desactivadas siguen siendo diagnosticables.

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
| 400 | `PARAMETRO_INVALIDO` | campo ausente/en blanco, valor no numérico, JSON malformado, o lectura físicamente imposible (p. ej. humedad negativa) |
| 404 | `ESPECIE_NO_SOPORTADA` | la especie no existe en la tabla de referencia |
| 401 | `NO_AUTENTICADO` / `CREDENCIALES_INVALIDAS` | sin token o token inválido / correo o contraseña incorrectos |
| 403 | `ACCESO_DENEGADO` | autenticado pero sin el rol que exige la ruta |
| 404 | `USUARIO_NO_ENCONTRADO` | no existe ese usuario |
| 409 | `CORREO_YA_REGISTRADO` / `ULTIMO_ADMINISTRADOR` / `ESPECIE_YA_EXISTE` | conflicto con el estado actual |
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
./mvnw verify     # 294 pruebas (113 antes del corte 2)
```

| Grupo | Qué cubre |
|---|---|
| Dominio (JUnit puro) | modelo (`Especie`, `Usuario`, `Credencial`, `Rango`, ...) y servicios (`PoliticaDeAdministradores`, clasificador, evaluador, ...) |
| Aplicación | `ServicioDiagnostico`, `ServicioCatalogo`, `ServicioCuentas`, `ServicioAdministracion` con dobles en memoria de los puertos (sin Spring ni base) |
| Contrato de puertos | `ContratoFuenteDeEspeciesTest` (lectura y escritura) contra el adaptador JPA y un doble en memoria: el puerto es sustituible (LSP) |
| Infraestructura | adaptadores JPA de especies y usuarios con Flyway real, BCrypt, JWT, sembradores |
| Web | `@WebMvcTest` de controladores, seguridad (401/403/rutas públicas), CORS; `SinHtmlTest` con el contenedor real; `FlujoDeCuentasIntegracionTest` de punta a punta |
| ArchUnit | 10 reglas de `ArquitecturaTest`: las 8 del corte 1 y 2 nuevas (el dominio no conoce seguridad/cifrado ni WebSocket) |

`scripts/probar-dominio-sin-infraestructura.sh` copia el repo a un directorio temporal, borra por completo el paquete `infraestructura` (main y test) y corre solo las pruebas de dominio y aplicación sobre esa copia mutilada. Demuestra —no solo argumenta— que el dominio no necesita infraestructura para compilar ni para pasar sus pruebas.

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
- [`docs/corte2/IMPLEMENTACION.md`](docs/corte2/IMPLEMENTACION.md) — corte 2, bloque 1: mapa de archivos, decisiones D20+, límites conocidos y preguntas de mutación.
- [`docs/BITACORA-IA.md`](docs/BITACORA-IA.md) — bitácora de uso de IA en el proyecto.

---

**Santiago Ortegón** · **Santiago Castellanos**

Arquitectura de Software · U. Sergio Arboleda · 2026-2
