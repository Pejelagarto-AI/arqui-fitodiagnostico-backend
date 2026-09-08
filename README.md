# Fitodiagnóstico API

API REST que recibe **una especie vegetal y tres lecturas ambientales** (temperatura, humedad relativa e iluminancia), consulta los umbrales de esa especie en base de datos y devuelve el estado en que se encuentra la planta.

Java 21 · Spring Boot 3.3.13 · H2 en memoria · Flyway · JPA

---

## Correrlo

```bash
./mvnw spring-boot:run     # http://localhost:8080
./mvnw test                # 65 pruebas
```

No hace falta instalar base de datos: H2 arranca en memoria y Flyway carga el esquema y 4 especies. Consola de la BD en `/h2-console` (JDBC `jdbc:h2:mem:fitodiagnostico`, usuario `SA`, sin clave).

## El endpoint

```
GET /api/v1/diagnosticos?especie=&temperaturaC=&humedadRelativa=&luzLux=
```

```bash
curl "http://localhost:8080/api/v1/diagnosticos?especie=Monstera%20deliciosa&temperaturaC=22&humedadRelativa=42&luzLux=1500"
```

```json
{
  "especie": { "nombreCientifico": "Monstera deliciosa", "nombreComun": "costilla de Adán" },
  "lectura": { "temperaturaC": 22.0, "humedadRelativa": 42.0, "luzLux": 1500 },
  "estado": "NECESITA_AGUA",
  "detalle": "humedad relativa 42.0 % por debajo del mínimo 55.0 %",
  "evaluadoEn": "2026-09-08T22:03:10Z"
}
```

Estados posibles: `NECESITA_ABRIGO`, `NECESITA_AGUA`, `NECESITA_LUZ`, `OPTIMO`. Devuelve **uno solo**, el de mayor prioridad. Errores: `400` validación, `404` especie desconocida, `422` lectura físicamente imposible — todos en `application/problem+json`.

## Arquitectura

```
Web  →  Aplicación  →  Dominio  ←  Infraestructura
```

Las dependencias apuntan siempre hacia adentro. El dominio no importa Spring ni JPA: es Java puro, y por eso sus 47 pruebas corren sin levantar contexto.

| Capa | Qué tiene |
|---|---|
| `web` | controlador, DTO, validación, `@RestControllerAdvice` |
| `aplicacion` | `ServicioDiagnostico` — el único caso de uso |
| `dominio` | `Especie`, `Ambiente`, `Rango`, `EstadoPlanta` + 4 estados, `EvaluadorDeEstado`, el puerto `RepositorioEspecies` |
| `infraestructura` | entidad JPA, Spring Data, mapeador, adaptador del puerto, configuración de beans |

**El estado no se decide con un `switch`.** Cada estado es una clase que implementa `EstadoPlanta` y sabe reconocerse a sí misma. `EvaluadorDeEstado` recorre la lista y devuelve el primero que aplique. Agregar un estado nuevo es escribir una clase y sumarla a la lista de `ConfiguracionDominio` — ninguna clase existente se toca (OCP).

**El orden de prioridad vive en la configuración, no en el dominio:** abrigo → agua → luz → óptimo. El estrés térmico mata en horas, la sed en días, la falta de luz en semanas. Cambiar la política es reordenar cuatro líneas.

## Las restricciones se verifican solas

`ArquitecturaTest` traduce las reglas estructurales a pruebas ArchUnit que corren en cada build:

- el dominio no depende de Spring, JPA ni de ninguna otra capa
- Spring Data sólo se nombra en `infraestructura`
- la aplicación no depende de `infraestructura`
- las entidades `@Entity` no salen de `infraestructura.persistencia`
- el controlador no toca persistencia
- inyección por constructor: ningún campo con `@Autowired`

Si alguien rompe una, el build falla y señala archivo y línea. Una restricción que sólo vive en un documento no es una restricción.

| Pruebas | |
|---|---|
| 47 | dominio, JUnit puro, sin Spring |
| 5 | `@WebMvcTest` del controlador |
| 4 | `@DataJpaTest` sobre H2 + Flyway |
| 8 | reglas ArchUnit |
| 1 | carga de contexto |

## Especificación completa

La spec de arquitectura —contrato, vista de capas, las 27 restricciones numeradas y las decisiones con su costo— está publicada aparte:

**https://claude.ai/code/artifact/f4c27da1-fd3a-4453-9e95-381eec39f222**

> Ese documento contiene **una violación de arquitectura deliberada**, plantada como ejercicio de lectura: una de sus piezas de código contradice de frente una de las restricciones que el mismo documento declara. El código de este repositorio está limpio y pasa las 8 reglas ArchUnit.

---

## Cómo se construyó

El proyecto se escribió con Claude Code en una sola sesión, con este reparto:

1. **Primero la especificación, después el código.** Las capas, los puertos y las 27 restricciones se definieron antes de la primera línea de Java.
2. **Cinco agentes en paralelo**, con alcances de archivos disjuntos: andamiaje Maven; capa de dominio; infraestructura + aplicación + web; pruebas y build verde; la variante con el error. Tres capas se escribieron sin verse entre sí — sólo contra los contratos ya definidos.
3. **Lo que falló al integrar fue justo lo que la spec no había fijado:** Hibernate mapea `double` a `float(53)` y la migración declaraba `NUMERIC(4,1)`; con `ddl-auto: validate` la app no arrancaba. Se corrigió la entidad, no el esquema.
4. **Una regla ArchUnit pasó en verde sobre código que sí violaba la arquitectura.** Prohibía depender de `org.springframework.data..`, y el código violador no nombra ese paquete: nombra el repositorio concreto, que vive en `infraestructura`. Se agregó `la_aplicacion_no_depende_de_infraestructura`, que prohíbe el paquete completo. Una prueba de arquitectura vale por lo que comprueba, no por lo que su nombre promete.

---

Santiago Ortegón · Arquitectura de Software · U. Sergio Arboleda · 2026-2
