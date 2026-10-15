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
- RF-B2 devuelve solo el usuario; sus plantas se agregan con el módulo de plantas.

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
