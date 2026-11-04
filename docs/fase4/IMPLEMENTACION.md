# Fase 4: lecturas del dispositivo por MQTT

Cuaderno de implementación (como `docs/fase3/IMPLEMENTACION.md`). **Es la pregunta de mutación de la fase 1, "¿y si la lectura llega por MQTT?", contestada con código:** un adaptador de entrada nuevo contra el mismo `RegistrarLecturaPort`, sin tocar un archivo del dominio ni de la aplicación. Spec: `SPEC-FASE4-MQTT.md` en la carpeta de la entrega.

| Commit | Qué hace |
|---|---|
| `7cd16d1` | `chore(mqtt)`: broker Mosquitto de desarrollo (`docker/mosquitto/mosquitto.conf`) y comando en el README |
| `ae9e54a` | `feat(mqtt)`: **el adaptador**. Paquete `adaptador.entrada.mqtt`, `config/ConfiguracionMqtt`, propiedades, regla ArchUnit |
| `84c362f` | `test(mqtt)`: traductor sin broker (16) y flujo completo con Moquette embebido (5) |
| `479765a` | `feat(simulador)`: `--transporte mqtt` y `simulador/mqtt_minimo.py` |
| este | `docs(fase4)` |

## 1. El flujo

```
  HTTP (sin cambios)                          MQTT (nuevo)
  ------------------                          ------------
  chip --reto/credencial--> backend           chip --PUBLISH QoS1--> broker --> backend (fito-backend, sesion persistente)
  chip --POST /lecturas---> backend                  fito/dispositivos/{serial}/lecturas {token, marcaDeTiempo, humedad, luz, temperatura}
                                                                                 |
                              LecturasDeDispositivoController    SuscriptorDeLecturasMqtt   (dos adaptadores de entrada, cero conocimiento mutuo)
                                              \                         /
                                               RegistrarLecturaPort.registrar(plantaId, medicion, DISPOSITIVO, tomadaEn)
                                                         (dominio y aplicacion: identicos)
  chip <--------------------------------------  broker <-- PUBLISH QoS1 -- backend   fito/dispositivos/{serial}/respuestas
```

El reto y la credencial siguen por HTTP: el chip consigue su token igual que antes (como AWS IoT, que aprovisiona por HTTPS y después habla MQTT). El token viaja **dentro del payload** de cada lectura.

Por cada mensaje, `SuscriptorDeLecturasMqtt`:

1. saca el serial del tópico y lee el JSON (roto o no-objeto: `PARAMETRO_INVALIDO`);
2. exige `token` (si falta: `CREDENCIAL_RECHAZADA`) y lo verifica con el **mismo `JwtDecoder`** que usa HTTP (firma, caducidad, emisor), más `tipo=dispositivo`, `scope=lecturas:reportar` y claim `planta` (si no: `CREDENCIAL_RECHAZADA`);
3. comprueba `sub == serial del tópico` (un chip no reporta por otro: `OPERACION_NO_PERMITIDA`, el mismo código del 403 HTTP);
4. recién entonces convierte el resto del JSON a su DTO `LecturaMqtt` (campo ausente o de formato inválido: `PARAMETRO_INVALIDO` con `detalle.campo`) y a `Medicion` (lo físicamente imposible lo rechaza el dominio, `PARAMETRO_INVALIDO`);
5. llama a `RegistrarLecturaPort.registrar(plantaId del TOKEN, medicion, DISPOSITIVO, marcaDeTiempo)`;
6. publica la respuesta en `.../respuestas`.

Orden deliberado: **autenticar antes que validar**, igual que HTTP; un mensaje sin token válido no llega nunca al puerto (hay prueba que lo exige: el doble del puerto no recibe llamadas). Planta inexistente: `PLANTA_NO_ENCONTRADA`; cualquier otra excepción: `ERROR_INTERNO` genérico y la traza al log. El `handleMessage` atrapa hasta `Throwable`: nada escapa al hilo de Paho y el suscriptor sigue vivo (hay prueba con un publicador que lanza).

## 2. Tópicos y mensajes

| Tópico | Publica | Payload | QoS |
|---|---|---|---|
| `fito/dispositivos/{serial}/lecturas` | el chip | `{token, marcaDeTiempo, humedad, luz, temperatura}` | 1 |
| `fito/dispositivos/{serial}/respuestas` | el backend | éxito `{ok:true, id, estado, registradaEn}`; error `{ok:false, error, mensaje, detalle}` | 1 |

Los códigos de error son los del cuerpo uniforme HTTP (`ManejadorGlobalDeErrores`): `PARAMETRO_INVALIDO`, `CREDENCIAL_RECHAZADA`, `OPERACION_NO_PERMITIDA`, `PLANTA_NO_ENCONTRADA`, `ERROR_INTERNO`.

Configuración (`application.yml`): `FITO_MQTT_HABILITADO` (`false` por defecto; sin ella, o sin broker, la aplicación arranca igual por HTTP), `FITO_MQTT_URL` (`tcp://localhost:1883`), `FITO_MQTT_CLIENT_ID` (`fito-backend`). Todo el cableado está en `config/ConfiguracionMqtt` con `@ConditionalOnProperty`; hay dos conexiones: `fito-backend` (entrada) y `fito-backend-respuestas` (salida), porque un clientId solo admite una conexión.

## 3. Decisiones

### D83: broker anónimo y JWT en el payload

El broker de desarrollo acepta conexiones anónimas y la autenticación la hace el backend verificando el JWT que viaja en **cada** mensaje. **Alternativas descartadas:** token como contraseña MQTT (el broker no lo valida sin un plugin, así que no autentica nada) y token en el tópico (queda en los logs del broker). **Lo que sí garantiza:** un mensaje sin token válido no entra al dominio, igual que en HTTP. **Lo que no:** cualquiera con acceso de red al puerto 1883 puede publicar (el backend lo rechaza) y *suscribirse* a las respuestas de cualquier serial (las respuestas no llevan datos sensibles, pero revelan que el serial existe y su estado). **Evolución:** que el broker valide el mismo JWT (`mosquitto-go-auth`) o certificados por dispositivo con TLS. El puerto 1883 no se expone fuera de la máquina de desarrollo.

### D84: tópico de respuestas

MQTT no tiene respuesta por mensaje; el tópico `.../respuestas` hace de ella. El chip se suscribe a su propio tópico y las imprime (el simulador) o las ignora (el firmware real puede no suscribirse). **Límite:** la respuesta no lleva un correlativo (el spec fija sus claves), así que el simulador empareja respuestas con lecturas **por orden**, lo que vale porque el backend responde cada mensaje en el orden en que llega. Si algún día hace falta correlación exacta, se agrega un `idDeMensaje` que el backend repita.

### D85: sesión persistente y QoS 1

El chip se conecta con `clientId = serial` y `cleanSession=false`; el backend se suscribe como `fito-backend` (fijo a propósito) con `cleanSession=false` y QoS 1. Así, si el backend cae, el broker **guarda** lo publicado con QoS 1 y lo entrega al volver; cada lectura conserva su `marcaDeTiempo` original, de modo que el historial queda completo y en orden aunque `registradoEn` sea posterior (la separación `tomadaEn`/`registradoEn` del dominio, D5, ya lo previó). Es entrega **al menos una vez**: una redelivery tras un corte a mitad de mensaje puede duplicar un diagnóstico; el dominio no deduplica y esta fase no lo agrega.

## 4. La prueba de cambio: `git show --stat` del commit del adaptador

```
ae9e54a feat(mqtt): adaptador de entrada que recibe lecturas del dispositivo por el broker

 pom.xml                                            |   6 +
 .../adaptador/entrada/mqtt/LecturaMqtt.java        |  13 ++
 .../adaptador/entrada/mqtt/RespuestaMqtt.java      |  23 +++
 .../entrada/mqtt/SuscriptorDeLecturasMqtt.java     | 224 +++++++++++++++++++++
 .../fitodiagnostico/config/ConfiguracionMqtt.java  | 129 ++++++++++++
 src/main/resources/application.yml                 |  12 ++
 .../arquitectura/ArquitecturaTest.java             |  17 ++
 7 files changed, 424 insertions(+)
```

**Cero archivos bajo `dominio/` o `aplicacion/`.** Se puede comprobar:

```bash
git diff --stat develop..feature/fase4-mqtt -- src/main/java/com/vivero/fitodiagnostico/dominio src/main/java/com/vivero/fitodiagnostico/aplicacion   # vacío
```

### Qué cambió y qué no

| | Cambió | No cambió |
|---|---|---|
| Dominio (`dominio/`) | nada | modelo, excepciones, `RegistrarLecturaPort`, todos los puertos |
| Aplicación (`aplicacion/`) | nada | `ServicioRegistrarLectura` y los demás casos de uso |
| Adaptadores existentes | nada (ni el controlador HTTP, ni persistencia, ni seguridad, ni WebSocket) | todos |
| Adaptador nuevo | `adaptador/entrada/mqtt/` (3 clases) | |
| Composición | `config/ConfiguracionMqtt` (nueva); `application.yml` (3 propiedades); `pom.xml` (1 dependencia) | las demás clases `Configuracion*` |
| Pruebas | regla ArchUnit nueva + 21 pruebas nuevas | las 816 anteriores, sin editar ninguna |

Lo que se reutilizó sin cambios: el puerto, el `JwtDecoder`, los códigos de error, y por tanto **todo** lo que ocurre después de `registrar` (evaluación, historial, avisos por correo, vista en vivo por WebSocket). Las notificaciones de cambio de estado salieron igual para lecturas MQTT que para HTTP sin que nadie las cableara.

ArchUnit (28 reglas, 2 nuevas): `el_adaptador_mqtt_no_depende_de_otros_adaptadores_de_entrada` y `mqtt_solo_en_adaptadores_y_config` (Paho y Spring Integration no se nombran en dominio ni aplicación). La regla preexistente `los_adaptadores_de_entrada_no_se_conocen_entre_si` ya cubría el nuevo paquete como una rebanada más, sin editarla.

## 5. Cosas que se descubrieron al probar con el broker de verdad

- **La suscripción vencía a los 30 s con un backlog.** Spring Integration suscribe dentro de `connectComplete`, que Paho llama en su único hilo de callbacks. Con sesión persistente el broker empieza a entregar lo acumulado apenas conecta; ese hilo, ocupado esperando el SUBACK, no procesa los mensajes, la cola interna de Paho (10) se llena, el hilo lector se frena y el SUBACK no se lee: "Error subscribing... Tiempo de espera excedido", y recién después se procesaba todo. Se corrigió con `AdaptadorDeEntrada` (en `ConfiguracionMqtt`), una subclase que suscribe en otro hilo. Con la corrección, 19 lecturas acumuladas se registraron en el arranque, sin error. No se vio con Moquette porque la prueba no acumula backlog.
- **`@EnableIntegration`** en `ConfiguracionMqtt` (solo activa con MQTT habilitado) para tener la infraestructura de Spring Integration sin su autoconfiguración.
- **El broker de la prueba es un bean del contexto de prueba**, no un `@BeforeAll`: con `@AfterAll` el broker moría antes que el contexto y el cierre de Paho esperaba 30 s a una desconexión imposible.

## 6. Límites

- Broker anónimo (D83). Para producción: validación del JWT en el broker o certificados con TLS.
- Entrega al menos una vez (D85): posible duplicado ante una redelivery.
- **Un `ERROR_INTERNO` no se reintenta por MQTT.** El mensaje ya se confirmó al broker (QoS 1 confirma la recepción, no el éxito). Con HTTP el simulador reintenta 5xx; por MQTT el error llega como respuesta tardía y la lectura se pierde. Se vio en una prueba con una base H2 que se cerraba mientras el backend aún procesaba mensajes durante el apagado. Evolución: no confirmar (lanzar) ante errores inesperados y dejar que el broker reentregue.
- **El token caduca en la cola.** Si el backend está caído más que la vida del token de dispositivo (1 h por defecto), las lecturas acumuladas llegan con el token vencido y se rechazan con `CREDENCIAL_RECHAZADA`. Para cortes cortos (el caso de la demo) no pasa.
- Persistencia del broker de desarrollo: sin volumen, la sesión persistente vive mientras viva el contenedor (`--rm`).
- Las respuestas se emparejan por orden (D84).

## 7. Cómo se verificó

- `./mvnw -q test`: **839 pruebas, 0 fallos** (816 anteriores + 16 del traductor + 5 con Moquette + 2 ArchUnit). Sin broker externo: Moquette 0.17 corre embebido en el JVM de la prueba (compila y anda con Spring Boot 3.3 y Java 21; no hizo falta HiveMQ).
- Punta a punta con Mosquitto 2 en Docker, el jar con `FITO_MQTT_HABILITADO=true`, un simulador por MQTT y otro por HTTP a otra planta al mismo tiempo. Backend apagado 20 s con el simulador publicando: al volver, el broker entregó lo acumulado y el historial quedó con `tomadaEn` original (por ejemplo `tomadaEn 02:15:49`, `registradoEn 02:16:20`, origen `DISPOSITIVO`). La salida real está en el reporte de la rama.
