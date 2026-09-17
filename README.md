# Fitodiagnóstico API

API REST que recibe una especie vegetal y tres lecturas ambientales (humedad del sustrato, luz y temperatura), las clasifica contra el rango óptimo de esa especie y devuelve el estado de la planta con recomendaciones.

**Front web:** https://github.com/Pejelagarto-AI/arqui-fitodiagnostico-frontend — cliente independiente, servido desde otro origen.

## Requisitos y ejecución

Solo Java 21. El wrapper trae Maven, no hace falta instalarlo.

```bash
./mvnw spring-boot:run     # http://localhost:8080, modo csv por defecto
```

**`fitodiagnostico.tabla-referencia`** decide la fuente de la tabla de referencia: `csv` (por defecto, lee `src/main/resources/referencia/especies.csv`) o `bd` (JPA + Flyway sobre H2 en memoria). Para arrancar en modo bd:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--fitodiagnostico.tabla-referencia=bd
```

Tensión conocida: en modo csv, H2 y Flyway igual arrancan, porque el datasource y las migraciones de este perfil no dependen de esta propiedad.

**`fitodiagnostico.cors.origenes`** — lista separada por comas de orígenes con permiso CORS sobre `/api/**`. Por defecto `http://localhost:5500,http://127.0.0.1:5500` (el front servido con `python3 -m http.server 5500`).

## Endpoints

Salidas reales, capturadas con la app corriendo.

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
./mvnw test     # 106 pruebas
```

| Grupo | Pruebas | Qué cubre |
|---|---|---|
| Dominio | 69 | JUnit puro, sin Spring: modelo (`Especie`, `Rango`, `Lectura`, `Medicion`, `ResultadoParametro`) y servicios (`ClasificadorDeParametros`, `EvaluadorDeEstado`, `RedactorDeRecomendaciones`, `ReglaPorDesviacion`) |
| Aplicación | 7 | `ServicioDiagnostico` y `ServicioCatalogo` con dobles en memoria del puerto (sin Mockito) |
| Contrato de adaptadores | 9 | `ContratoFuenteDeEspeciesTest` corrido contra CSV (JUnit puro) y contra JPA (`@DataJpaTest` con Flyway real) — misma prueba, dos implementaciones (LSP) |
| Web | 12 | `@WebMvcTest` del controlador de diagnósticos, del de especies y del preflight CORS |
| ArchUnit | 8 | reglas estructurales de `ArquitecturaTest` (ver `docs/ARQUITECTURA.md`) |
| Contexto | 1 | `FitodiagnosticoApplicationTests`, carga del `ApplicationContext` |

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
- [`docs/BITACORA-IA.md`](docs/BITACORA-IA.md) — bitácora de uso de IA en el proyecto.

---

**Santiago Ortegón** · **Santiago Castellanos**

Arquitectura de Software · U. Sergio Arboleda · 2026-2
