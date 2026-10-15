# Corte 2, bloque 1: cuentas, administración y catálogo editable

Qué se construyó, dónde vive cada pieza y por qué, qué requerimiento cubre y qué decisiones se tomaron al implementar (con la alternativa descartada). La especificación y el registro de decisiones D1 a D19 están en los HTML del diseño; las decisiones D20 en adelante son de este documento y deben pasar al registro.

Alcance de este bloque: `Especie.activa`, CSV como datos semilla, usuarios con roles, seguridad JWT, administración de usuarios y especies, primer administrador. Fuera: plantas, mediciones, invitaciones, notificaciones, recuperación de contraseña (RF-A7/A8), macetas, front.

## 1. Cómo se ejecuta

Antes del corte 2 la aplicación arrancaba sin configuración. Ahora exige secretos por variable de entorno y **no arranca sin `FITO_JWT_SECRETO`** (a propósito: ningún secreto vive en el repositorio fuera del perfil `test`).

```bash
export FITO_JWT_SECRETO="$(openssl rand -base64 48)"   # firma de los JWT, mínimo 32 bytes
export FITO_ADMIN_CORREO="admin@tu-dominio.co"          # primer administrador,
export FITO_ADMIN_CONTRASENA="una-contrasena-larga"     # solo se usa si aún no existe ninguno
./mvnw spring-boot:run
```

Opcionales: `FITO_JWT_EXPIRACION` (ISO-8601, por defecto `PT2H`) y `FITO_BCRYPT_COSTE` (por defecto 10). Las pruebas usan el perfil `test` (`src/test/resources/application-test.yml`), el único lugar del repositorio con credenciales, y son de desarrollo.

## 2. Mapa de archivos

### dominio (sin Spring, sin cifrado, sin HTTP; lo vigilan dos reglas ArchUnit nuevas)

| Archivo | Por qué vive en dominio | Cubre |
|---|---|---|
| `modelo/Especie.java` (modificado) | Gana `boolean activa` y `desactivada()/reactivada()/conRangos()`. Que una especie esté activa es un hecho de la especie, no de un adaptador. El constructor de dos argumentos se conserva (nace activa) para no tocar a los llamadores del corte 1. | D10, RN11, RF-C5, RF-C6 |
| `puerto/CatalogoEditable.java` | Puerto de escritura del catálogo (`guardar`, `cambiarActivacion`). Separado de los dos de lectura, que no cambian de firma. | D8, RF-C3..C6 |
| `modelo/Rol.java` | Enum `CLIENTE`, `ADMINISTRADOR`. | D1, RN2 |
| `modelo/Credencial.java` | Record con el hash. Evita que contraseña en claro y hash sean ambos un `String` intercambiable; `toString` no imprime el hash. | RN10 |
| `modelo/Usuario.java` | Record inmutable: id, nombre, correo normalizado, credencial, `Set<Rol>`. El constructor garantiza que `CLIENTE` siempre está. Cada cambio devuelve una copia. | D1, RN1 (normalización), RN2 |
| `modelo/TokenDeSesion.java` | Valor opaco y caducidad. El dominio no sabe que es un JWT. | RF-A2 |
| `puerto/RepositorioUsuarios.java` | Persistencia de usuarios; incluye `contarAdministradores()`. | RN1, RN4 |
| `puerto/CifradorDeContrasena.java` | `cifrar` y `coincide`. Esconde BCrypt. | RN10, RA4 |
| `puerto/EmisorDeTokens.java` | "Dame una prueba de sesión para este usuario". Esconde la librería de tokens. No viene en el diagrama de la especificación (ver D24). | RF-A2 |
| `servicio/PoliticaDeAdministradores.java` | Servicio de dominio puro: recibe el conteo ya calculado, no consulta puertos. | RN4, D18 |
| `excepcion/*` (6 nuevas) | Errores de negocio: correo duplicado, usuario inexistente, último administrador, credenciales inválidas, contraseña actual incorrecta, especie duplicada. | RN1, RN4 |

### aplicacion

| Archivo | Qué hace | Cubre |
|---|---|---|
| `ServicioCatalogo.java` (modificado) | `listar()` filtra activas (público); `listarTodas()` devuelve todas (administración). El filtro vive aquí y no en el puerto. | RF-C1, RF-C2, D8 |
| `ServicioCuentas.java` | Registrar, iniciar sesión, consultar y editar la propia cuenta, cambiar contraseña, eliminar la propia cuenta, sembrar el primer administrador. | RF-A1, A2, A4..A6, RN1, RN4 |
| `ServicioAdministracion.java` | Listar/buscar, detalle, editar y eliminar usuarios; otorgar y revocar administrador; crear, editar, desactivar y reactivar especies. | RF-B1..B5, RF-C3..C6, RN4 |
| `OperacionesDeUsuario.java` | Pasos comunes (cargar, correo libre, editar datos) que comparten los dos servicios, porque editar datos es la misma operación sea el dueño (A4) o un administrador (B3). Visible solo en el paquete. | RF-A4, RF-B3 |

RF-A3 (cerrar sesión) no tiene caso de uso: ver D22.

### infraestructura

| Archivo | Por qué vive aquí | Cubre |
|---|---|---|
| `persistencia/AdaptadorEspecieJpa.java` (modificado) | Ahora implementa los tres puertos del catálogo y es el único adaptador de producción; ya no es condicional. Devuelve también las inactivas. | D14, D10 |
| `persistencia/EspecieEntity`, `MapeadorEspecie` (modificados) | Columna `activa` y camino de escritura (`volcarEn`). | D10 |
| `persistencia/UsuarioEntity`, `UsuarioJpaRepository`, `MapeadorUsuario`, `AdaptadorUsuarioJpa` | Adaptador de `RepositorioUsuarios`. Traduce la violación del constraint del correo a `CorreoYaRegistradoException`. | RN1 |
| `referencia/LectorDeEspeciesCsv.java` (antes `AdaptadorEspeciesCsv`) | Solo lee el CSV a objetos de dominio; ya no implementa puertos. | D14 |
| `referencia/SembradorDeEspecies.java` | `ApplicationRunner`: si el catálogo está vacío, copia el CSV a la base por los puertos. | D14 |
| `seguridad/CifradorBcrypt.java` | Único archivo que nombra `org.springframework.security.crypto`. | RN10 |
| `seguridad/ConfiguracionJwt.java`, `EmisorDeTokensJwt.java` | Firma y valida con HS256; el emisor implementa `EmisorDeTokens`. | RF-A2 |
| `arranque/SembradorDeAdministrador.java` | `ApplicationRunner` que llama a `ServicioCuentas.asegurarAdministradorInicial` con las variables de entorno. Es un adaptador de entrada, como un controlador. | Datos semilla |
| `configuracion/ConfiguracionDominio.java` (modificado) | Ensambla `PoliticaDeAdministradores` (el dominio no lleva anotaciones). | D18 |
| `db/migration/{h2,postgresql}/V4__especie_activa.sql` | Columna `activa` y tabla vaciada para que el CSV la siembre (ver D25). | D10, D14 |
| `db/migration/{h2,postgresql}/V5__usuario.sql` | Tablas `usuario` (correo único) y `usuario_rol`. | D1, RN1 |

### web

| Archivo | Qué hace | Cubre |
|---|---|---|
| `ConfiguracionSeguridad.java` | La cadena de filtros: qué es público, qué exige sesión, qué exige rol. | D6, RF-A* |
| `ManejadorDeErroresDeSeguridad.java` | `AuthenticationEntryPoint` + `AccessDeniedHandler`: 401 y 403 con `ErrorRespuesta` JSON. | RA1 |
| `ConvertidorDeJwt.java` | Del JWT ya verificado a la identidad de la petición; lee los roles de la base (D21). | RN2 |
| `ConfiguracionContenido.java` | La API responde JSON sin importar `Accept` (D29). | RA1 |
| `ConfiguracionCors.java` (modificado) | Permite `PUT`, `DELETE` y la cabecera `Authorization`. | RA7 |
| `AutenticacionController`, `CuentaController` | `POST /api/v1/auth/registro`, `POST /api/v1/auth/sesion`; `GET/PUT/DELETE /api/v1/cuenta`, `PUT /api/v1/cuenta/contrasena`. | RF-A1..A6 |
| `AdministracionUsuariosController` | `/api/v1/admin/usuarios` (listar con `?q=`, detalle, editar, eliminar, `PUT/DELETE .../rol-administrador`). | RF-B1..B5 |
| `AdministracionEspeciesController` | `/api/v1/admin/especies` (listar todas, crear, editar, `POST .../desactivar`, `POST .../reactivar`). Sin `DELETE`. | RF-C2..C6 |
| `IdentidadDelSolicitante.java` | De `Authentication` al `UUID` que necesita la aplicación. | RA4 |
| `ManejadorGlobalDeErrores.java` (modificado) | Traduce las excepciones nuevas: 409 correo duplicado, último administrador y especie duplicada; 404 usuario; 401 credenciales; 400 contraseña actual o dato inválido. | RA6 |
| `dto/*` (9 nuevos) | Validación de forma en el borde y conversión a dominio. Ninguno expone credencial ni hash. | RA5, RA6, RN10 |

## 3. Decisiones técnicas tomadas al implementar (candidatas a D20 en adelante)

**D20. La sesión es un JWT bearer sin estado en el servidor, firmado con HS256.** `spring-boot-starter-oauth2-resource-server` valida firma, caducidad y emisor; el secreto sale de `FITO_JWT_SECRETO` y sin él no arranca. Descartado: sesión HTTP con cookie, que obliga a CSRF, a cookies entre orígenes distintos (el front vive en otro origen) y a guardar estado en el servidor. Descartado: RS256, porque quien firma y quien valida es el mismo proceso y no hay tercero que deba verificar sin poder firmar. Costo: una clave compartida; si se filtra, se puede forjar cualquier token.

**D21. El token solo prueba quién es; los roles se leen de la base en cada petición.** `EmisorDeTokensJwt` firma solo `sub` (el id), `iss`, `iat`, `exp`. `ConvertidorDeJwt` consulta al usuario y deriva los roles. Así, revocar el rol (RF-B5) o eliminar la cuenta (RF-B4, RF-A6) surte efecto en la siguiente petición, con el mismo token. Prueba: `FlujoDeCuentasIntegracionTest.otorgarYRevocarElRolSurteEfectoAlInstanteConElMismoToken`. Descartado: roles dentro del token, que dejaría a un administrador revocado con poderes hasta que caduque (hasta 2 h). Costo: una consulta por petición autenticada.

**D22. Cerrar sesión (RF-A3) es que el front descarte el token; no hay endpoint.** Descartado: lista de revocación (tabla o caché de `jti` invalidados que el filtro consulte). Reintroduce estado en el servidor, que es justo lo que D20 evita, y D21 ya cubre el caso grave (cuenta eliminada o rol revocado se rechazan al instante). Lo que no cubre: un token robado de una cuenta viva sigue siendo válido hasta caducar (2 h por defecto, configurable). Si el riesgo no es aceptable, el paso siguiente es la lista de revocación, y se agrega solo en `ConvertidorDeJwt` (o un validador del decoder), sin tocar dominio ni servicios.

**D23. BCrypt detrás del puerto `CifradorDeContrasena`, coste configurable.** Solo `CifradorBcrypt` nombra la librería. Descartado: usar `PasswordEncoder` de Spring directamente en `ServicioCuentas`, que mete una librería de seguridad en aplicación y rompe la regla ArchUnit de RA4. La contraseña se limita a 72 caracteres en el borde: BCrypt solo usa los primeros 72 bytes y aceptar más daría una falsa sensación de seguridad.

**D24. Puerto nuevo `EmisorDeTokens` (no está en el diagrama de la especificación).** `ServicioCuentas.iniciarSesion` necesita entregar una prueba de sesión; sin puerto tendría que importar `JwtEncoder`. Descartado: emitir el token en el controlador (obligaría a que el controlador conozca la política de qué lleva un token y a duplicarla si otro adaptador de entrada inicia sesión). La validación del token por petición no pasa por el puerto: es un filtro del borde HTTP, no un caso de uso.

**D25. El CSV se siembra vaciando antes lo que sembró SQL.** V2 y V3 ya habían insertado las cinco especies desde SQL. Con ellas, la tabla nunca está vacía y `SembradorDeEspecies` (D14, "cargar si está vacía") sería código muerto. `V4` agrega `activa` y hace `DELETE FROM especie`; el primer arranque las recarga del CSV. Es seguro hoy porque nada referencia a `especie` todavía; no se pueden editar V2/V3 (Flyway rechaza migraciones modificadas). Descartado: dejar V3 y que el sembrador sea inalcanzable. Descartado también: insertar solo las especies que falten, porque cambia la regla "si está vacía" y, ante una especie ausente, no hay forma de saber si el administrador la quitó o nunca existió.

**D26. Primer administrador por `ApplicationRunner` y variables de entorno.** Si no hay ningún administrador y faltan `FITO_ADMIN_CORREO`/`FITO_ADMIN_CONTRASENA`, la aplicación no arranca (mejor eso que un sistema que nadie puede administrar). Si ya hay un administrador, las variables no hacen falta. Si el correo ya es de un cliente, se le asciende conservando su contraseña. Descartado: endpoint `/setup` (deja una ventana abierta hasta que alguien lo use) y una migración con la contraseña (credencial en el repositorio).

**D27. Las especies no tienen verbo `DELETE`; desactivar y reactivar son acciones `POST`.** `POST /admin/especies/{nombre}/desactivar`. Un `DELETE` en la API prometería lo que D10 prohíbe. Descartado: `PUT .../activa` con cuerpo: más genérico, pero en el registro de peticiones se lee peor que "desactivar". Hay una prueba de que `DELETE` da 405.

**D28. El rol de administrador es un subrecurso, no un campo editable.** `PUT/DELETE /admin/usuarios/{id}/rol-administrador`. Tiene una regla propia (RN4) y no debe poder colarse en una edición de nombre y correo. Descartado: `roles` dentro del cuerpo de `PUT /admin/usuarios/{id}`.

**D29. La API ignora `Accept` y siempre responde JSON (`ConfiguracionContenido`).** Con la negociación por defecto, `Accept: text/html` a un endpoint que devuelve un objeto terminaba en 500 (`HttpMediaTypeNotAcceptableException`); el corte 1 lo arreglaba fijando `Content-Type` en cada `ResponseEntity`. Con una decena de endpoints nuevos eso es una regla que se olvida. Descartado: repetir el `contentType(...)` por endpoint. Costo: ningún endpoint ofrece otro formato por `Accept`; si hiciera falta (exportar CSV), se declara con `produces` en ese endpoint.

**D30. Códigos HTTP de los errores nuevos.** 409 para conflicto con el estado (correo duplicado, último administrador, especie duplicada). Login fallido: 401 con un solo código y mensaje para "correo inexistente" y "contraseña errónea" (no se enumeran correos). Contraseña actual incorrecta al cambiarla: **400**, no 401, porque el usuario SÍ está autenticado y un 401 haría que el front lo mandara a iniciar sesión otra vez. Registro devuelve el usuario (201), no un token: iniciar sesión es un paso aparte.

**D31. Una especie nueva o editada exige las tres magnitudes y rangos dentro del rango físico del sensor.** Lo valida `ServicioAdministracion`, no `Especie`: los tests del núcleo construyen especies parciales y el RF-C3 pide "un rango para cada magnitud" solo al administrar. Descartado: endurecer `Especie`, que rompería pruebas y llamadores del corte 1 por una regla de administración.

**D32. Solo el perfil `test` trae credenciales.** `application.yml` enlaza `${FITO_JWT_SECRETO}` sin valor por defecto; `application-test.yml` (en `src/test/resources`) trae valores de desarrollo y BCrypt con coste 4. Todo `@SpringBootTest` declara `@ActiveProfiles("test")`.

**D33. `CatalogoEditable.guardar` es un upsert; decidir crear o editar es de la aplicación.** El servicio consulta antes `RangosPorEspecie` (que ve también inactivas) para responder 409 al crear un nombre existente y 404 al editar uno inexistente. El nombre es la clave y no se renombra (`volcarEn` solo lo escribe en entidades nuevas).

**D34. `ConvertidorDeJwt` no es un bean.** Spring MVC registra todo bean `Converter` como conversor de parámetros; este no debe serlo. Lo instancia `ConfiguracionSeguridad`. Consecuencia: las pruebas de rebanada web necesitan un `ServicioCuentas` (ver `SeguridadDePruebas`).

## 4. Límites conocidos (decirlos antes de que los encuentren)

- **RN4 con dos administradores concurrentes.** `contarAdministradores()` y la revocación no están bajo un bloqueo: dos administradores que se revocan mutuamente en el mismo instante podrían dejar cero. La salida es un bloqueo pesimista sobre las filas de administrador o aislamiento serializable en esos dos métodos. No se hizo porque exigiría una consulta específica de JPA en el puerto; está cubierto por la regla, no por la concurrencia.
- **`GET /api/v1/especies` conserva `Cache-Control: max-age=300`.** Una especie desactivada puede seguir viéndose hasta 5 minutos en cachés del cliente.
- **Un token robado de una cuenta viva** sigue sirviendo hasta caducar (D22).
- **Tiempo de respuesta del login** distinto entre correo inexistente (sin BCrypt) y existente (con BCrypt): un atacante paciente puede enumerar correos por temporización. No se mitigó (habría que hacer un hash simulado).
- **`FITO_JWT_SECRETO` simétrico**: rotarlo invalida todas las sesiones a la vez.
- ~~RF-B2 devuelve solo el usuario; sus plantas se agregan con el módulo de plantas.~~ Resuelto en el bloque 3 (D47).

## 5. Pruebas

Antes del bloque: 113. Después: **294** (`./mvnw verify`).

| Capa | Pruebas nuevas relevantes |
|---|---|
| Dominio (JUnit puro) | `UsuarioTest`, `CredencialTest`, `PoliticaDeAdministradoresTest`, ampliación de `EspecieTest` |
| Aplicación (dobles en memoria, sin Spring) | `ServicioCuentasTest`, `ServicioAdministracionTest`, ampliaciones de `ServicioCatalogoTest` y `ServicioDiagnosticoTest` (especie inactiva sigue diagnosticable) |
| Contrato de puertos | `ContratoFuenteDeEspeciesTest` cubre lectura y escritura; lo pasan `AdaptadorEspecieJpaTest` y el doble `EspeciesEnMemoriaTest` |
| Infraestructura | `AdaptadorUsuarioJpaTest`, `CifradorBcryptTest`, `EmisorDeTokensJwtTest` (caducado, otra clave, manipulado), `LectorDeEspeciesCsvTest` (incluye el sembrador), `SembradorDeAdministradorTest` |
| Web (MockMvc) | `SeguridadWebTest` (401 JSON sin token, 403 JSON con rol insuficiente, rutas públicas siguen públicas, no-HTML), `CuentasControllerTest`, `AdministracionControllerTest`, `ConvertidorDeJwtTest`, `ConfiguracionCorsTest` ampliado |
| Contenedor real | `SinHtmlTest` extendido a 401/403 y rutas nuevas; `FlujoDeCuentasIntegracionTest` (17 casos de punta a punta) |
| ArchUnit | 10 reglas: las 8 originales sin modificar y 2 nuevas |

Las dos reglas ArchUnit nuevas se comprobaron con una violación deliberada (una clase temporal en `dominio` que importaba `BCryptPasswordEncoder`): fallan. `scripts/probar-dominio-sin-infraestructura.sh` sigue pasando.

Si una prueba de integración nueva deja filas en la H2 compartida, rompe el contrato del catálogo (que espera exactamente 5 especies); por eso `FlujoDeCuentasIntegracionTest` usa prefijo `flujo-` y limpia en `@AfterEach`.

## 6. Preguntas de mutación: "si mañana cambia X, ¿qué archivos tocas?"

- **Cambia BCrypt por Argon2.** Una clase nueva en `infraestructura/seguridad` que implementa `CifradorDeContrasena`, y quitar `@Component` de `CifradorBcrypt`. No se tocan dominio, aplicación ni web. Ojo: los hashes ya guardados son BCrypt; hay que verificar con ambos o forzar cambio de contraseña. Prueba: contrato análogo a `CifradorBcryptTest`.
- **Cambia JWT por sesión con cookie.** `EmisorDeTokensJwt` y `ConfiguracionJwt` (se reemplazan), `ConfiguracionSeguridad` (CSRF, `sessionManagement`, quitar `oauth2ResourceServer`), `ConvertidorDeJwt`, `ConfiguracionCors` (`allowCredentials`). Dominio y aplicación no cambian; `EmisorDeTokens` desaparece o devuelve el id de sesión.
- **Aparece un tercer rol.** `Rol`, una migración `V6` que amplíe el `CHECK` de `ck_usuario_rol`, y la regla correspondiente en `ConfiguracionSeguridad`. `Usuario` solo si el rol necesita operaciones propias.
- **La regla pasa a "al menos dos administradores".** Una línea en `PoliticaDeAdministradores` y su prueba. Los dos servicios no cambian: esa es la razón de D18.
- **Se agrega la magnitud pH.** `Magnitud`, `SolicitudDiagnostico`, `DiagnosticoController`, `ManejadorGlobalDeErrores` (`ORDEN_CAMPOS`), `EspecieEntity`, `MapeadorEspecie` (en `aDominio` y en `volcarEn`), una migración `V6` en cada vendor (con `DEFAULT` para las filas existentes), `LectorDeEspeciesCsv` (`PREFIJO_COLUMNA`) y `especies.csv`: 10 archivos. No se tocan `SolicitudRangos`, `ServicioAdministracion` ni `RespuestaEspecie*`, que recorren `Magnitud.values()`. Antes del corte 2 el modo CSV y el modo BD contaban aparte; con un solo adaptador la cuenta es esta.
- **El CSV vuelve a ser fuente en caliente.** No se puede sin romper el módulo C: editar y desactivar son escrituras y el CSV es de solo lectura (D14).
- **Otro adaptador de entrada inicia sesión (por ejemplo WebSocket de la maceta).** Llama a `ServicioCuentas` o a un servicio nuevo; `EmisorDeTokens` ya existe. El dominio no se entera.
- **Se agrega un `GET /api/v1/diagnosticos` con historial.** Queda protegido sin hacer nada, porque solo el `POST` es público. Con `permitAll()` sobre la ruta entera quedaría público por descuido: por eso los matchers llevan método.
- **¿Por qué `usuario_rol` y no una columna `es_admin`?** Un tercer rol es una fila nueva y no un cambio de esquema del usuario; la `CHECK` es el único punto a ampliar.
- **¿Cómo prueba ArchUnit que el dominio no usa BCrypt?** `el_dominio_no_conoce_seguridad_ni_cifrado` prohíbe dependencias de `org.springframework.security..`, `..crypto..`, `org.bouncycastle..` y `com.nimbusds..` desde `..dominio..`.


---

# Corte 2, bloque 3: plantas, mediciones manuales, cambio de estado y cascadas

Qué se construyó, dónde vive cada pieza y por qué, qué requerimiento cubre y qué decisiones se tomaron (D35 en adelante, con la alternativa descartada). Misma convención del bloque 1: puertos en `dominio.puerto`, el "quién soy" llega como `UUID` desde `IdentidadDelSolicitante`, errores con `ErrorRespuesta` desde `ManejadorGlobalDeErrores`, migraciones nuevas en ambos dialectos, perfil `test` para los `@SpringBootTest`.

Alcance: `Planta`, `RepositorioPlantas`, `PoliticaDeAcceso`, `RepositorioAccesos` (implementado y probado, sin endpoints de compartir), mediciones manuales con historial (`DiagnosticoDePlanta`), `DetectorDeCambioDeEstado`, `Notificador` con un adaptador de log, y las cascadas de eliminación (planta y cuenta). Fuera: invitaciones y endpoints de compartir, recuperación de contraseña, correo, WebSocket, macetas, front. El núcleo no se tocó: `Medicion`, `Lectura`, `Diagnostico`, `EvaluadorDeEstado` y `ReglaPorDesviacion` están intactos (y una prueba de `DiagnosticoDePlantaTest` afirma que `Diagnostico` y `Medicion` siguen sin campo de origen ni de planta, D17).

## 1. Cómo se prueba a mano

No hay variables nuevas. Con la aplicación arrancada como en la sección 1 del bloque 1:

```bash
U=http://localhost:8080/api/v1; H='Content-Type: application/json'
curl -s -X POST $U/auth/registro -H "$H" -d '{"nombre":"Ana","correo":"ana@vivero.co","contrasena":"clave-larga-1"}'
T=$(curl -s -X POST $U/auth/sesion -H "$H" -d '{"correo":"ana@vivero.co","contrasena":"clave-larga-1"}' | jq -r .token)
ID=$(curl -s -X POST $U/plantas -H "$H" -H "Authorization: Bearer $T" -d '{"apodo":"Pepa","especie":"potos"}' | jq -r .id)
curl -s $U/plantas/$ID/diagnostico -H "Authorization: Bearer $T"                       # hayDiagnostico=false (RN9)
curl -s -X POST $U/plantas/$ID/mediciones -H "$H" -H "Authorization: Bearer $T" -d '{"humedad":55,"luz":700,"temperatura":22}'
curl -s -X POST $U/plantas/$ID/mediciones -H "$H" -H "Authorization: Bearer $T" -d '{"humedad":5,"luz":700,"temperatura":22}'
curl -s "$U/plantas/$ID/diagnosticos?pagina=0&tamano=10" -H "Authorization: Bearer $T"  # historial, más reciente primero
```

La segunda medición cambia el estado (SALUDABLE a CRITICO) y deja una línea `aviso CAMBIO_DE_ESTADO ...` en el registro de la aplicación (el único `Notificador` de este bloque). Este recorrido también se corrió contra un PostgreSQL 16 real (contenedor) con el perfil `postgres`: las migraciones V6 y V7 aplican, Hibernate valida el esquema y el flujo completo (crear, medir dos veces, paginar, ajeno 404, borrar planta, borrar cuenta) responde igual que en H2. No hay una prueba automática contra PostgreSQL (no hay Testcontainers en el proyecto): fue una verificación manual.

## 2. Mapa de archivos

### dominio (sin Spring, sin JPA, sin HTTP; las reglas ArchUnit viejas y dos nuevas lo vigilan)

| Archivo | Por qué vive en dominio | Cubre |
|---|---|---|
| `modelo/Planta.java` | Record inmutable: `id`, `duenoId`, `apodo`, `Especie`, `registradaEn`. Guarda el id del dueño y no al `Usuario` (D19). La RN11 vive en las fábricas `nueva` y `conEspecie`, no en el constructor, para poder reconstruir plantas cuya especie se descontinuó (D42). | RF-D1, RF-D4, RN6, RN7, RN11, D19 |
| `modelo/DiagnosticoDePlanta.java` | Envuelve un `Diagnostico` con `plantaId`, `OrigenDeMedicion` y `registradoEn`. `Diagnostico` y `Medicion` no ganan ningún campo. | RF-F3, D5, D17 |
| `modelo/OrigenDeMedicion.java` | `MANUAL`, `DISPOSITIVO`. El origen es del envoltorio, no de la medición. | RN20, D17 |
| `modelo/AccesoCompartido.java` | Planta, invitado y fecha: el permiso de lectura de un invitado. | D2, D19 |
| `modelo/Aviso.java`, `modelo/TipoDeAviso.java` | Qué se avisa, a quién y con qué texto, sin canal. El enum trae los seis tipos de la especificación aunque este bloque solo emite dos. | RF-G1, RF-G3, D43 |
| `modelo/Paginacion.java`, `modelo/Pagina.java` | Qué página se pide y qué página se devuelve. Propios del dominio para no meter `Pageable` de Spring Data en un puerto (D44). | RF-F5, DA6 |
| `puerto/RepositorioPlantas.java` | `buscar`, `deDueno`, `compartidasCon`, `guardar`, `eliminar`. | RF-D, RN3, RN7 |
| `puerto/RepositorioAccesos.java` | `invitadosDe`, `otorgar`, `revocar`, más `revocarTodosDe` y `revocarTodosDelInvitado` para las cascadas. | D19, RN12 |
| `puerto/RepositorioDiagnosticos.java` | `ultimoDe`, `historialDe` paginado, `guardar`, `eliminarDe`. Sin "actualizar": el historial solo se agrega. | RF-F4, RF-F5, D3 |
| `puerto/Notificador.java` | `avisar(Aviso)`. El dominio no sabe si hay log, correo o WebSocket. | RF-G1, RF-G3 |
| `servicio/PoliticaDeAcceso.java` | Función pura: `puedeLeer(usuario, planta, invitados)` y `puedeModificar(usuario, planta)`. No llama puertos (lo vigila una regla ArchUnit). | RN3, RN5, D19 |
| `servicio/DetectorDeCambioDeEstado.java` | `huboCambio(anterior, nuevo)`: compara solo el `EstadoGlobal`; el primer diagnóstico no cuenta (D40). | RN8, RF-G1 |
| `excepcion/PlantaNoEncontradaException`, `OperacionNoPermitidaException`, `EspecieInactivaException` | Errores de negocio nuevos. La primera cubre "no existe" y "no es tuya" a propósito (D35). | RN3, RN5, RN11 |

### aplicacion

| Archivo | Qué hace | Cubre |
|---|---|---|
| `ServicioPlantas.java` | Registrar, listar propias, detalle, editar, eliminar. | RF-D1..D5 |
| `ServicioMediciones.java` | `registrarManual` (autoriza al dueño) y `registrar(plantaId, medicion, origen)` (el caso de uso). Evalúa, guarda, detecta el cambio y avisa. Es el único camino que escribe en el historial. | RF-F1, RF-F3, RF-G1, RN5, RN8, D17, D41 |
| `ServicioConsultaDeDiagnosticos.java` | `vigente` y `historial`, todo `readOnly`. No recibe evaluador ni notificador, y una regla ArchUnit lo impide. | RF-F4, RF-F5, RN9, D3, D38 |
| `CascadaDeEliminacion.java` | Historial, accesos, planta y aviso `ACCESO_PERDIDO`; la usan eliminar planta, eliminar cuenta propia y eliminar usuario por el administrador. | RN12, D11, RF-G3, D37 |
| `AutorizadorDePlantas.java` | Paso común "cargar la planta y comprobar permiso" (paquete): trae los invitados del puerto y se los da a `PoliticaDeAcceso`. | RN3, RN5, D19, D36 |
| `ServicioCuentas.java`, `ServicioAdministracion.java` (modificados) | Reciben la cascada. `detalleDeUsuario` devuelve ahora un `DetalleDeUsuario` con las plantas. | RF-A6, RF-B2, RF-B4 |

### infraestructura

| Archivo | Por qué vive aquí | Cubre |
|---|---|---|
| `persistencia/PlantaEntity`, `PlantaJpaRepository`, `MapeadorPlanta`, `AdaptadorPlantaJpa` | Adaptador de `RepositorioPlantas`. La planta lleva los rangos vigentes de su especie (se arma con `MapeadorEspecie`). | RF-D, RN11 |
| `persistencia/AccesoId`, `AccesoEntity`, `AccesoJpaRepository`, `AdaptadorAccesoJpa` | Adaptador de `RepositorioAccesos`. Clave compuesta (planta, invitado); otorgar y revocar son idempotentes. | D19 |
| `persistencia/DiagnosticoDePlantaEntity`, `ParametroEmbebido`, `DiagnosticoDePlantaJpaRepository`, `MapeadorDiagnosticoDePlanta`, `AdaptadorDiagnosticoJpa` | Adaptador de `RepositorioDiagnosticos`. `Pageable` de Spring Data solo aparece aquí. El diagnóstico se reconstruye desde su propia fila (D48). | RF-F4, RF-F5, D39 |
| `notificacion/NotificadorEnRegistro.java` | Único adaptador de `Notificador` en este bloque: una línea de log por aviso. | RF-G1, RF-G3 |
| `configuracion/ConfiguracionDominio.java` (modificado) | Ensambla `PoliticaDeAcceso` y `DetectorDeCambioDeEstado`. | D19 |
| `db/migration/{h2,postgresql}/V6__planta.sql` | `planta` y `acceso_compartido`; claves foráneas sin `ON DELETE CASCADE` (D37). | RN6, RN7, RN12 |
| `db/migration/{h2,postgresql}/V7__diagnostico_de_planta.sql` | `diagnostico_planta` más tablas hijas `diagnostico_parametro` y `diagnostico_recomendacion`; índice (planta, fecha desc). | D39, DA6 |

### web

| Archivo | Qué hace | Cubre |
|---|---|---|
| `PlantasController.java` | `POST/GET /api/v1/plantas`, `GET/PUT/DELETE /api/v1/plantas/{id}`. | RF-D1..D5 |
| `MedicionesController.java` | `POST /api/v1/plantas/{id}/mediciones`: adaptador de entrada humano del caso de uso. | RF-F1, RN5 |
| `DiagnosticosDePlantaController.java` | `GET .../diagnostico` (vigente) y `GET .../diagnosticos?pagina=&tamano=` (historial). Solo `GET`. | RF-F4, RF-F5, D3 |
| `AdministracionUsuariosController.java` (modificado) | El detalle de usuario devuelve también el resumen de sus plantas. | RF-B2 |
| `ManejadorGlobalDeErrores.java` (modificado) | 404 `PLANTA_NO_ENCONTRADA`, 403 `OPERACION_NO_PERMITIDA`, 409 `ESPECIE_DESCONTINUADA`. | RA1, RA6 |
| `dto/SolicitudPlanta`, `SolicitudMedicion`, `RespuestaPlanta`, `RespuestaDiagnosticoDePlanta`, `RespuestaDiagnosticoVigente`, `RespuestaPagina`, `RespuestaDetalleDeUsuario` | Validación de forma y conversión a dominio en el borde (`SolicitudMedicion.aMedicion()`). | RA5, RA6 |

El endpoint suelto `POST /api/v1/diagnosticos` (`DiagnosticoController`, `ServicioDiagnostico`, sus DTO) no se tocó (D6); una prueba de `SinHtmlTest` confirma que sigue público.

## 3. Decisiones técnicas (D35 en adelante)

**D35. Una planta ajena sin permiso responde 404, igual que una inexistente.** `PlantaNoEncontradaException` cubre los dos casos y el cuerpo es idéntico (la prueba de integración compara los dos cuerpos byte a byte, salvo el id). Con un 403, cualquiera con un token podría recorrer UUID y descubrir cuáles plantas existen, y cuáles son de quién. El costo es que un cliente legítimo que se equivoca de id no sabe si fue de id o de permiso; para este sistema es el costo correcto. Descartado: 403 para el ajeno. Es más explícito y es lo que muchos esperan, pero filtra existencia. El 403 se conserva para el invitado que intenta una operación de dueño (D36), que ya sabe que la planta existe. Cubre RN3.

**D36. La autorización vive en la aplicación (`AutorizadorDePlantas` + `PoliticaDeAcceso`), no en el controlador ni en anotaciones.** El borde web solo sabe de rol (`ConfiguracionSeguridad`: `/admin/**`); "¿es esta planta tuya?" depende del dato, y se decide en un servicio de dominio puro que se prueba con dos objetos (D19). `AutorizadorDePlantas` junta lo que la política no hace por diseño (traer los invitados del puerto). Resultado: ajeno 404, invitado 403 en lo que solo hace el dueño, dueño todo. Un administrador no es dueño de plantas ajenas: tampoco las ve (hay una prueba). Descartado: `@PreAuthorize` con expresiones sobre la planta, que obliga a que Spring Security conozca el repositorio y saca la regla del dominio; y comprobar en cada controlador, que es la regla que se olvida en el tercer endpoint.

**D37. La cascada es código de la aplicación (`CascadaDeEliminacion`) y las claves foráneas NO tienen `ON DELETE CASCADE`.** Eliminar una planta no es solo borrar filas: hay que leer los invitados antes de quitar los accesos y avisarles (RF-G3), y eso es una regla de negocio, probable con dobles. La base queda como red de seguridad inversa: si un camino nuevo olvida la cascada, la clave foránea hace fallar el borrado en una prueba en vez de dejar huérfanos (hay pruebas de adaptador que lo demuestran). Es el mismo razonamiento de D18. Excepción deliberada: las tablas hijas del diagnóstico (`diagnostico_parametro`, `diagnostico_recomendacion`) sí caen por `ON DELETE CASCADE`, porque son partes del diagnóstico y no agregados, y permite borrar el historial de una planta con decenas de miles de filas en una sola sentencia. Descartado: `ON DELETE CASCADE` en todo; sería correcto en los datos pero invisible en el dominio y no avisaría a nadie. También descartado: dos servicios con su propia copia de la secuencia (planta, cuenta propia, baja por administrador son tres caminos; la cascada tiene un solo dueño).

**D38. Consultar y registrar son dos servicios (`ServicioConsultaDeDiagnosticos` y `ServicioMediciones`), y una regla ArchUnit lo hace cumplir.** D3 ("consultar nunca escribe") deja de ser una promesa: el servicio de consulta no recibe evaluador, detector ni notificador, todo es `readOnly`, y la regla `consultar_diagnosticos_no_puede_producir_ni_avisar` falla si alguien se los agrega (se comprobó con una violación deliberada). Descartado: un único `ServicioDiagnosticosDePlanta` con `registrar`, `vigente` e `historial`; menos clases, pero entonces D3 depende de la disciplina de quien lo edite.

**D39. El diagnóstico se guarda en una fila con tablas hijas tipadas, no en una columna JSON.** `diagnostico_planta` (estado, origen, fechas, nombre de la especie al diagnosticar), `diagnostico_parametro` (una fila por magnitud con valor, rango óptimo y clasificación) y `diagnostico_recomendacion` (texto con su orden). Razones: H2 y PostgreSQL tratan distinto el JSON (`JSON`, `JSONB`) y habría dos migraciones con semántica distinta; y el plan de evolución pide la serie temporal de cada magnitud, que con filas tipadas es una consulta SQL. Costo: tres tablas y un mapeador que arma y desarma. Descartado: una columna JSON con el `Diagnostico` serializado, más corta pero ilegible para SQL y atada a Jackson; y una sola tabla con columnas fijas por magnitud (`humedad_valor`, `humedad_min`, ...), que reintroduce el defecto que el enum `Magnitud` evita (agregar pH sería una migración de columnas). La carga de las colecciones es `EAGER` con `SUBSELECT` (en vez de un `LAZY`, que fallaría fuera de la transacción con `open-in-view` desactivado) para que las colecciones de una página de historial se traigan en una consulta por colección y no una por fila; no se midió el número de consultas, es la intención del mapeo.

**D40. El primer diagnóstico de una planta no cuenta como cambio de estado.** No hay estado anterior contra el cual cambiar (RN8 compara contra "el inmediatamente anterior"), y avisar "pasó de nada a SALUDABLE" entrena a ignorar los avisos. Que existe un diagnóstico nuevo se avisa por otro camino (`DIAGNOSTICO_NUEVO`, RF-G2, bloque de WebSocket). Descartado: contarlo como cambio cuando nace en un estado distinto de SALUDABLE; es una segunda regla con su propia justificación que nadie pidió. Si se decide al revés, es una línea en `DetectorDeCambioDeEstado` y su prueba, y `ServicioMediciones` debe cambiar el texto del aviso (hoy asume que hay anterior).

**D41. El caso de uso tiene dos puertas: `registrarManual(solicitante, plantaId, medicion)` y `registrar(plantaId, medicion, origen)`.** La primera autoriza al dueño (RN5) y delega en la segunda con `MANUAL`. La segunda no autoriza: quien la llame responde de haber autenticado el origen (la sesión del dueño hoy; la credencial de dispositivo, RN19, en el bloque de macetas). Una maceta no es un usuario y no tiene `UUID` de solicitante que comprobar. Costo: un método público que no autoriza; está documentado en el javadoc y solo lo usa `registrarManual` hoy. Descartado: un único método con el origen y un `solicitante` nulo para dispositivos, que mezcla dos modelos de identidad en un parámetro opcional; y comprobar la autoría en el controlador, que sacaría RN5 de la aplicación.

**D42. `Planta` guarda la `Especie` completa y la RN11 vive en sus fábricas.** `Planta.nueva` y `conEspecie` rechazan una especie inactiva; el constructor canónico no, porque es el que usa el adaptador para leer de la base y una planta cuya especie se descontinuó después debe poder reconstruirse. Editar sin cambiar de especie no exige que esté activa (si no, el dueño no podría ni cambiar el apodo de su planta, contra la RN11); cambiarla por otra distinta, sí. La especie que lleva es la vigente: si el administrador edita los rangos, las plantas lo reflejan en su siguiente diagnóstico. Descartado: guardar solo el nombre de la especie y que cada servicio la busque (la RN11 quedaría en la aplicación y se repetiría), y copiar los rangos a la planta (se desincronizan al editar la especie, RF-C4).

**D43. Los avisos salen al final del método, dentro de la transacción, y este bloque solo emite `CAMBIO_DE_ESTADO` y `ACCESO_PERDIDO`.** El enum trae los seis tipos de la especificación, pero `DIAGNOSTICO_NUEVO` (RF-G2) es la vista en vivo y emitirlo hoy solo llenaría el registro de ruido; lo emite el bloque de WebSocket. Destinatarios de `CAMBIO_DE_ESTADO`: dueño más invitados (RF-G1); de `ACCESO_PERDIDO`: solo los invitados, y no se emite si no había ninguno. Limitación aceptada: si la transacción se revierte después de avisar, el aviso ya salió (ver sección 4). Descartado por ahora: `@TransactionalEventListener(AFTER_COMMIT)`, que resuelve eso pero mete eventos de Spring en la aplicación; tiene sentido cuando el canal sea correo, que sí tiene un costo de aviso fantasma.

**D44. La paginación es del dominio (`Paginacion`, `Pagina`) y se pide desde el principio.** Páginas desde 0, tamaño de 1 a 100 (20 por defecto); fuera de eso, 400 `PARAMETRO_INVALIDO`. Una maceta que reporta cada quince minutos son unos 35 000 diagnósticos por planta y año (DA6): devolver todo no es una opción que se pueda dejar para después. `Pageable` de Spring Data solo existe en `AdaptadorDiagnosticoJpa`. Se conserva todo el historial (la opción "conservar todo" de DA6); acotarlo después es una decisión de retención que no cambia el puerto. Descartado: `Pageable` en el puerto (rompe RA4); paginación por cursor (más eficiente a escala, innecesaria hoy y más difícil de explicar); y devolver la lista completa con `?limite=`.

**D45. Una planta sin diagnóstico responde 200 con `hayDiagnostico=false`, `diagnostico=null` y un mensaje.** RN9: no es un error, es un estado válido, y el front lo distingue por un booleano explícito y no por un código de error ni por adivinar si el objeto vino vacío. Descartado: 404 (confunde "no hay planta" con "no hay diagnóstico") y 204 sin cuerpo (no da el mensaje y obliga al front a tratarlo aparte del 200).

**D46. Códigos de los errores nuevos.** `POST .../mediciones` responde 201 con el diagnóstico que quedó en el historial (se creó un registro). `POST /plantas` responde 201 con `Location`. `ESPECIE_DESCONTINUADA` es 409 por el mismo criterio de D30 (la petición está bien formada y choca con el estado del catálogo); una especie inexistente conserva el 404 `ESPECIE_NO_SOPORTADA` del endpoint de diagnóstico suelto. `OPERACION_NO_PERMITIDA` es 403 (el invitado). Editar es `PUT` con el cuerpo completo (apodo y especie), igual que la cuenta; descartado `PATCH`, que obliga a distinguir "no enviado" de "nulo" sin necesidad con dos campos.

**D47. RF-B2: el detalle de usuario trae un resumen de sus plantas, no su contenido.** `GET /api/v1/admin/usuarios/{id}` devuelve el usuario y `plantas: [{id, apodo, especie, registradaEn}]`. El administrador sabe qué plantas tiene el usuario, no lee sus diagnósticos ni sus invitados, y sigue sin poder abrir `GET /plantas/{id}` de una planta ajena (RN3). Descartado: darle acceso de lectura a todas las plantas por ser administrador (RN3 dice que un cliente solo accede a las suyas y a las compartidas, y el administrador también es cliente).

**D48. Un diagnóstico del historial se reconstruye desde su propia fila, no desde el catálogo.** La `Especie` y la `Medicion` de un `DiagnosticoDePlanta` leído se arman con los parámetros guardados (valor y rango óptimo con el que se clasificó). Así un diagnóstico viejo no cambia aunque la especie se edite o se descontinúe, ni cuando la planta cambia de especie (`especie_nombre` es la del momento). Consecuencia: la `Especie` reconstruida es un retrato de lo usado para evaluar y su `activa` queda en `true`; nadie lee ese campo de un diagnóstico del historial. Descartado: volver a buscar la especie por nombre, que mentiría (los rangos de hoy contra un estado de ayer) y fallaría si alguna vez se eliminara una especie.

## 4. Límites conocidos (decirlos antes de que los encuentren)

- **Concurrencia en la medición.** `ultimoDe` y `guardar` no están bajo un bloqueo: dos mediciones simultáneas sobre la misma planta pueden compararse contra el mismo "anterior" y emitir dos avisos, o ninguno. Con una maceta que mide cada quince minutos es improbable; con un dueño que pulsa dos veces, posible. La salida es un bloqueo pesimista sobre la fila de la planta o una versión optimista. No se hizo por el mismo motivo que la RN4 del bloque 1 (exigiría una consulta específica de JPA en el puerto).
- **Aviso dentro de la transacción (D43).** Si algo falla después de `avisar`, la transacción se revierte y el aviso ya salió. Con el adaptador de log es inocuo; con correo, no.
- **El `Notificador` es un solo bean.** Cuando exista un segundo adaptador (correo, WebSocket), Spring no sabrá cuál inyectar: hará falta un `NotificadorCompuesto` que reparta, o una lista en `ServicioMediciones` y `CascadaDeEliminacion` (ver la pregunta de mutación 4). Un adaptador no debe lanzar excepciones al fallar: el contrato del puerto lo dice y la medición ya quedó registrada.
- **`registrar(plantaId, medicion, origen)` no autoriza (D41).** Quien lo llame desde un adaptador nuevo debe autenticar su origen antes.
- **Historial sin retención.** Crece sin límite (DA6 sigue abierta en lo que respecta a recortarlo).
- **Un diagnóstico no guarda quién lo registró,** solo el origen. El dueño es el único que mide hoy; cuando una maceta mida, el origen `DISPOSITIVO` basta.
- **Un tipo de contenido no soportado responde 500, no 415** (por ejemplo `POST /api/v1/plantas` con `Content-Type: text/plain`). No es nuevo: `ManejadorGlobalDeErrores` no mapea `HttpMediaTypeNotSupportedException` y la cae en el manejador genérico, y afecta por igual a los endpoints del bloque 1. Sigue siendo JSON (RA1). La corrección es un manejador de tres líneas; no se hizo para no mezclar un arreglo del bloque 1 con este.
- **`GET /api/v1/plantas` lista solo las propias.** Las compartidas conmigo (RF-E5) llegan con el bloque de invitaciones; `RepositorioPlantas.compartidasCon` ya existe y está probado en el adaptador, pero no tiene servicio ni endpoint.

## 5. Pruebas

Antes del bloque: **294** según el informe de surefire de `develop` (el encargo decía 298; la diferencia no se pudo explicar y se midió, no se asumió). Después: **441** (`./mvnw verify`), 147 nuevas. ArchUnit pasa de 10 a 12 reglas (las 10 anteriores sin modificar).

| Capa | Pruebas nuevas relevantes |
|---|---|
| Dominio (JUnit puro) | `PlantaTest` (RN11 en las fábricas, apodo), `PoliticaDeAccesoTest` (dueño, invitado, ajeno, nulos), `DetectorDeCambioDeEstadoTest` (los 9 pares, primer diagnóstico, solo mira el estado), `DiagnosticoDePlantaTest` (envuelve sin copiar, D17), `PaginacionTest`, `AvisoTest` |
| Aplicación (dobles, sin Spring) | `ServicioPlantasTest`, `ServicioMedicionesTest` (RN5, RN8, destinatarios, comparación por planta, origen como parámetro), `ServicioConsultaDeDiagnosticosTest` (no escribe, paginación, vacío), `CascadaDeEliminacionTest`, y ampliaciones de `ServicioCuentasTest` y `ServicioAdministracionTest` (cascada por baja propia y por administrador, RF-B2) |
| Infraestructura (H2 + Flyway real) | `AdaptadorPlantaJpaTest`, `AdaptadorAccesoJpaTest`, `AdaptadorDiagnosticoJpaTest` (ida y vuelta completa, orden, paginación, hijas, snapshot), `NotificadorEnRegistroTest`. Incluyen que las claves foráneas rechazan borrar una planta con accesos o historial y un usuario con plantas |
| Web (MockMvc) | `PlantasControllerTest`, `MedicionesYDiagnosticosDePlantaControllerTest` (forma, validación, 400/403/404/409, solo GET en consultas), `SeguridadWebTest` y `SinHtmlTest` ampliados, `AdministracionControllerTest` (RF-B2) |
| Contenedor real | `FlujoDePlantasIntegracionTest` (15 casos): registrarse, crear, medir dos veces con cambio de estado, historial, un solo aviso; matriz dueño/invitado/ajeno/administrador; RN11 de punta a punta; las tres cascadas |
| ArchUnit | `consultar_diagnosticos_no_puede_producir_ni_avisar` y `las_politicas_de_dominio_no_llaman_puertos`; la primera se comprobó con una violación deliberada y falla |

Además se comprobó que las pruebas muerden: se rompió a propósito `PoliticaDeAcceso.puedeModificar` (que devolviera siempre `true`) y `DetectorDeCambioDeEstado` (que contara el primer diagnóstico) y fallaron 7 casos de integración y los de aplicación y dominio correspondientes. `scripts/probar-dominio-sin-infraestructura.sh` sigue pasando: el dominio y la aplicación nuevos compilan y pasan sus pruebas sin un solo archivo de `infraestructura`.

`FlujoDePlantasIntegracionTest` usa el prefijo `plantas-` y limpia en `@AfterEach`, hijos antes que padres (las claves foráneas no tienen cascada), para no romper el contrato del catálogo que espera exactamente 5 especies.

## 6. Preguntas de mutación: "si mañana cambia X, ¿qué archivos tocas?"

1. **Las mediciones llegan de una maceta en vez de un formulario.** Se agrega un adaptador de entrada en `infraestructura` (el canal de la maceta) que autentica la credencial de dispositivo (RN19) y llama a `ServicioMediciones.registrar(plantaId, medicion, OrigenDeMedicion.DISPOSITIVO)`. No cambian `ServicioMediciones`, `EvaluadorDeEstado`, `DetectorDeCambioDeEstado`, `DiagnosticoDePlanta`, `RepositorioDiagnosticos`, ni el esquema (el origen `DISPOSITIVO` ya existe y la tabla ya lo admite). Se agrega un puerto y su repositorio de macetas. Es el pago de D17 y D41: un caso de uso, dos adaptadores.
2. **El invitado pudiera editar (permiso de escritura).** Se cambia `PoliticaDeAcceso.puedeModificar` y su prueba; `AutorizadorDePlantas` y los servicios no se tocan. Si el permiso fuera por invitado (D2 lo descartó) habría que agregar una columna a `acceso_compartido`, una migración V8 en ambos dialectos, y `AccesoCompartido` y `RepositorioAccesos` ganarían el nivel; la política recibiría accesos en vez de ids.
3. **Se prefiere 403 para el ajeno en vez de 404.** Un solo cambio de comportamiento: `AutorizadorDePlantas.paraLeer` lanzaría `OperacionNoPermitidaException` cuando la planta existe pero no se puede leer. Ojo: eso exige distinguir "no existe" de "no es tuya", y es justo la fuga que D35 evita. Cambian `AutorizadorDePlantas`, las pruebas de aplicación de ajeno, `PlantasControllerTest` y la matriz de `FlujoDePlantasIntegracionTest`; la política y los controladores no.
4. **Se agrega aviso por correo además del log.** Una clase nueva en `infraestructura.notificacion` que implementa `Notificador`. Como entonces hay dos beans del mismo tipo, hay que decidir cómo se reparten: un `NotificadorCompuesto` anotado `@Primary` que llame a los demás. Ni `ServicioMediciones`, ni `CascadaDeEliminacion`, ni el dominio cambian. Si el correo fuera lento, la salida es `@TransactionalEventListener` o un hilo aparte dentro del adaptador de correo, nunca en la aplicación.
5. **El historial conserva solo el último año (DA6).** `RepositorioDiagnosticos` gana `eliminarAnterioresA(Instant)`, `AdaptadorDiagnosticoJpa` la implementa con una sentencia (las hijas caen por la cascada de V7), y algo periódico la llama; hoy no existe ningún proceso programado, así que sería la primera pieza de ese tipo. El índice de V7 (`planta_id, registrado_en`) ya sirve.
6. **Se agrega la magnitud pH.** A la lista del bloque 1 se suma un archivo: `SolicitudMedicion` (campo `ph` y su `Lectura` en `aMedicion()`), porque la medición manual tiene su propio DTO. La persistencia del historial no cambia: `diagnostico_parametro` guarda una fila por magnitud sin `CHECK` sobre su nombre, y `MapeadorDiagnosticoDePlanta` arma tantos parámetros como haya (los diagnósticos viejos simplemente no tienen pH). Con las columnas fijas por magnitud que se descartaron en D39, serían además una migración de columnas.
7. **¿Dónde se prueba que consultar no escribe?** En tres sitios: `ServicioConsultaDeDiagnosticosTest.consultarNuncaEscribe` (cuenta las escrituras del doble), la regla ArchUnit `consultar_diagnosticos_no_puede_producir_ni_avisar` (el servicio no puede ni referirse al evaluador) y `FlujoDePlantasIntegracionTest` (cuenta filas antes y después de consultar con el contexto real).
8. **¿Qué pasa si la planta cambia de especie con diagnósticos ya guardados?** Nada en el historial: cada diagnóstico guarda el nombre de la especie y los rangos con que se evaluó (D48); el siguiente diagnóstico usa la especie nueva. Hay una prueba de adaptador y otra de integración que lo muestran.
9. **¿Por qué el aviso de cambio incluye al dueño si él mismo midió?** Porque RF-G1 dice "dueño e invitados" y la medición puede venir de la maceta (origen `DISPOSITIVO`), cuando el dueño no está mirando. Excluirlo según el origen sería una regla que hoy nadie pidió.



---

# Corte 2, bloque 4: compartir plantas, recuperación, correo, vista en vivo y solicitudes de especie

Qué se construyó, dónde vive cada pieza y por qué, qué requerimiento cubre y qué decisiones se tomaron (D49 en adelante, con la alternativa descartada). Misma convención de los bloques anteriores: puertos en `dominio.puerto`, "quién soy" como `UUID` desde `IdentidadDelSolicitante`, errores con `ErrorRespuesta` desde `ManejadorGlobalDeErrores`, migraciones nuevas en ambos dialectos (V8 a V10), perfil `test` para los `@SpringBootTest`, planta ajena 404 (D35), cascadas explícitas (D37).

Alcance: invitaciones por enlace de un solo uso (RF-E1 a RF-E6, RN13), recuperación de contraseña (RF-A7, RF-A8, RN14), notificación por correo (D13), varios notificadores con reparto por tipo, avisos después del commit, vista en vivo por WebSocket (RF-G2), solicitudes de especie (RF-H1 a RF-H5) y el arreglo del 415. **Fuera: macetas, emparejamiento, canal de dispositivos y front.** El WebSocket de este bloque es solo la vista del navegador; el canal de la maceta (DA7, MQTT o WebSocket) no se tocó. El núcleo del corte 1 tampoco (`Medicion`, `Lectura`, `Diagnostico`, `EvaluadorDeEstado`).

## 1. Cómo se prueba a mano

Variables nuevas (todas con valor de desarrollo; ninguna es obligatoria):

| Variable | Para qué | Por defecto |
|---|---|---|
| `FITO_SMTP_HOST`, `FITO_SMTP_PUERTO` | Servidor SMTP | `localhost`, `1025` (Mailpit) |
| `FITO_SMTP_USUARIO`, `FITO_SMTP_CONTRASENA` | Credenciales SMTP; vacías = sin autenticación | vacías |
| `FITO_SMTP_AUTH`, `FITO_SMTP_STARTTLS` | `true` cuando el servidor lo exige | `false` |
| `FITO_CORREO_REMITENTE` | Dirección "De:" | `no-responder@fitodiagnostico.local` |
| `FITO_CORREO_ASINCRONO` | Enviar en otro hilo | `true` |
| `FITO_FRONT_URL` | Base de los enlaces de los correos (`/invitacion?token=`, `/restablecer?token=`) | `http://localhost:5500` |

Si no hay servidor SMTP, la aplicación arranca y funciona: cada correo falla con una línea de `WARN` y nada más (el aviso es mejor esfuerzo, ver límites).

```bash
# 1. Mailpit: SMTP en 1025, bandeja web en http://localhost:8025
docker run -d --rm --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
# 2. La aplicación (variables del bloque 1) y listo: ya apunta a localhost:1025
./mvnw spring-boot:run

U=http://localhost:8080/api/v1; H='Content-Type: application/json'
# Dos cuentas y una planta
for n in ana beto; do curl -s -X POST $U/auth/registro -H "$H" -d "{\"nombre\":\"$n\",\"correo\":\"$n@vivero.co\",\"contrasena\":\"clave-larga-1\"}" >/dev/null; done
TA=$(curl -s -X POST $U/auth/sesion -H "$H" -d '{"correo":"ana@vivero.co","contrasena":"clave-larga-1"}' | jq -r .token)
P=$(curl -s -X POST $U/plantas -H "$H" -H "Authorization: Bearer $TA" -d '{"apodo":"Pepa","especie":"potos"}' | jq -r .id)
# Ana invita a alguien sin cuenta (el correo es opcional; sin él, ella reparte el enlace)
curl -s -X POST $U/plantas/$P/invitaciones -H "$H" -H "Authorization: Bearer $TA" -d '{"correo":"visita@vivero.co"}'
#   -> {"token":"...","enlace":"http://localhost:5500/invitacion?token=...", ...}  y un correo en Mailpit
# La visita se registra y acepta en un solo paso (ruta pública)
curl -s -X POST $U/invitaciones/aceptar-con-registro -H "$H" -d "{\"token\":\"<token>\",\"nombre\":\"Visita\",\"correo\":\"visita@vivero.co\",\"contrasena\":\"clave-larga-1\"}"
# Recuperación: el enlace llega a Mailpit; el token sale del correo
curl -s -X POST $U/auth/recuperacion -H "$H" -d '{"correo":"beto@vivero.co"}'
curl -s -X POST $U/auth/restablecimiento -H "$H" -d '{"token":"<del correo>","contrasenaNueva":"otra-clave-larga-1"}'
```

Vista en vivo (cliente de ejemplo con `@stomp/stompjs`; el JWT va en el primer frame, no en la URL):

```js
const cliente = new StompJs.Client({
  brokerURL: 'ws://localhost:8080/ws',
  connectHeaders: { Authorization: 'Bearer ' + token },
  onConnect: () => {
    cliente.subscribe('/user/queue/plantas/' + plantaId, m => console.log(JSON.parse(m.body)));  // DIAGNOSTICO_NUEVO y CAMBIO_DE_ESTADO
    cliente.subscribe('/user/queue/avisos', m => console.log(JSON.parse(m.body)));               // ACCESO_PERDIDO, SOLICITUD_RESUELTA
  },
});
cliente.activate();
```

Esta cadena se verificó además contra un PostgreSQL 16 y un Mailpit reales en contenedores (V8 a V10 aplican, Hibernate valida el esquema, los correos llegan); no hay prueba automática contra PostgreSQL (no hay Testcontainers en el proyecto), igual que en el bloque 3. Los contenedores se detuvieron al terminar.

## 2. Mapa de archivos

### dominio (sin Spring, sin JPA, sin HTTP, sin correo, sin WebSocket; ArchUnit lo vigila)

| Archivo | Por qué vive en dominio | Cubre |
|---|---|---|
| `modelo/Invitacion.java`, `modelo/EstadoInvitacion.java` | Token de un solo uso con caducidad. Solo guarda la huella del token. `estadoEn(ahora)` deriva CADUCADA de la fecha; el dominio no llama al reloj, recibe `ahora`. | RF-E1..E3, RN13, D12, D49, D51 |
| `modelo/SolicitudRestablecimiento.java` | Un solo uso, caduca (1 h) y se invalida si la huella de la credencial ya no es la de cuando se pidió. | RF-A7, RF-A8, RN14, D13, D52 |
| `modelo/SolicitudEspecie.java`, `modelo/EstadoSolicitud.java` | PENDIENTE a APROBADA o RECHAZADA, una sola vez; rechazar exige respuesta. | RF-H1..H5, D55 |
| `modelo/Aviso.java` (modificado) | Gana `datos` (token, id de planta, estado) y `correos` (personas sin cuenta). `toString` no imprime ninguno de los dos. El constructor de tres argumentos se conserva. | D53 |
| `puerto/GeneradorDeTokens.java` | `generar()` y `huella(texto)`. | D51 |
| `puerto/RepositorioInvitaciones`, `RepositorioRestablecimientos`, `RepositorioSolicitudes` | Persistencia de los tres agregados nuevos, con `eliminarDe...` para las cascadas. | D37, D56 |
| `puerto/RepositorioAccesos.java` (modificado) | `accesosDe(plantaId)`: los accesos con su fecha, para que el dueño vea a quién le dio acceso y desde cuándo. | RF-E4 |
| `excepcion/*` (6 nuevas) | `InvitacionNoEncontrada`, `InvitacionNoVigente` (lleva el estado), `InvitadoNoEncontrado`, `RestablecimientoNoVigente`, `SolicitudNoEncontrada`, `SolicitudYaResuelta`. | RN13, RN14 |

### aplicacion

| Archivo | Qué hace | Cubre |
|---|---|---|
| `ServicioInvitaciones.java` | Generar, listar y revocar invitaciones; aceptar con sesión; registrarse y aceptar en una transacción; listar invitados, revocar acceso, listar compartidas, renunciar. | RF-E1..E6, RF-G3 |
| `ServicioRecuperacion.java` | Solicitar (misma respuesta exista o no el correo) y restablecer con token. | RF-A7, RF-A8, RN14 |
| `ServicioSolicitudes.java` | Enviar, ver las propias, listar pendientes, aprobar y rechazar con aviso. | RF-H1..H5 |
| `RutasDelFront.java` | Las pantallas del front a las que apunta un enlace con token (`/invitacion?token=`, `/restablecer?token=`), escritas en un solo sitio y sin Spring. | D53 |
| `ServicioMediciones.java` (modificado) | Emite `DIAGNOSTICO_NUEVO` en cada medición, además de `CAMBIO_DE_ESTADO` cuando corresponde. | RF-G2, D62 |
| `CascadaDeEliminacion.java` (modificado) | Borra también las invitaciones de la planta, y las solicitudes de especie y de restablecimiento del usuario. | RN12, D56 |

Ninguna clase de `aplicacion` ganó un import nuevo de Spring: siguen siendo `@Service`, `@Component` y `@Transactional`. Que los avisos esperen al commit no se ve desde aquí (D57).

### infraestructura

| Archivo | Por qué vive aquí | Cubre |
|---|---|---|
| `persistencia/InvitacionEntity`, `InvitacionJpaRepository`, `AdaptadorInvitacionJpa`; igual para `Restablecimiento` y `SolicitudEspecie` | Adaptadores de los tres puertos. Solo la huella del token llega a la base. Claves foráneas sin cascada. `AdaptadorAccesoJpa` gana `accesosDe`. | D37, D51 |
| `seguridad/GeneradorDeTokensSeguro.java` | `SecureRandom` de 256 bits en Base64 URL, y huella SHA-256 hexadecimal. | D51 |
| `notificacion/NotificadorPorCorreo.java` | Adaptador SMTP. Resuelve UUID a dirección con `RepositorioUsuarios`; envía en un hilo aparte. | D13, D54 |
| `notificacion/NotificadorPorWebSocket.java` | Empuja el aviso por STOMP a cada destinatario. Nunca envía el token. | RF-G2, D59 |
| `notificacion/NotificadorCompuesto.java` | Reparte cada tipo de aviso a sus canales con una tabla visible (`RUTAS`). | D58 |
| `notificacion/NotificadorTrasCommit.java` | Decorador: retiene el aviso hasta el commit. En rollback no sale. | D57 |
| `notificacion/ConfiguracionNotificacion.java` | Arma la cadena y marca el decorador como `@Primary`. | D57, D58 |
| `websocket/ConfiguracionWebSocket.java` | STOMP en `/ws`, orígenes de CORS, broker simple, sentido único. | D59 |
| `websocket/ProtectorDeCanalStomp.java` | CONNECT autentica con el mismo JWT; SUBSCRIBE autoriza por planta; SEND se rechaza. | D59 |
| `websocket/HandshakeConErroresJson.java` | Los rechazos del handshake salen en JSON (RA1). | D59 |
| `configuracion/EnlacesDelFront.java` | URL base configurable (`FITO_FRONT_URL`) para el adaptador de correo; las rutas vienen de `aplicacion.RutasDelFront`. | D53 |
| `configuracion/ConfiguracionDominio.java` (modificado) | Bean `Clock`. | D50 |
| `db/migration/{h2,postgresql}/V8__invitacion.sql`, `V9__restablecimiento.sql`, `V10__solicitud_especie.sql` | Tablas nuevas, claves foráneas sin cascada, huellas únicas. | D37, D51 |

### web

| Archivo | Qué hace | Cubre |
|---|---|---|
| `InvitacionesController.java` | Rutas del dueño bajo `/plantas/{id}` (invitaciones e invitados), del invitado (`/plantas/compartidas`) y de quien recibe el enlace (`/invitaciones/aceptar`, `/invitaciones/aceptar-con-registro`). | RF-E1..E6 |
| `RecuperacionController.java` | `POST /auth/recuperacion` y `POST /auth/restablecimiento`, públicas. | RF-A7, RF-A8 |
| `SolicitudesEspecieController.java`, `AdministracionSolicitudesController.java` | Las del cliente y las del administrador (`/admin/solicitudes-especie`). | RF-H1..H5 |
| `ManejadorGlobalDeErrores.java` (modificado) | 404, 410, 409 y **415** nuevos. | D60 |
| `ConfiguracionSeguridad.java` (modificado) | Rutas públicas nuevas y `/ws/**` (el handshake no lleva `Authorization`; la autenticación es el frame CONNECT). | D59 |
| `dto/*` (12 nuevos) | Validación de forma y conversión. Ninguno expone credencial, hash ni, salvo el de generar, token. | RA6 |

### Rutas nuevas

| Ruta | Quién | Respuesta |
|---|---|---|
| `POST /api/v1/plantas/{id}/invitaciones` (cuerpo opcional `{"correo"}`) | dueño | 201 `{id, plantaId, token, enlace, estado, expiraEn, correoDestino}`; el token solo se entrega aquí |
| `GET /api/v1/plantas/{id}/invitaciones` | dueño | invitaciones vigentes, sin token |
| `DELETE /api/v1/plantas/{id}/invitaciones/{invitacionId}` | dueño | 204; 410 si ya no estaba pendiente |
| `GET /api/v1/plantas/{id}/invitados` | dueño | `[{id, nombre, correo, otorgadoEn}]` |
| `DELETE /api/v1/plantas/{id}/invitados/{invitadoId}` | dueño | 204; avisa ACCESO_PERDIDO al invitado |
| `GET /api/v1/plantas/compartidas` | cliente | las plantas compartidas conmigo |
| `DELETE /api/v1/plantas/compartidas/{id}` | invitado | 204; renunciar |
| `POST /api/v1/invitaciones/aceptar` `{token}` | cliente | 200 con la planta; 410 si ya se usó, caducó o se revocó |
| `POST /api/v1/invitaciones/aceptar-con-registro` `{token, nombre, correo, contrasena}` | **público** | 201 `{usuario, planta}` |
| `POST /api/v1/auth/recuperacion` `{correo}` | **público** | 202 con el mismo cuerpo exista o no el correo |
| `POST /api/v1/auth/restablecimiento` `{token, contrasenaNueva}` | **público** | 204; 410 si no sirve |
| `POST /api/v1/solicitudes-especie` `{nombre, justificacion?}`, `GET /api/v1/solicitudes-especie` | cliente | 201 / las propias |
| `GET /api/v1/admin/solicitudes-especie`, `POST .../{id}/aprobar`, `POST .../{id}/rechazar` `{respuesta}` | administrador | pendientes / la solicitud resuelta |
| `ws://.../ws` (STOMP) | cliente | `/user/queue/plantas/{id}` y `/user/queue/avisos` |

Códigos nuevos: 404 `INVITACION_NO_ENCONTRADA`, `INVITADO_NO_ENCONTRADO`, `SOLICITUD_NO_ENCONTRADA`; 410 `INVITACION_YA_ACEPTADA`, `INVITACION_CADUCADA`, `INVITACION_REVOCADA`, `RESTABLECIMIENTO_NO_VIGENTE`; 409 `SOLICITUD_YA_RESUELTA`; **415 `TIPO_DE_CONTENIDO_NO_SOPORTADO`**.

## 3. Decisiones técnicas (D49 en adelante)

**D49. Una invitación es PENDIENTE, ACEPTADA, REVOCADA o CADUCADA, y CADUCADA no se escribe nunca: se deriva de la fecha.** `Invitacion.estadoEn(ahora)` devuelve CADUCADA si está PENDIENTE y `expiraEn` ya pasó. Vigencia de una semana. Guardar CADUCADA exigiría un proceso periódico que recorra y marque, y hoy no hay ninguno en el sistema; derivarla da exactamente el mismo resultado visible. El dominio recibe `ahora` como parámetro y los servicios llevan un `Clock` inyectado (bean en `ConfiguracionDominio`): las pruebas de caducidad usan un reloj manual, sin `sleep`. Descartado: persistir CADUCADA con una tarea programada (primer proceso periódico del sistema para ganar nada visible) y filtrar por fecha en SQL (la regla quedaría en dos sitios: la consulta y el dominio).

**D50. Aceptar es un cuerpo, no una ruta, y registrarse más aceptar es una sola transacción con la invitación validada primero.** `POST /invitaciones/aceptar {token}` (con sesión) y `POST /invitaciones/aceptar-con-registro` (pública). El token va en el cuerpo porque una ruta queda en los registros de acceso del servidor y de cualquier proxy. En la variante con registro la invitación se valida ANTES de crear la cuenta (un token inválido no deja una cuenta nueva) y las dos escrituras viajan juntas: si el correo ya tiene cuenta responde 409 y la invitación sigue vigente. Todas las comprobaciones van antes de escribir: el dueño que intenta aceptar su propia invitación recibe 403 y no la consume. Aceptar no inicia sesión (D30): el registro no lo hacía y esta ruta sigue el mismo contrato. Descartado: dos llamadas del front (registrar, luego aceptar), que dejan una cuenta sin acceso si la segunda falla; y `/invitaciones/{token}/aceptar`, con el token en la ruta. `ServicioInvitaciones` llama a `ServicioCuentas.registrar` en lugar de duplicar el alta: es un servicio que usa a otro de la misma capa, y la regla de correo duplicado (RN1) sigue en un solo sitio.

**D51. Del token solo se guarda su huella SHA-256, y la huella viaja por el puerto `GeneradorDeTokens`.** El token (256 bits de `SecureRandom`) existe una vez, en la respuesta que crea la invitación; en la base queda `huella_token` y nadie, ni con acceso a una copia de seguridad, puede aceptar una invitación. SHA-256 sin sal ni costo es correcto aquí y no lo sería para una contraseña: la entrada es aleatoria de 256 bits, no algo que un humano eligió, así que no hay diccionario que probar y BCrypt solo agregaría latencia. La huella vive en el puerto (`generar()` más `huella(texto)`, una adición a la firma del diagrama) porque guardar solo la huella y buscar por huella son dos mitades de la misma decisión: si una estuviera fuera, alguien terminaría guardando el token. Una regla ArchUnit impide que una entidad JPA tenga un campo `token`. Costo: el dueño no puede volver a ver el enlace de una invitación pendiente, solo revocarla y generar otra. Descartado: guardar el token en claro (más cómodo: se podría relistar el enlace) y cifrarlo reversible (hace falta una clave que, si se filtra con la base, devuelve todo).

**D52. El restablecimiento se invalida comparando la huella de la credencial, y no comparte abstracción con la invitación.** "Se invalida si la contraseña cambia por otra vía" (RN14) se resuelve sin acoplarse a ninguna de esas vías: al pedir el enlace se guarda la huella del hash vigente y al usarlo se compara con la de ahora; si difieren, la contraseña cambió. Cubre el cambio autenticado (RF-A5) y cualquier camino futuro sin tocarlos. `solicitar` responde igual exista o no el correo: no devuelve nada, ni lanza, ni tarda distinto en el aviso (el correo sale en otro hilo, D54), y un correo mal formado también se calla (la forma ya la valida el borde con 400). Invitación y restablecimiento son "la misma figura" (token de un solo uso con caducidad), pero tienen estados distintos (una se revoca; la otra se invalida por un hecho externo) y reglas distintas; una abstracción común habría tenido más parámetros que código compartido. Descartado: invalidar las solicitudes pendientes desde `ServicioCuentas.cambiarContrasena` (acopla el servicio de cuentas al de recuperación y se olvida al agregar la siguiente vía) y una versión de credencial en `Usuario` (columna y migración para algo que el hash ya dice).

**D53. `Aviso` gana datos estructurados y correos externos; el enlace lo arma el borde.** El texto de un aviso es para leer; el token, el id de la planta o el estado son para máquinas, y van en `datos`. Así el dominio sigue sin conocer URLs: `EnlacesDelFront` (infraestructura, `FITO_FRONT_URL`) arma el enlace absoluto en el adaptador de correo y el controlador lo arma igual para la respuesta de generar, con las rutas de `RutasDelFront`. `correos` existe porque quien recibe una invitación sin tener cuenta no tiene `UUID`: es la única excepción a "los destinatarios son ids". El token es una credencial, así que `Aviso.toString()` no imprime `datos` ni `correos`, el log no lo muestra y el adaptador de WebSocket lo quita aunque llegue. Las rutas del front (`/invitacion`, `/restablecer`) son un acuerdo con el front; están escritas en un solo sitio, `aplicacion.RutasDelFront`, que usan el adaptador de correo y el controlador (cada uno lee la URL base de la propiedad), así la web no depende de infraestructura (lo vigila una regla ArchUnit y `scripts/probar-dominio-sin-infraestructura.sh`). Descartado: poner el enlace ya armado en `mensaje` (el token quedaría en el log y en la vista en vivo) y una `Map<String,Object>` sin claves nombradas (los typos se descubren en producción).

**D54. Correo: resolución de dirección en el adaptador, envío asíncrono y un doble por perfil.** (a) UUID a dirección: en `NotificadorPorCorreo`, consultando `RepositorioUsuarios`. La dirección es un dato del canal (el WebSocket no la necesita y el log no debe verla), y el puerto ya existe. Una cuenta que ya no existe al momento de enviar se omite sin ruido. Descartado: llevar la dirección en el `Aviso` (dato de canal en el dominio) y un puerto `DirectorioDeContactos` (otra interfaz con una sola implementación que sería `RepositorioUsuarios` con otro nombre). (b) `spring-boot-starter-mail` con el tipo `MailSender`: el adaptador no nombra `JavaMailSender` ni `MimeMessage`; texto plano, un correo por persona y sin copias para que nadie vea las direcciones de los demás invitados. (c) Se envía en un hilo aparte (dos hilos daemon, propios del adaptador y no un bean `Executor`, que apagaría el ejecutor por defecto de Spring Boot): un SMTP lento o caído no retrasa la medición, y el tiempo de respuesta de `recuperacion` deja de depender de si el correo existe. `FITO_CORREO_ASINCRONO=false` lo desactiva. (d) En el perfil `test` el `MailSender` es `CorreoCapturado` (en `src/test`, `@Profile("test")`), que guarda los mensajes; nada sale a ninguna red. En desarrollo, Mailpit (sección 1).

**D55. Aprobar una solicitud de especie NO crea la especie.** La solicitud solo trae un nombre y una justificación; una especie válida exige las tres magnitudes con rangos dentro del rango físico (D31), y esos rangos los define el administrador con `POST /admin/especies` (RF-C3). Aprobar es el compromiso de agregarla; el alta es otro acto, y el solicitante se entera de la resolución (RF-H5). Rechazar exige respuesta con el motivo (400 si falta) porque un rechazo sin motivo no le sirve a quien lo recibe; aprobar la admite opcional. Pedir una especie que ya está en el catálogo, activa o no, responde 409 `ESPECIE_YA_EXISTE`. Descartado: crear la especie inactiva y sin rangos (viola D31 y deja en el catálogo una fila que `RF-C3` tendría que completar) y crearla con rangos copiados de otra (inventa un dato agronómico).

**D56. La cascada se extiende, y ninguna cuenta deja datos suyos vivos.** Eliminar una planta borra además sus invitaciones (un enlace repartido a una planta que ya no existe no debe seguir circulando; aceptarlo da 404). Eliminar una cuenta borra además todas sus solicitudes de especie (pendientes y resueltas) y sus solicitudes de restablecimiento. Razón: dejar una solicitud pendiente de alguien dado de baja obligaría al administrador a resolver algo cuya respuesta no llega a nadie, y RN12 y D11 dicen que no queden datos de un usuario dado de baja. Las invitaciones aceptadas por quien se da de baja no guardan quién las aceptó (solo `aceptada_en`), así que no hay nada que limpiar; sus accesos como invitado ya los quitaba `revocarTodosDelInvitado`. Las claves foráneas siguen sin `ON DELETE CASCADE` (D37) y hay una prueba de adaptador por tabla que lo demuestra. Descartado: conservar las solicitudes resueltas como historial del catálogo (son datos personales sin dueño).

**D57. Los avisos salen después del commit, con un decorador del puerto y sin que la aplicación se entere.** `NotificadorTrasCommit` implementa `Notificador`, es `@Primary` y, si hay una transacción activa, registra el envío en `afterCommit`; sin transacción, sale de inmediato. En rollback el aviso no sale nunca (resuelve el límite del bloque 3 "el aviso sale aunque la transacción se revierta"). Los servicios siguen llamando `notificador.avisar(...)` y no ganaron un import: una regla ArchUnit impide que `aplicacion` nombre `TransactionSynchronization` o `ApplicationEventPublisher`. Un fallo del destino jamás se propaga: en `afterCommit` la excepción llegaría al llamador del commit y respondería 500 por una operación que sí quedó guardada. No hubo que mover nada de capa. Descartado: `@TransactionalEventListener(AFTER_COMMIT)` (es lo que D43 había anticipado: obliga a cada servicio a publicar eventos con `ApplicationEventPublisher`, o sea Spring dentro de aplicación, y a definir una clase de evento por tipo de aviso) y un `TransactionSynchronization` dentro de cada servicio (se olvida en el siguiente). Cómo se prueba: `NotificadorTrasCommitTest` (gestor de transacciones real: commit, rollback, `setRollbackOnly`, sin transacción, destino roto), `FlujoDeCompartirIntegracionTest.unAvisoDentroDeUnaTransaccionRevertidaNoSaleYUnoConfirmadoSi` con la cadena real, y la regla ArchUnit.

**D58. Varios notificadores: `NotificadorCompuesto` con una tabla de rutas por tipo.** Los servicios ven un solo `Notificador`; el compuesto decide:

| Tipo | Registro | Correo | Vista en vivo | Por qué |
|---|---|---|---|---|
| `CAMBIO_DE_ESTADO` | sí | sí | sí | Lo que quiere saber aunque no esté mirando, y verlo al instante si mira |
| `ACCESO_PERDIDO` | sí | sí | sí (`/user/queue/avisos`) | Ídem |
| `SOLICITUD_RESUELTA` | sí | sí | sí (`/user/queue/avisos`) | Ídem |
| `DIAGNOSTICO_NUEVO` | no | no | sí (`/user/queue/plantas/{id}`) | Una por medición: en el registro es ruido y por correo una avalancha; existe para refrescar una pantalla abierta |
| `INVITACION` | sí | sí | **no** | Lleva un token y su destinatario puede no tener sesión: solo le llega el correo |
| `RESTABLECIMIENTO` | sí | sí | **no** | Ídem |

La tabla es una constante visible (`NotificadorCompuesto.RUTAS`) y una prueba afirma que todo `TipoDeAviso` tiene ruta: si alguien agrega un tipo y olvida decidir, falla en vez de perder el aviso en silencio. Un canal que falla no impide a los demás. Descartado: una lista de `Notificador` en cada servicio (cada servicio decidiría los canales) y que cada adaptador filtre por tipo (la regla quedaría repartida en tres clases).

**D59. Vista en vivo: STOMP sobre WebSocket plano, autenticado en el primer frame, entrega por usuario.** (a) Spring WebSocket con STOMP y el broker simple en memoria, sin SockJS. STOMP da suscripciones y destinos por usuario hechos; un WebSocket plano habría exigido inventar el formato de mensajes y el reparto. (b) El navegador no puede poner cabeceras en el handshake de un WebSocket, pero sí en el primer frame: el cliente manda `Authorization: Bearer <JWT>` en el CONNECT, y `ProtectorDeCanalStomp` lo valida con el MISMO `JwtDecoder` (firma, caducidad, emisor) y comprueba que la cuenta exista (D21: eliminarla surte efecto en la siguiente conexión). Sin token válido no hay sesión. (c) Cada SUBSCRIBE se autoriza: solo `/user/queue/avisos` y `/user/queue/plantas/{id}`, y esta última únicamente si el usuario puede leer esa planta, con la misma regla que el GET (`ServicioPlantas.detalle`); una ajena o inexistente se rechaza igual (D35) y un administrador no lee plantas ajenas. Cualquier otro destino se rechaza. (d) El cliente nunca envía: todo SEND se rechaza (sin esto, un cliente podría publicar frames directamente en el broker haciéndose pasar por el servidor). (e) Se entrega POR USUARIO a los destinatarios que el servicio calculó al emitir el aviso, no a un tema por planta. Un tema compartido deja suscrito para siempre a quien ya perdió el acceso (la suscripción se autorizó cuando la hizo y no se vuelve a evaluar); por usuario, la autorización es la del momento del aviso y revocar surte efecto en el siguiente mensaje (hay una prueba de punta a punta). (f) Orígenes: la misma lista de `fitodiagnostico.cors.origenes`; el handshake responde 403 a cualquier otro `Origin`. CORS no cubre WebSocket; el origen defiende al usuario del navegador (CSWSH), no autentica. (g) El handshake fallido (GET sin `Upgrade`) responde 400 en JSON, no en el texto plano de Spring (RA1): `HandshakeConErroresJson`. Descartado: WebSocket plano con formato propio; JWT en la URL (`?token=`), que queda en registros de acceso; y un tema por planta con comprobación al suscribirse como única barrera (el problema de (e)). Esto NO es el canal de la maceta (DA7): no hay nada de macetas.

**D60. `Content-Type` no soportado responde 415 con el cuerpo uniforme.** `HttpMediaTypeNotSupportedException` tiene su manejador: `415 TIPO_DE_CONTENIDO_NO_SOPORTADO`, antes un 500 genérico por algo que es culpa del cliente (límite del bloque 1, que afectaba a todos los endpoints con cuerpo). Hay prueba de rebanada (ruta pública y autenticada, varios tipos) y contra el contenedor real (JSON, nunca HTML).

**D61. Revocar avisa; renunciar no. Las rutas cuelgan de la planta para el dueño y de `/plantas/compartidas` para el invitado.** RF-G3 es para quien pierde el acceso sin haberlo decidido: la revocación del dueño y la eliminación de la planta o de la cuenta del dueño avisan ACCESO_PERDIDO; que el propio invitado renuncie (RF-E6) no avisa a nadie, él lo hizo. Revocar una invitación (RF-E3) y revocar un acceso (RF-E4) son dos rutas porque son dos cosas: la primera cancela un enlace aún no usado y falla con 410 si ya se aceptó (lo que corresponde entonces es quitar el acceso). `/plantas/compartidas` es una ruta literal y por eso tiene prioridad sobre `/plantas/{id}`; `GET /plantas` sigue listando solo las propias. Solo el dueño lista invitaciones e invitados (D2: un invitado no ve a los demás).

**D62. `DIAGNOSTICO_NUEVO` se emite en cada medición y solo viaja por la vista en vivo.** Cumple lo que D43 dejó anunciado: ahora que existe un canal que lo usa, `ServicioMediciones` lo emite siempre (haya cambiado el estado o no) al dueño y a los invitados, antes de `CAMBIO_DE_ESTADO` si lo hay. Lleva en `datos` el id de la planta, el estado, el origen y la fecha, lo que la pantalla necesita para refrescarse sin otra petición. Una medición rechazada (403, 400) no emite nada. Los destinatarios incluyen al dueño aunque él haya medido, por la misma razón que en el bloque 3 (pregunta de mutación 9): la medición puede venir de la maceta.

## 4. Límites conocidos (decirlos antes de que los encuentren)

- **El aviso es de mejor esfuerzo y no se reintenta.** Sale después del commit y, el correo, en otro hilo. Si el proceso muere entre el commit y el envío, o el SMTP está caído, el aviso se pierde (queda una línea de `WARN`, sin dirección ni token). La salida es una tabla `outbox` escrita en la misma transacción y un despachador que reintente; no se hizo porque exigiría el primer proceso periódico del sistema.
- **La vista en vivo no repite lo que se perdió.** El broker simple es en memoria: sin historial, y con más de una instancia del backend un aviso solo llega a los clientes conectados a la instancia que lo emitió (haría falta un broker externo). Un cliente que se reconecta debe pedir `GET /plantas/{id}/diagnostico` para ponerse al día.
- **La identidad del WebSocket se verifica al conectar.** Una conexión abierta no se revalida cuando el JWT caduca, ni cuando se elimina la cuenta (la cuenta eliminada ya no está en los destinatarios, así que no recibe nada, pero la conexión sigue abierta hasta que el cliente se vaya). La suscripción tampoco se reautoriza (D59 e).
- **Las rutas públicas no tienen límite de intentos.** `recuperacion` puede usarse para mandarle correos a alguien; `aceptar-con-registro` y `restablecimiento` aceptan intentos sin freno (con 256 bits de entropía no hay nada que adivinar, pero sí que inundar). Hace falta un limitador por IP y por correo; no se hizo.
- **`recuperacion` no es de tiempo constante.** Existe una escritura en la base solo si el correo existe. El envío de correo ya no se nota (otro hilo), pero un atacante muy paciente podría medir la diferencia de la escritura. Mismo límite que el login del bloque 1.
- **Aceptar la misma invitación en dos peticiones simultáneas.** Se comprueba que esté vigente y después se consume, sin bloqueo: dos peticiones exactamente concurrentes podrían pasar las dos (las dos quedarían invitadas con el mismo enlace). La salida es un `UPDATE ... WHERE estado = 'PENDIENTE'` que cuente filas, o una versión optimista; mismo motivo que los límites de concurrencia de los bloques 1 y 3.
- **Las filas de invitaciones caducadas, aceptadas o revocadas no se purgan.** Quedan hasta que se elimine la planta. Con correo opcional, una invitación con `correo_destino` conserva una dirección ajena más tiempo del necesario; purgar es otra tarea periódica.
- **El correo es texto plano, sin plantilla, sin idioma elegible.**
- **Sin SMTP configurado la aplicación arranca y los correos fallan en silencio** (con `WARN`). Es a propósito (el correo no debe tumbar la aplicación) pero un despliegue mal configurado no lo notaría por un error.
- **No hay prueba automática contra PostgreSQL ni contra un SMTP real** (verificado a mano, sección 1).
- **Un cliente que sabe que su invitación es para su correo no está obligado a usar ese correo al registrarse**: el `correo_destino` es solo adónde se mandó el enlace, no una restricción de quién lo acepta (el enlace es el permiso, D12).

## 5. Pruebas

Antes del bloque: **442** según el informe de surefire de `develop`. Después: **663** (`./mvnw verify`), 221 nuevas. ArchUnit pasa de 12 a 17 reglas (las 12 anteriores sin modificar).

| Capa | Pruebas nuevas relevantes |
|---|---|
| Dominio (JUnit puro) | `InvitacionTest` (estados, caducidad con fechas fijas, un solo uso), `SolicitudRestablecimientoTest` (caduca, un solo uso, se invalida por la huella), `SolicitudEspecieTest` (transiciones, motivo obligatorio al rechazar), ampliación de `AvisoTest` (datos, correos, `toString` sin token) |
| Aplicación (dobles, sin Spring) | `ServicioInvitacionesTest` (quién puede qué, un solo uso, caducidad con reloj manual, registrar y aceptar en una transacción, autoinvitación, revocar), `ServicioRecuperacionTest`, `ServicioSolicitudesTest`, `CascadaDeEliminacionTest` y `ServicioMedicionesTest` ampliados (invitaciones, solicitudes, `DIAGNOSTICO_NUEVO`); el doble `NotificadorGrabador` captura los avisos |
| Infraestructura | `AdaptadorInvitacionJpaTest`, `AdaptadorRestablecimientoJpaTest`, `AdaptadorSolicitudEspecieJpaTest` y ampliación de `AdaptadorAccesoJpaTest` (H2 con Flyway V1 a V10; solo la huella en la base; las claves foráneas rechazan borrar sin cascada); `GeneradorDeTokensSeguroTest` (SHA-256 contra un valor de referencia); `NotificadorCompuestoTest` (la tabla de rutas), `NotificadorTrasCommitTest` (transacciones reales), `NotificadorPorCorreoTest`, `NotificadorPorWebSocketTest`, `ProtectorDeCanalStompTest` (frame por frame, sin red) |
| Web (MockMvc) | `InvitacionesControllerTest`, `RecuperacionControllerTest`, `SolicitudesEspecieControllerTest`, `SeguridadCompartirYAvisosWebTest` (401/403/rutas públicas), `TipoDeContenidoWebTest` (415), `SinHtmlTest` ampliado (415 y rutas nuevas con el contenedor real, el handshake en JSON) |
| Contenedor real | `FlujoDeCompartirIntegracionTest` (28 casos: compartir con invitados reales, recuperación, solicitudes, cascadas, correo capturado, rollback sin aviso) y `VistaEnVivoIntegracionTest` (10 casos con un cliente STOMP de verdad: el recorrido de punta a punta, autenticación, orígenes, suscripción a planta ajena rechazada, sentido único) |
| ArchUnit | `el_dominio_no_conoce_el_correo`, `correo_y_websocket_solo_en_infraestructura`, `la_aplicacion_no_conoce_la_sincronizacion_de_transacciones`, `las_entidades_no_guardan_tokens_en_claro`, `la_web_no_depende_de_infraestructura` |

El recorrido pedido, de punta a punta, está en `VistaEnVivoIntegracionTest`: el dueño invita, el visitante se registra y acepta en el mismo flujo, el dueño mide con cambio de estado, el invitado recibe el aviso por correo (doble) y por WebSocket, el dueño revoca y el invitado recibe ACCESO_PERDIDO.

Además se comprobó que las pruebas muerden. Se rompió a propósito, una por una, y fallaron las pruebas esperadas: (1) `ProtectorDeCanalStomp` sin comprobar la planta al suscribirse: 4 fallos (`ProtectorDeCanalStompTest` y `VistaEnVivoIntegracionTest`); (2) `NotificadorTrasCommit` enviando de inmediato en vez de esperar al commit: 6 fallos (los de transacciones reales y el de rollback de la cadena completa); (3) `ServicioInvitaciones` sin consumir la invitación al aceptar: 9 fallos; (4) un campo `token` en una entidad: 2 fallos de ArchUnit; (5) el CONNECT del WebSocket sin exigir token: 5 fallos; (6) quitar el manejador del 415: 5 fallos; (7) `ServicioMediciones` sin los invitados entre los destinatarios del cambio de estado: 7 fallos (aplicación, integración por correo y por WebSocket); (8) la cascada de la planta sin borrar sus invitaciones: 5 fallos. También se rompió `RutasDelFront` en el camino: `scripts/probar-dominio-sin-infraestructura.sh` falló mientras `web` dependía de `infraestructura`, y por eso pasó a existir `la_web_no_depende_de_infraestructura`.

`FlujoDeCompartirIntegracionTest` y `VistaEnVivoIntegracionTest` usan el prefijo `compartir-` y limpian en `@AfterEach`, hijos antes que padres (las claves foráneas no tienen cascada). Los mensajes del WebSocket se esperan con una sonda por el mismo destino hasta que llega: el broker simple no envía RECEIPT, y sin ella el test mediría antes de que alguien escuche.

## 6. Preguntas de mutación: "si mañana cambia X, ¿qué archivos tocas?"

1. **Mañana se agrega notificación por WhatsApp.** Una clase nueva en `infraestructura.notificacion` que implementa `Notificador` (`NotificadorPorWhatsApp`), un valor nuevo en `NotificadorCompuesto.Canal` y las entradas de `RUTAS` que lo usen, y una línea en `ConfiguracionNotificacion` que lo pase al mapa de canales: tres archivos de producción y la prueba del adaptador. No cambian ni el dominio, ni la aplicación, ni la web, ni `NotificadorTrasCommit` (el "después del commit" y el no propagar errores valen para el canal nuevo sin hacer nada). Con el envío por un proveedor externo conviene el mismo patrón asíncrono de `NotificadorPorCorreo`. Si hace falta el número de teléfono de cada usuario, esa es otra historia y sí toca más: `Usuario`, `UsuarioEntity`, `MapeadorUsuario`, una migración V11 en los dos dialectos y los DTO de la cuenta; la resolución UUID a número viviría en el adaptador, como la del correo (D54).
2. **El WebSocket del navegador se cambia por Server-Sent Events.** Cambia solo la infraestructura de la vista en vivo: se van `ConfiguracionWebSocket`, `ProtectorDeCanalStomp`, `HandshakeConErroresJson` y `NotificadorPorWebSocket`, y entran un `NotificadorPorSse` que empuja a un registro de `SseEmitter` por usuario más el endpoint HTTP que los abre (un `GET` que valida el JWT como cualquier otro y autoriza la planta con `ServicioPlantas.detalle`, con la misma regla de D59 c). En `ConfiguracionNotificacion` cambia el tipo que se pasa como canal `VIVO`; en `ConfiguracionSeguridad` se quita `/ws/**` (el SSE sí lleva `Authorization`); en el `pom`, `spring-boot-starter-websocket` sale. **No cambian** `ServicioMediciones`, `CascadaDeEliminacion`, `Aviso`, `NotificadorCompuesto` ni la tabla `RUTAS`, ni el dominio ni los demás servicios; la regla ArchUnit `correo_y_websocket_solo_en_infraestructura` sigue cumpliéndose sin tocarla. Se reescribe `VistaEnVivoIntegracionTest` y se reemplazan `NotificadorPorWebSocketTest` y `ProtectorDeCanalStompTest`. Dos diferencias de fondo: SSE solo va del servidor al cliente, que es justo el sentido único que ya teníamos, y el `EventSource` del navegador no permite poner `Authorization`, así que el front tendría que usar `fetch` con un lector de flujo o aceptar otra forma de autenticación.
3. **Las invitaciones caducan a las 24 horas.** Una constante en `Invitacion.VIGENCIA` y su prueba. La caducidad se deriva de `expiraEn` (D49), así que las ya creadas conservan su fecha. El texto del correo ("caduca en N días") lee la misma constante.
4. **Los avisos tienen que salir aunque la transacción se revierta (p. ej., "intento de acceso fallido").** Esos avisos no pasan por `NotificadorTrasCommit`: se llamaría directamente al compuesto desde un servicio que lo reciba por otro puerto. Hoy no hay ninguno; el decorador existe para el caso contrario (el normal).
5. **El proveedor de correo pasa de SMTP a la API de un tercero.** Solo `NotificadorPorCorreo` (o un `MailSender` que lo reemplace: el adaptador depende de la interfaz `MailSender` de Spring, no de `JavaMailSender`) y las propiedades de `application.yml`. Dominio, aplicación y web no se enteran, y `CorreoCapturado` sigue sirviendo para las pruebas.
6. **¿Por qué se guarda la huella del token y no el token?** Porque quien lea la base (una copia de seguridad, un volcado, una inyección SQL) no debe poder aceptar invitaciones ni restablecer contraseñas. SHA-256 sin sal alcanza porque el token es aleatorio de 256 bits; para una contraseña, que un humano eligió, no (D51). Lo vigila una regla ArchUnit y una prueba que mira el esquema.
7. **¿Dónde se prueba que el aviso sale después del commit y no si hay rollback?** En tres sitios: `NotificadorTrasCommitTest` (gestor de transacciones real), `FlujoDeCompartirIntegracionTest.unAvisoDentroDeUnaTransaccionRevertidaNoSaleYUnoConfirmadoSi` (la cadena real con el "SMTP" de pruebas) y la regla ArchUnit que impide que la aplicación sincronice transacciones por su cuenta.
8. **¿Por qué `DIAGNOSTICO_NUEVO` no va por correo ni por registro?** Porque es uno por medición: con una maceta que mide cada quince minutos serían cien correos al día por planta. Existe para refrescar una pantalla abierta (RF-G2); lo que sí le interesa a quien no está mirando es el cambio de estado (RF-G1), y ese sí va por correo. Es una fila de la tabla `RUTAS` y se cambia ahí.
9. **¿Qué impide que un invitado al que le quitaron el acceso siga viendo la planta en vivo?** La entrega es por usuario con los destinatarios que calcula el servicio al emitir el aviso, no un tema compartido; el que ya no es invitado no está en esa lista (D59 e). Hay una prueba de punta a punta: revoca y, con su suscripción todavía abierta, no recibe la medición siguiente.
10. **¿Por qué `registrarYAceptar` valida la invitación antes de crear la cuenta?** Para que un token inválido, caducado o ya usado no deje una cuenta nueva sin acceso a nada; y todo corre en una transacción, así que si el correo ya existe tampoco se consume el enlace (D50). Hay un caso de aplicación y uno de integración por cada rama.
