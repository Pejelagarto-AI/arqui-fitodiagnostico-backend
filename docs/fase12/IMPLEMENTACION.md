# Fase 12: pulido de mascota, avisos y base de datos

Cuaderno de implementación (como `docs/fase10/`). Pedido: cerrar los límites que dejaron las fases 8, 9 y 10 y quitarle peso a la base antes de que haya muchas materas. Seis piezas, todas sin tocar `dominio/` (`git diff --stat origin/develop -- src/main/java/com/vivero/fitodiagnostico/dominio` da vacío). El firmware y el front no se tocan; lo que cambia para ellos está en "Qué cambia para el front".

## Qué hay

| Pieza | Dónde | Capa |
|---|---|---|
| `DORMIDA`; el amanecer no es sol | `mascota/modelo/Animo.java`, `MapaDeAnimo.java`, `DetectorDeSaltos.java`, `LecturaReciente.java` (copia `periodo`) | contexto `mascota` |
| `motivo: PERIODO` y sin correo por la noche | `aplicacion/CausaDelCambioDeEstado.java`, `ServicioMediciones.java`, `avisos/MotivoDelAviso.java`, `adaptador/salida/notificacion/NotificadorCompuesto.java` | aplicación y salida |
| Enfriamiento de las caricias | `mascota/aplicacion/ServicioMascota.java`, `ParametrosDeMascota.enfriamientoDeCaricia`, `config/PropiedadesDeMascota.java`, `MascotaController` (200) | mascota y entrada |
| El invitado lee los dispositivos; desvincular borra la salud | `aplicacion/ServicioDispositivos.java`, `salud/RepositorioDeSalud.eliminar`, `AdaptadorSaludJpa` | aplicación y salida |
| Resumen de luz por hora | `adaptador/salida/jpa/ResumenDeLuzPorHora.java` (puro), `ResumenesDeLuz.java`, `ResumenLuzHora{Entity,Id,JpaRepository}.java`, `AdaptadorDiagnosticoJpa.serieDe`, `V22` | salida |
| Retención de crudas | `historial/` (puertos), `aplicacion/ServicioDeRetencion.java`, `adaptador/salida/jpa/AdaptadorHistorialJpa.java`, `adaptador/entrada/programado/RetencionDeLecturas.java`, `config/ConfiguracionHistorial.java` | entrada, aplicación y salida |
| Índices y N+1 | `V22`, `PlantaJpaRepository` (`@EntityGraph` y `join fetch`) | salida |
| Medición de consultas | `ConsultasDelHistorialMedicionTest` | prueba |

## Decisiones

**D170. La mascota conoce la noche: `DORMIDA`, y el amanecer no es salir al sol.** `LecturaReciente` copia el `periodo` del diagnóstico de cada lectura (un constructor de tres argumentos se queda con `DIA`, así que las pruebas viejas no cambian). En `MapaDeAnimo`, de noche (`Diagnostico.periodo() == NOCHE`) la luz no puede producir `A_OSCURAS` ni `DESLUMBRADA`: se ignora al buscar el parámetro que más se desvía. Si era lo único que fallaba, el ánimo es `DORMIDA`, nuevo valor, con `causa: null` (dormir no es un problema y la cara no pide ayuda). Si de noche falla otra magnitud, gana esa aunque la luz se desvíe más. Con todo en rango de noche el ánimo sigue siendo `FELIZ` o `CANSADA`: `DORMIDA` es "la luz es lo único que falla", no "es de noche". `DESMAYADA` sigue ganando sobre todo, y su `causa` tampoco culpa a la luz de noche. En `DetectorDeSaltos.sol`, un salto cuya candidata es de periodo `DIA` y cuya referencia (la última hora antes de la candidata) era en más de la mitad `NOCHE` se descarta: es la mañana. El riego no cambia. Límite: sin reloj ni ubicación, una lámpara encendida en la noche se ve igual que un amanecer y tampoco cuenta como sol. Se eligió "más de la mitad" y no "toda la referencia" porque un amanecer gradual deja las últimas lecturas ya en `DIA`.

**D171. Un cambio de estado por la noche avisa por la vista en vivo con `motivo: PERIODO` y no manda correo.** Cuando el estado global cambia solo porque entró o salió la noche (la luz dejó de contar o volvió a contar), `ServicioMediciones` agrega `datos.motivo = "PERIODO"` al aviso `CAMBIO_DE_ESTADO`. La regla (`CausaDelCambioDeEstado`, en aplicación, sin tocar el dominio): el periodo cambió **y** ninguna magnitud distinta de la luz cambió de clasificación entre los dos diagnósticos; lo único que pudo mover el estado es la luz. Si además cambió la humedad, el aviso sale normal. `NotificadorCompuesto.rutasDe` quita el canal `CORREO` de un `CAMBIO_DE_ESTADO` con ese motivo; el registro y la vista en vivo siguen. Se decidió en el compuesto y no en el correo porque la tabla de rutas ya vive ahí y se lee entera. La clave y el valor están en `avisos/MotivoDelAviso`, un paquete neutro, porque los escribe la aplicación y los leen los adaptadores y ninguno puede depender del otro (D174). Descartado: un `TipoDeAviso` nuevo (es del dominio) y repetir el aviso por otro destino.

**D172. Una caricia por planta cada 10 s (configurable); las que sobran responden 200 con la última.** `fitodiagnostico.mascota.enfriamiento-de-caricia` (`PT10S` por defecto, `0` lo apaga). `ServicioMascota.actuar` busca la última `ACARICIADA` de la planta (`RepositorioEventosDeMascota.ultimoDeTipo`, servida por `ix_evento_mascota_tipo` de V22); si pasaron menos de 10 s devuelve `ResultadoDeAccion` con `repetida = true`, el `id` y la hora de esa caricia, sin guardar evento, sin avisar al front y sin mensaje MQTT. El controlador responde `200` en vez de `201`. Los 10 s justos ya cuentan. El `SELECT ... FOR UPDATE` sobre la fila de la mascota (D147) serializa dos caricias simultáneas; sin fila de mascota (planta sin lecturas) no hay bloqueo y dos caricias en el mismo milisegundo podrían colarse: es una carrera inofensiva. `LA_REGUE` no cambia (ya era idempotente con una pendiente, D138). El permiso sigue siendo del dueño: un invitado recibe 403 antes de llegar al enfriamiento.

**D173. El invitado lee los dispositivos de la planta; al desvincular se borra la salud.** `GET /plantas/{id}/dispositivos` usa `paraLeer` en vez de `paraModificar`: dueño e invitados leen, un ajeno recibe 404, igual que la planta. `POST` y `DELETE` siguen siendo solo del dueño (invitado 403). El invitado ve el `serial` (que es el código del sticker): no puede hacer nada con él, porque reclamar es del dueño y una matera ya reclamada responde 409. El Javadoc de `ReclamarDispositivoPort.listar` está en `dominio/` y sigue diciendo "solo el dueño": no se tocó por la regla de esta fase y queda desactualizado. `ServicioDispositivos.desvincular` llama a `RepositorioDeSalud.eliminar(serial)`: se va la fila entera, `ultimaLecturaEn` incluida, y quien reclame la matera después ve `salud: null` y `ultimaLecturaEn: null` hasta la primera lectura o reporte. `ServicioDispositivos` ahora recibe el puerto de salud (un parámetro más del constructor).

**D174. Dos paquetes nuevos fuera del dominio, sin adaptadores adentro: `avisos` y `historial`.** `avisos/MotivoDelAviso` (constantes) y `historial/` (`PodarHistorialPort`, `AlmacenDeHistorial`) siguen el camino de `salud/` (D150): nada de `..aplicacion..` porque ArchUnit prohíbe que un adaptador dependa de ese paquete, y nada de `dominio/`. Dos reglas nuevas en `ArquitecturaTest`: ninguno depende de adaptadores ni de la composición, y `AlmacenDeHistorial` lo implementa un adaptador de salida y `PodarHistorialPort`, la aplicación.

**D175. `serieDe` combina el resumen de las horas cerradas con las crudas de la hora en curso, sin cambiar el puerto.** Con lecturas cada 5 s la regla de luz acumulada leía unas 17 000 filas por lectura (D127). Ahora, para `Magnitud.LUZ` y un rango con al menos una hora entera cerrada, `AdaptadorDiagnosticoJpa.serieDe` lee una fila de `resumen_luz_hora` por hora cerrada (23 en la ventana de 24 h) y solo las crudas de la hora en curso (hasta unas 720). El puerto `RepositorioDiagnosticos` y la regla `ReglaDeLuzAcumulada` no cambian: el dominio recibe una `SerieTemporal` como siempre.
- **El resumen.** Por planta y hora: lecturas, segundos observados (la suma de los tramos de la regla, con su tope de 30 min), segundos con luz, lux por segundo de esos tramos, segundos de oscuridad al final de la hora, el primer tramo oscuro de esa cola y la hora de la última lectura. Son exactamente los números que la regla suma, y reusa sus constantes públicas (`UMBRAL_HAY_LUZ_LUX`, `HUECO_MAXIMO`).
- **Cómo la regla "ve" el resumen.** `ResumenDeLuzPorHora.comoSerie` lo vuelve a convertir en pocas muestras sintéticas (oscuridad inicial, luz con su lux medio, oscuridad final; tramos de a lo más 30 min), pegadas y alineadas a la derecha en la última lectura real, con un ancla al inicio que cuenta como luz (así no alarga hacia atrás la racha de oscuridad: la oscuridad empieza en la primera lectura oscura de verdad). Así cada tramo mide lo que el resumen dice y la regla llega a los mismos números. Se descartó cambiar la regla para que aceptara resúmenes: es el dominio, y la pieza no lo necesitaba.
- **Cuándo se llena: la primera vez que hace falta, no con una tarea cada hora.** La primera lectura de la planta después de que la hora cierra calcula y guarda el resumen de las horas que falten (una consulta de unas 720 filas por hora). Una tarea cada hora tendría que recorrer todas las plantas aunque no hayan hablado, y si se cae deja huecos que nadie nota; así, un hueco se llena solo en la siguiente lectura. Tampoco se escribe en cada lectura (una escritura más por cada una de las 17 000 diarias). Una hora sin lecturas queda anotada con `lecturas = 0` para no recalcularla. La retención usa el mismo camino (D176).
- **Lecturas atrasadas.** `guardar` invalida el resumen de la hora de la lectura y el de la siguiente (su primer tramo depende de la última lectura de la anterior) cuando la hora en que se tomó no es la hora en que se registró (cola sin red, reloj de la matera). En el camino normal no cuesta ni una sentencia. Un resumen invalidado se recalcula en la siguiente lectura.
- **Migración V22**, tabla `resumen_luz_hora` con clave `(planta_id, hora)` y `ON DELETE CASCADE` hacia `planta` (como la mascota, D144).

**D176. Retención configurable: crudas 30 días, resumen por hora para siempre.** `fitodiagnostico.historial.retencion.crudas` (`P30D`, mínimo `PT48H`), `.habilitada` (`true`; el perfil de pruebas la apaga) y `.cron` (`0 30 3 * * *`). `ServicioDeRetencion` corta en hora en punto, y por planta poda en tandas de un día, cada una en su transacción: asegura el resumen de cada hora que se va (y el de la hora siguiente al corte, cuyo primer tramo depende de la última lectura que se borra) y borra las crudas de ese rango en una sentencia (las tablas hijas caen por la cascada de V7). Si el resumen falla no se borra nada; una planta que falla se registra y no frena a las demás; una pasada interrumpida se completa en la siguiente. La tarea es un adaptador de entrada (`RetencionDeLecturas`) como el tic de la mascota. Consecuencia: el historial paginado y `GET .../diagnostico` solo ven los últimos 30 días; antes de eso solo queda el resumen por hora (no hay endpoint para leerlo: si se quiere una gráfica larga, es el siguiente paso). Las pruebas de la mascota, que miran las últimas 60 lecturas, no se afectan.

**D177. Índices, N+1 y la prueba que cuenta sentencias.** Revisión de las consultas más frecuentes:

| Consulta | Antes | Después |
|---|---|---|
| Listado de plantas del dueño | `ix_planta_dueno`; `PlantaEntity.especie` es `EAGER`: una sentencia más por cada especie distinta | igual índice; `@EntityGraph(attributePaths = "especie")` |
| Plantas compartidas conmigo | `ix_acceso_invitado`; mismo N+1 | `join fetch p.especie` |
| Historial paginado | `ix_diagnostico_historial (planta_id, registrado_en DESC)`: el motor ordenaba el desempate por `id` aparte | V22 lo reemplaza por `(planta_id, registrado_en DESC, id DESC)`: la página sale directo del índice |
| Última lectura | el mismo índice | el mismo (ya lo cubre) |
| Eventos de la mascota (línea de tiempo) | `ix_evento_mascota_linea` | sin cambio |
| Última caricia (nueva) | sin índice por tipo | `ix_evento_mascota_tipo (planta_id, tipo, ocurrido_en DESC, registrado_en DESC)` |
| Colecciones del diagnóstico (parámetros, recomendaciones) | `EAGER` + `SUBSELECT` desde la fase 6: ya van en lote | sin cambio |
| Dispositivos y salud (listados, revisión de silencio) | ya usan `findAllById` en una sentencia | sin cambio |

Medición (`ConsultasDelHistorialMedicionTest`, H2 con Flyway, estadísticas de Hibernate; imprime las cifras como `MEDICION ...` y fija los topes para que un N+1 nuevo rompa la prueba). Antes es la misma prueba sobre `develop` (rama de la fase 10 mergeada); después es esta rama:

| Consulta | Antes | Después |
|---|---|---|
| Listar 15 plantas de 5 especies (propias) | 6 sentencias | 1 |
| Listar 15 plantas de 5 especies (compartidas) | 6 | 1 |
| Una página de 100 diagnósticos del historial | 4 | 4 (ya iba en lote) |
| Última lectura | 3 | 3 |
| Serie de luz de 24 h con 17 280 lecturas guardadas | 1 sentencia, 17 279 filas al dominio, 16 ms | 2 sentencias, 420 muestras al dominio (60 sintéticas y 360 crudas de la media hora en curso), 2 a 4 ms |
| Primera serie tras sembrar un día sin resúmenes (una vez) | no aplica | 71 sentencias, unos 110 ms: calcula y guarda 23 resúmenes |

Los milisegundos son de H2 en memoria y miden poco: lo que importa en PostgreSQL es que ya no viajan ni se mapean 17 000 filas por lectura, y que el dominio recorre unas 400 muestras y no 17 000. En el peor momento (la última hora de la ventana llena) las crudas son unas 720.

**D178. Las aproximaciones del resumen, dichas.** Con crudas la regla mira 24 h exactas hacia atrás; con resumen, desde la primera hora en punto de la ventana, así que mira entre 23 y 24 h (se pierde hasta la hora parcial más antigua). Dentro de las horas cerradas el resumen reproduce los números de la regla (horas observadas y de luz a segundos, lux efectivo en menos de 0,5 %, oscuridad en menos de 0,01 h), incluso con huecos de más de 30 min y con lámpara de noche; las pruebas lo verifican contra las crudas de la misma ventana y contra las de 24 h (misma clasificación, mismo periodo). Quedan tres diferencias que no se corrigen: (1) un hueco largo sin lecturas dentro de una racha oscura no se cuenta como tiempo de oscuridad (las crudas sí, desde la primera lectura oscura), así que una matera apagada por horas puede tardar en declarar la noche; (2) el orden de la luz y la oscuridad dentro de una hora se pierde salvo la cola oscura, que es lo único que importa a la noche; (3) el borde de 2 h exactas de noche es exacto a segundos, no a milisegundos.

**D179. El contrato solo agrega.** Un valor de `animo` nuevo (`DORMIDA`), `datos.motivo` opcional en `CAMBIO_DE_ESTADO`, y dos cambios de estado HTTP en rutas que ya existían: `GET /plantas/{id}/dispositivos` ahora acepta invitados (antes 403) y `POST /plantas/{id}/mascota/acciones` con `ACARICIAR` puede responder `200` en vez de `201`. Detalle en "Qué cambia para el front".

## Qué cambia para el front

- **`animo: "DORMIDA"`** en `GET .../mascota` y en el campo `animo` de los avisos `EVENTO_DE_MASCOTA`. Dibujar la planta dormida (la misma pose de `periodo: NOCHE` de la fase 8, ojos cerrados, sin pedir ayuda). `causa` viene `null`.
- **`motivo: "PERIODO"`** en `datos` del aviso `CAMBIO_DE_ESTADO` de `/user/queue/plantas/{id}`: el estado cambió porque entró o salió la noche. No mostrarlo como alarma (un "durmió" o "amaneció" basta). Sin `motivo` sigue siendo un cambio real. Esos avisos ya no llegan por correo.
- **`POST .../mascota/acciones` con `ACARICIAR`:** `201` como siempre, o `200` con el mismo cuerpo (`id`, `tipo`, `estado: "HECHA"`, `creadaEn` de la caricia anterior) si pasaron menos de 10 s. En el `200` no hay evento nuevo ni aviso por WebSocket: no sumar una animación. Tratar `200` y `201` como éxito.
- **`GET /plantas/{id}/dispositivos` para invitados:** 200 con la lista y la `salud` de cada matera (mismos campos de la fase 10). Mostrar el aviso de salud al abrir la pantalla, no solo por el aviso en vivo. `POST` y `DELETE` siguen devolviendo 403 al invitado: ocultar esos botones.
- **Al desvincular y reclamar de nuevo** la matera, `salud` y `ultimaLecturaEn` vienen `null` hasta que hable.
- **Historial:** `GET .../diagnosticos` solo llega hasta 30 días atrás (D176).

## Pruebas

- **Mascota, sin Spring:** `MapaDeAnimoDeMascotaTest` (4 nuevas: de noche solo la luz duerme en las dos direcciones, otra falla gana, todo en rango sigue `FELIZ` y de día sigue `A_OSCURAS`, desmayada no culpa a la luz), `DetectorDeSaltosDeMascotaTest` (3 nuevas: el amanecer no es sol, el mismo salto de día sí, el riego no se afecta).
- **Aplicación, con dobles:** `ServicioMascotaTest` (2 nuevas: el enfriamiento de 10 s con la sobrante que no avisa y el límite de 10 s justos; el enfriamiento configurable y apagable), `CausaDelCambioDeEstadoTest` (3), `ServicioMedicionesTest` (2: anochecer y amanecer con `PERIODO`, y un cambio por humedad sin motivo), `ServicioDispositivosTest` (2 nuevas y 1 cambiada: el invitado lista, desvincular borra la salud), `ServicioDeRetencionTest` (4: corte y tandas, nada que podar, una planta que falla no frena a las demás, mínimo de 48 h).
- **Notificación:** `NotificadorCompuestoTest` (2 nuevas: `PERIODO` sin correo pero con registro y vista en vivo; otro motivo y otro tipo no pierden el correo).
- **Integración (Spring, seguridad, JPA, Flyway H2 hasta V22):** `MascotaIntegracionTest` (el enfriamiento por HTTP: `201`, `200` y `200`, un solo evento y un solo mensaje, invitado 403), `SaludDeDispositivoIntegracionTest` (2 nuevas: el invitado lee la salud y no puede reclamar ni desvincular; desvincular borra la fila y al reclamar de nuevo viene `null`), `ReclamoDeDispositivoIntegracionTest` (el invitado pasa de 403 a 200 en el listado), `RetencionDelHistorialConfiguracionTest` (la tarea existe encendida y el perfil de pruebas la apaga).
- **Adaptadores JPA (H2 con Flyway):** `ResumenPorHoraContraCrudasTest` (9: día de sol, noche tras un buen día, poca luz, exceso, matera apagada más de 30 min y lámpara de noche, el borde de 2 h exactas de noche y 1 min antes, lecturas espaciadas una hora (regresión del ancla), una lectura atrasada invalida su resumen y el siguiente, una planta sin lecturas), `RetencionDeLecturasJpaTest` (4: borra las viejas y deja las recientes, cada hora podada deja resumen y suma lo mismo, dos pasadas no cambian nada, la luz de hoy sigue saliendo de resúmenes tras podar), `ResumenDeLuzPorHoraTest` (5, puro), `ConsultasDelHistorialMedicionTest` (3, las cifras de D177).
- ArchUnit: 3 reglas nuevas (D174).

## Límites

- Los de D178 (23 a 24 h de ventana, huecos largos en una racha oscura).
- La retención borra las crudas viejas y no hay un endpoint que lea el resumen por hora (D176).
- El resumen solo existe para la luz: la mascota sigue leyendo 60 lecturas crudas con sus parámetros por lectura (D148).
- `saveAll` de los resúmenes con clave asignada hace un `SELECT` por fila antes del `INSERT` (la primera vez, 23 horas): cuesta una vez por hora de vida de la planta, no se optimizó.
- Con MQTT encendido las lecturas atrasadas (QoS 1 reentregado) invalidan resúmenes como cualquiera; no hay prueba con broker real (la fase no arranca ni uno).
- El `listar` del puerto `ReclamarDispositivoPort` dice "solo el dueño" en su Javadoc dentro de `dominio/` (D173).
- Las cifras de D177 son de H2 en memoria; no se midió contra PostgreSQL (sin Docker).
