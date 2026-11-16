# Fase 8: luz acumulada y noche

Cuaderno de implementación (como `docs/fase7/`). Pedido: decisión de Santiago del 12-nov. Hasta la fase 7 la luz se clasificaba con la lectura del momento contra el rango de la especie, así que una planta a las 10 pm quedaba `EN_RIESGO` por falta de luz. Es absurdo: una planta necesita luz acumulada a lo largo del día, y de noche duerme. Esta fase cambia esa regla del dominio y nada más del dominio. El contrato HTTP solo agrega campos. El firmware y el front no se tocan (ver "Qué deben hacer el front y el firmware").

## Qué hay

| Pieza | Dónde | Capa |
|---|---|---|
| La regla: horas de luz, lux efectivo, noche, constantes | `dominio/servicio/ReglaDeLuzAcumulada.java` | dominio |
| Objetos de valor nuevos | `dominio/modelo/Muestra.java`, `SerieTemporal.java`, `ExposicionLuminica.java`, `Periodo.java`, `BaseDeEvaluacion.java` | dominio |
| El diagnóstico dice el periodo y la exposición | `dominio/modelo/Diagnostico.java` (campos `periodo`, `exposicion`, método `baseDeLuz()`) | dominio |
| El parámetro distingue lo leído de lo evaluado | `dominio/modelo/ResultadoParametro.java` (campos `valorEvaluado`, `base`) | dominio |
| El evaluador recibe la serie de luz | `dominio/servicio/EvaluadorDeEstado.java` (sobrecarga `evaluar(especie, medicion, tomadaEn, luzAnterior)`) | dominio |
| La recomendación dice que es un promedio del día | `dominio/servicio/RedactorDeRecomendaciones.java` | dominio |
| La serie de una magnitud, leída del historial | `dominio/puerto/salida/RepositorioDiagnosticos.java` (`serieDe`, con implementación por defecto) | dominio (puerto) |
| Trae la serie y se la entrega al evaluador | `aplicacion/ServicioMediciones.java` (`luzAnteriorA`) | aplicación |
| Consulta de la serie y columnas nuevas | `adaptador/salida/jpa/AdaptadorDiagnosticoJpa.java`, `DiagnosticoDePlantaJpaRepository.java`, `DiagnosticoDePlantaEntity.java`, `ParametroEmbebido.java`, `MapeadorDiagnosticoDePlanta.java` | infraestructura |
| Migración | `db/migration/{h2,postgresql}/V15__diagnostico_luz_acumulada.sql` | infraestructura |
| Contrato | `adaptador/entrada/rest/dto/RespuestaDiagnostico.java`, `adaptador/entrada/dispositivo/RespuestaLectura.java`, `adaptador/entrada/mqtt/RespuestaMqtt.java` | presentación |

## La regla

Se aplica cuando la planta tiene historial. Ventana: las lecturas de las 24 h anteriores a esta, más esta.

1. **Tramos.** Cada lectura describe el tramo desde la lectura anterior, con un tope de 30 min por tramo. La suma de tramos son las `horasObservadas`.
2. **Horas de luz.** Un tramo tiene luz si su lectura es de al menos **100 lux**.
3. **Lux efectivo.** `lux·h de los tramos con luz / max(horasDeLuz, 6 h × horasObservadas / 24)`. Con 6 h de luz o más es el promedio de lux en las horas con luz. Con menos, ese promedio se diluye en las 6 h que se exigen.
4. **Clasificación.** El lux efectivo contra el rango de la especie: `BAJO` si no llega al mínimo, `ALTO` si pasa el máximo, `OPTIMO` si cae dentro (extremos incluidos, como siempre).
5. **Noche.** Si esta lectura y las anteriores llevan **2 h** o más por debajo de 100 lux, el periodo es `NOCHE`. La luz no entra al estado global ni a las recomendaciones. El parámetro de luz sigue diciendo su clasificación.
6. **Sin historial.** Sin lecturas anteriores en la ventana (la primera lectura de una planta, o `POST /diagnosticos`, que no tiene planta) la luz se clasifica con la lectura del momento, el periodo es `DIA` y el diagnóstico dice `base: INSTANTANEA`.

Ejemplos con la suculenta (luz 800 a 2500 lux):

| Día | Horas de luz | Promedio en luz | Lux efectivo | Luz |
|---|---|---|---|---|
| Ventana con sol de mañana y tarde | 10 h | 1500 | 1500 | `OPTIMO` |
| Solo 3 h de sol | 3 h | 1500 | 1500 × 3 / 6 = 750 | `BAJO` |
| 6 h justas al mínimo | 6 h | 800 | 800 | `OPTIMO` |
| 5 h 45 min al mínimo | 5,75 h | 800 | 767 | `BAJO` |
| Sol directo todo el día | 10 h | 4000 | 4000 | `ALTO` |

## Decisiones

**D120. La aplicación trae datos en bruto; el dominio decide qué significan.** `ServicioMediciones` pide al puerto los lux de la ventana (`SerieTemporal`, una lista de `Muestra(tomadaEn, valor)`) y se la pasa a `EvaluadorDeEstado`. No cuenta horas ni compara lux. Se eligió entregar la serie y no un `ExposicionLuminica` ya calculado: si la aplicación calculara las horas de luz, el umbral de 100 lux viviría en la aplicación, y la regla RA-05 de la fase 2 dice que la aplicación no compara umbrales. Descartado también que el dominio consulte el repositorio: el dominio no obtiene datos (RA4). El dominio recibe objetos del dominio, no mapas ni DTO (RA6).

**D121. `periodo` es parte del diagnóstico; de noche la luz no cuenta, pero no se esconde.** `Diagnostico` agrega `periodo` (`DIA` o `NOCHE`). De noche, `ReglaDeLuzAcumulada.queCuentanParaElEstado` quita la luz de lo que agrega `ReglaPorDesviacion` y de lo que redacta `RedactorDeRecomendaciones`. El parámetro de luz sigue en `parametros` con su clasificación acumulada: si el día fue malo dice `BAJO`, aunque no degrade el estado. Así el front puede decir "hoy tuvo poca luz" sin que la planta amanezca en riesgo a las 10 pm. Sin historial el periodo es `DIA` por convención: no hay evidencia de noche, y el diagnóstico lo avisa con `base: INSTANTANEA`. `Diagnostico` no admite `NOCHE` sin exposición.

**D122. `valor` sigue siendo la lectura; lo comparado es `valorEvaluado`.** `ResultadoParametro` agrega `valorEvaluado` y `base` (`BaseDeEvaluacion`: `INSTANTANEA` o `ACUMULADA`). En una evaluación instantánea son el mismo número, y el constructor lo exige. `desviacionRelativa()` (de donde sale la gravedad, RF3) y el texto de la recomendación usan `valorEvaluado`. Se descartó poner el lux efectivo en `valor`: el contrato ya promete que `valor` es lo leído y el historial reconstruye la `Medicion` desde ese número. `BaseDeEvaluacion` no nombra ninguna magnitud: si mañana la humedad se acumula, se reusa.

**D123. Un solo número, el lux efectivo, contra el rango de siempre.** Pedía "promedio en las horas de luz dentro del rango y un mínimo de horas". Se juntaron en un número para no tocar `Rango.clasificar`, la desviación ni el redactor: la luz de las horas con luz se reparte en al menos las horas exigidas. Es la idea de la integral diaria de luz (lo que importa es cuánta luz entró en el día): 3 h a 3000 lux equivalen a 6 h a 1500. ALTO solo sale si el promedio en luz pasa el máximo, porque diluir nunca sube el número. Cada lectura describe el tramo **anterior** a ella y no el siguiente: así la lectura actual siempre pesa, y con dos lecturas el resultado es el de la actual, como la clasificación instantánea. El tope de 30 min por tramo evita inventar luz si la matera estuvo apagada. Las horas exigidas son proporcionales a lo observado (6 h por 24 h): una planta con 3 h de historial no queda en `BAJO` por no haber cumplido un día que no se ha visto.

**D124. `ReglaDeLuzAcumulada` es la única clase del dominio que nombra `Magnitud.LUZ`.** El fotoperiodo es de la luz y de ninguna otra magnitud. `ClasificadorDeParametros`, `ReglaPorDesviacion`, `RedactorDeRecomendaciones` y `ReglaDeAgregacion` siguen sin nombrar magnitudes (su OCP del corte 1 no cambia). `EvaluadorDeEstado` le pregunta a la regla `magnitud()`. Se conservaron los constructores anteriores de `EvaluadorDeEstado`, `Diagnostico` y `ResultadoParametro`, por eso `ConfiguracionDominio` y las pruebas viejas no cambiaron.

**D125. El umbral de "hay luz" es 100 lux, una constante del ambiente.** Día y noche son los mismos para todas las plantas del cuarto, y la OLED tiene que dormirlas igual. Derivarlo del rango de la especie haría que el cactus (mínimo 20 000 lux) "durmiera" en un día nublado. 100 lux es del orden del punto de compensación de las plantas de sombra (por debajo no ganan nada) y queda debajo del mínimo de luz de todas las especies del catálogo (el más bajo es el helecho, 150 lux). Una lámpara de cuarto (150 a 300 lux) cuenta como luz. Las 6 h mínimas también son constantes: es lo que piden las fichas de cultivo de las especies de sol del catálogo y queda debajo del fotoperiodo de Bogotá (unas 12 h todo el año). Hacerlas por especie es una columna nueva del catálogo; queda en límites.

**D126. Noche son 2 h de oscuridad sostenida.** Más que una nube, una cortina cerrada un rato o llevar la planta a otro cuarto; menos que cualquier noche real. Se cuenta desde la lectura oscura más antigua de la racha, no desde la última con luz: entre las dos no se sabe cuándo se fue la luz, y es mejor tardar un poco en dormir la planta que dormirla de más. El borde exacto (2 h) ya es noche. Si ahora hay luz, es de día aunque haya estado oscuro antes.

**D127. El puerto agrega `serieDe`, con implementación por defecto.** La matera reporta cada 5 s: unas 17 000 lecturas por día. Recorrer `historialDe` (100 diagnósticos completos por página, con sus parámetros y recomendaciones) serían unas 170 consultas por lectura. `RepositorioDiagnosticos.serieDe(planta, magnitud, desde, hasta)` es una lectura más del mismo historial. Su implementación por defecto recorre `historialDe` (Java puro, vive en el puerto), así que los dobles en memoria y cualquier otro adaptador la tienen sin escribir nada. El adaptador JPA la reemplaza por una consulta de solo instante y valor. Es un cambio del dominio (el puerto es del dominio) y por eso va en el commit del dominio.

**D128. Migración V15, porque el diagnóstico se reconstruye desde su fila.** Desde V7 el diagnóstico del historial sale de su propia fila y no se recalcula. Sin columnas nuevas, `GET /plantas/{id}/diagnostico` no podría decir que la planta duerme. V15 agrega a `diagnostico_planta` `periodo` (`DEFAULT 'DIA' NOT NULL`) y las cinco columnas `luz_*` de la exposición; a `diagnostico_parametro`, `valor_evaluado` y `base_evaluacion`; y el índice `ix_diagnostico_serie (planta_id, tomada_en)` para la consulta de D127. Las filas anteriores se leen como lo que fueron: de día y con la luz evaluada al instante. Las lecturas viejas sí sirven de historial desde el primer momento, porque `valor` siempre fue el lux leído.

**D129. El contrato solo agrega.** En `POST /api/v1/diagnosticos`, `POST /plantas/{id}/mediciones`, `GET /plantas/{id}/diagnostico`, el historial y `POST /dispositivos/{serial}/lecturas`: `diagnostico.periodo`, `diagnostico.exposicionLuminica` (`base`, `horasObservadas`, `horasDeLuz`, `luxPromedioEnLuz`, `luxEfectivo`, `horasEnOscuridad`; los números en `null` si `base` es `INSTANTANEA`) y `parametros[].valorEvaluado`. En la respuesta MQTT, `periodo`. Ningún campo existente cambia de nombre, tipo ni significado. El redondeo (dos decimales en horas, uno en lux) es del DTO; el dominio guarda el número completo. Los avisos por WebSocket no cambian: el front ya vuelve a pedir el diagnóstico al recibir `DIAGNOSTICO_NUEVO`.

## La prueba de cambio

Dos commits de código, separados a propósito:

| Commit | Qué | Archivos |
|---|---|---|
| `75838af` `feat(dominio)` | La regla nueva | `dominio/modelo/`: `BaseDeEvaluacion`, `Diagnostico`, `ExposicionLuminica`, `Muestra`, `Periodo`, `ResultadoParametro`, `SerieTemporal`. `dominio/puerto/salida/RepositorioDiagnosticos`. `dominio/servicio/`: `EvaluadorDeEstado`, `RedactorDeRecomendaciones`, `ReglaDeLuzAcumulada`. Pruebas: `LuzAcumuladaEnElModeloTest`, `SerieDelHistorialTest`, `EvaluadorConLuzAcumuladaTest`, `ReglaDeLuzAcumuladaTest` |
| `7eac1b1` `feat(aplicacion)` | Usarla y mostrarla | `aplicacion/ServicioMediciones`. `adaptador/entrada/`: `RespuestaDiagnostico`, `RespuestaLectura`, `RespuestaMqtt`, `SuscriptorDeLecturasMqtt`. `adaptador/salida/jpa/`: `AdaptadorDiagnosticoJpa`, `DiagnosticoDePlantaEntity`, `DiagnosticoDePlantaJpaRepository`, `MapeadorDiagnosticoDePlanta`, `ParametroEmbebido`. `V15` (h2 y postgresql). Pruebas: `ServicioMedicionesTest`, `AdaptadorDiagnosticoJpaTest`, `LecturasDeDispositivoIntegracionTest`, `SuscriptorDeLecturasMqttTest` (la lista de claves MQTT) |

Se verifica con `git show --stat 7eac1b1 | grep dominio` (sin salida) y `git show --stat 75838af | grep -v dominio` (solo el resumen).

Lo que muestra el primero: con el commit del dominio solo, el proyecto compila y las pruebas pasan sin tocar una línea de fuera del dominio (verificado en un checkout de `75838af`: 171 pruebas de dominio, aplicación, adaptadores de diagnóstico y ArchUnit, ninguna falla). Los constructores anteriores se conservaron (D124), así que la aplicación sigue llamando `evaluar(especie, medicion)` y obtiene lo de siempre. Lo que muestra el segundo: ninguna clase del dominio cambia para que la regla llegue a HTTP, MQTT y la base.

Lo que **no** cambió: `Rango`, `Especie`, `Medicion`, `Lectura`, `Magnitud`, `ClasificadorDeParametros`, `ReglaDeAgregacion`, `ReglaPorDesviacion`, `DetectorDeCambioDeEstado`; `ServicioDiagnostico` (RF-F6 sigue instantáneo por no tener planta), `ServicioConsultaDeDiagnosticos` (consultar sigue sin escribir, D3), los controladores, `config/`, el firmware, el front y el simulador.

## Pruebas

- Dominio, sin servidor ni base (`ReglaDeLuzAcumuladaTest` 13, `EvaluadorConLuzAcumuladaTest` 7, `LuzAcumuladaEnElModeloTest` 6, `SerieDelHistorialTest` 2): día con buena exposición, día con poca, exceso, noche tras un buen día, noche tras un mal día (el parámetro dice `BAJO` y el estado sigue `SALUDABLE`), de noche las otras magnitudes siguen contando, sin historial, serie vacía, lecturas fuera de la ventana o posteriores (reenvíos), hueco largo, poco historial. Bordes: 100 lux exactos cuentan como luz y 99,9 no; 6 h exactas al mínimo son `OPTIMO` y 5 h 45 min son `BAJO`; 2 h exactas de oscuridad son noche y 1 h 45 min no.
- Aplicación con dobles (`ServicioMedicionesTest`, 3 nuevas): la primera lectura es instantánea, un día de historial hace dormir a la planta, las lecturas de otra planta no cuentan.
- Adaptadores: `AdaptadorDiagnosticoJpaTest` (3 nuevas, H2 con Flyway hasta V15: el periodo, la exposición y el valor evaluado sobreviven a la base; la serie se recorta por `tomada_en`), `LecturasDeDispositivoIntegracionTest` (2 nuevas, de punta a punta por HTTP: los campos nuevos, y la noche que se ve en el diagnóstico vigente).
- Subconjunto (`MAVEN_OPTS=-Xmx512m ./mvnw -Dtest='*Diagnostic*,*Medicion*,*Luz*,*Lectura*,*Mqtt*,*Evaluador*,*Serie*,ArquitecturaTest,*Mediciones*' test`): 187 pruebas, todas pasan.
- Suite completa (`MAVEN_OPTS=-Xmx512m ./mvnw test`, una vez, al final): 909 pruebas, ninguna falla (eran 873 en la fase 7; la fase 8 agrega 36).

## Qué deben hacer el front y el firmware

- **Firmware.** Leer `diagnostico.periodo` de la respuesta de `POST /dispositivos/{serial}/lecturas` (o `periodo` de la respuesta MQTT). Con `NOCHE`, la mascota duerme: ojos cerrados, sin animación de riesgo por luz. El ánimo sigue saliendo de `estado`, que de noche ya no se degrada por luz. Espejo en `previsualizar.py` y paridad con el front (regla del 12-nov).
- **Front.** Leer `periodo` en el diagnóstico vigente, el historial y la respuesta de `POST /mediciones`, y dibujar la planta dormida con la misma pose que la OLED. De noche, mostrar el parámetro de luz en gris con "de noche no cuenta". Mostrar la luz del día con `exposicionLuminica` (`horasDeLuz` y `luxEfectivo`), y `valor` como "ahora". Si `base` es `INSTANTANEA`, no hay historial: no mostrar horas. `POST /diagnosticos` (sin cuenta) siempre es `INSTANTANEA` y `DIA`.

## Límites

- Sin reloj ni ubicación, el sistema no distingue la noche de un armario: una planta 2 h a oscuras de día también "duerme". La luz acumulada del día sigue diciendo si le faltó luz.
- Umbral de luz, horas mínimas y horas para la noche son iguales para todas las especies (D125). Hacerlos por especie es una columna nueva del catálogo, con su migración y su campo en `POST /admin/especies`.
- Cada lectura de la matera consulta unas 17 000 filas (D127). Con el índice de V15 es una lectura por rango, pero si hubiera muchas materas convendría un resumen por hora. **Resuelto en D175** (fase 12: resumen por hora).
- Al entrar en la noche, una planta que estaba `EN_RIESGO` solo por luz pasa a `SALUDABLE`, y eso dispara el aviso `CAMBIO_DE_ESTADO` (correo incluido). Es cierto, pero puede sorprender. **Resuelto en D171** (fase 12: `motivo: PERIODO`, sin correo).
- `POST /diagnosticos` no tiene historial y sigue juzgando la luz al instante: es lo pedido (RF-F6), y la respuesta lo dice con `base: INSTANTANEA`.
- El firmware y el front aún no usan `periodo` (sección anterior).

## Para el documento de arquitectura

Qué cambió en el dominio: la regla de luz. Antes la luz se comparaba con la lectura del momento; ahora se juzga por la luz acumulada en 24 h (horas con al menos 100 lux, mínimo 6 h), y tras 2 h de oscuridad la planta "duerme": la luz no cuenta para el estado global. La regla vive en una clase nueva del dominio, `ReglaDeLuzAcumulada`, con sus umbrales como constantes, y el diagnóstico agrega `periodo` y la exposición.

Qué no cambió: el dominio sigue sin obtener datos. La aplicación pide al puerto `RepositorioDiagnosticos` la serie de lux de la planta y se la entrega al evaluador como objetos del dominio (`SerieTemporal`); no compara ningún umbral. El clasificador, la regla de agregación por desviación, el redactor y el detector de cambios no se tocaron.

Por qué demuestra la hexagonal: el cambio de regla entró en un commit que solo toca el dominio y compila y pasa las pruebas solo. Llevarlo a HTTP, MQTT y la base fue otro commit que no toca el dominio. Las pruebas de la regla nueva corren sin servidor ni base, con la serie armada a mano. El contrato HTTP solo agregó campos, así que el front y la matera siguen funcionando sin cambios.
