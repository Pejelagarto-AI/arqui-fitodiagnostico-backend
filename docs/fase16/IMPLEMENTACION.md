# Fase 16: el periodo sale del reloj

Cuaderno de implementación (como `docs/fase15/`). Pedido: Santiago, 27-nov. Desde la fase 8 el periodo (DIA/NOCHE) lo decidía la oscuridad: la lectura y las anteriores llevaban al menos 2 h por debajo de 100 lux. Dos fallas: de día, una planta en un rincón oscuro quedaba NOCHE y nunca se avisaba que le faltaba luz; de noche, una lámpara encendida la volvía DIA. Decisión: **el periodo sale del reloj, en franja fija y hora de Bogotá: de 06:00 (incluido) a 18:00 (excluido) es DIA; el resto, NOCHE.** La oscuridad ya no decide el periodo. El contrato HTTP no cambia. El firmware no se toca (solo lee `periodo` del 201).

## Qué hay

| Pieza | Dónde | Capa |
|---|---|---|
| La franja: constantes, constructor con zona y horas, `periodo(Instant)` | `dominio/servicio/ReglaDeLuzAcumulada.java` | dominio |
| El evaluador decide el periodo con la hora de la lectura | `dominio/servicio/EvaluadorDeEstado.java` | dominio |
| `Diagnostico` admite NOCHE sin exposición | `dominio/modelo/Diagnostico.java` | dominio |
| Zona y franja desde la configuración | `config/ConfiguracionDominio.java`, `application.yml` (`fitodiagnostico.zona-horaria`, `fitodiagnostico.dia.inicio/fin`) | composición |

## La regla

1. **Periodo.** DIA si la hora local de la lectura (zona de la regla, por defecto `America/Bogota`) está en `[06:00, 18:00)`; NOCHE en el resto. Solo depende de `tomadaEn`: no mira la luz ni el historial.
2. **De noche** la luz no entra al estado global ni a las recomendaciones (D121, sin cambio). **De día** la luz cuenta siempre: una planta oscura todo el día queda `BAJO` y en riesgo.
3. **Sin historial,** el periodo también sale del reloj (la primera lectura de la noche es NOCHE). El diagnóstico suelto sin hora (RF-F6, `evaluar(especie, medicion)`) no tiene reloj que consultar y dice DIA (D207).
4. Lo demás de la regla de luz no cambió: ventana de 24 h, tramos con tope de 30 min, horas de luz, lux efectivo, y sin historial la luz se clasifica con la lectura del momento. `horasEnOscuridad` sigue en la exposición como dato para el front, pero ya no decide nada.

## Decisiones

**D205. Periodo por reloj, no por oscuridad.** La oscuridad mide el cuarto, no el día: una suculenta en un rincón (20 lux a las 2 pm) llevaba horas "a oscuras" y quedaba dormida sin que nadie le dijera que le falta luz, que es justo lo que el sistema debe decir. Y de noche una lámpara de 300 lux cortaba la racha y la volvía DIA, y al apagarla tardaba 2 h en dormir. El día y la noche son del planeta, no de la matera. `OSCURIDAD_PARA_NOCHE` (D126) se eliminó: quedó sin uso. `periodo(ExposicionLuminica)` se reemplazó por `periodo(Instant)`.

**D206. Franja fija y no amanecer calculado.** Bogotá está a 4,6 °N: el amanecer y el atardecer se mueven unos 20 a 30 min en todo el año (el día va de unas 11 h 45 a 12 h 15). Calcular el sol pide latitud, longitud, una fórmula astronómica y pruebas por fecha, para ganar una precisión que la planta no nota: la regla solo decide cuándo la luz deja de contar en contra. 06:00 a 18:00 es simple, se explica en una frase, se prueba en cuatro bordes y es lo que Santiago pidió. La zona entra como `ZoneId` (por defecto `ZONA_POR_DEFECTO`, `America/Bogota`) y no como un offset, para no depender de UTC-5 escrito a mano. Las horas y la zona se leen de `application.yml` en `ConfiguracionDominio`; el dominio no lee configuración, las recibe por constructor. Se conservó el constructor sin argumentos para quien arma la regla sin configuración (`new ReglaDeLuzAcumulada()` en pruebas y en `EvaluadorDeEstado`). Los bordes: 06:00 es DIA, 18:00 es NOCHE.

**D207. El periodo no necesita historial, salvo el diagnóstico suelto sin hora.** Con la regla vieja el periodo era una consecuencia de la exposición, así que sin historial siempre era DIA y `Diagnostico` lo exigía (NOCHE implicaba `exposicion != null`). Con el reloj, el periodo existe desde la primera lectura: se quitó esa validación del constructor de `Diagnostico`, y NOCHE con `exposicion == null` es válido (la luz se evalúa instantánea y, de noche, no cuenta). La única excepción es `EvaluadorDeEstado.evaluar(especie, medicion)` (RF-F6), que fabricaba `Instant.now()` solo para cumplir la firma: ahí el reloj del servidor no es la hora de ninguna lectura, y una consulta suelta hecha de noche daría un resultado distinto según cuándo se pregunte. Se queda en DIA, como siempre, y esto está documentado en el método y en `Periodo`.

**D208. Los diagnósticos guardados no se recalculan.** Se reconstruyen desde su propia fila (D203): los de antes de la fase 16 conservan el periodo que tenían. El cambio vale desde la siguiente lectura. Puede haber, por una vez, una transición de estado al cambiar la regla; el aviso lleva motivo `PERIODO` si el estado cambió solo por el periodo.

## La prueba de cambio

Dos commits de código, separados a propósito, como en las fases 8 y 15:

| Commit | Qué | Archivos |
|---|---|---|
| `feat(dominio)` | La regla nueva | `dominio/servicio/ReglaDeLuzAcumulada`, `dominio/servicio/EvaluadorDeEstado`, `dominio/modelo/Diagnostico`, `dominio/modelo/Periodo` (solo documentación). Pruebas del dominio y las que asumían la oscuridad |
| `chore(config)` | Leer la zona y la franja de `application.yml` | `config/ConfiguracionDominio`, `application.yml`, `ConfiguracionDominioTest` |

El primero compila y pasa las pruebas sin tocar nada fuera del dominio ni de las pruebas: la aplicación sigue llamando `evaluar(especie, medicion, tomadaEn, luzAnterior, humedadAnterior)` y `ConfiguracionDominio` sigue armando el evaluador con el constructor de tres argumentos, que usa la franja por defecto. El segundo no cambia ninguna clase del dominio.

Lo que **no** cambió: el puerto `RepositorioDiagnosticos`, `ServicioMediciones`, `MapaDeAnimo` (lee `diagnostico.periodo()`, no sabe cómo se decide), `DetectorDeCambioDeEstado` (el motivo `PERIODO` sigue saliendo de comparar periodos), los controladores, las migraciones, el resumen de luz por hora (fase 12), el simulador y el firmware.

## Pruebas

- Dominio, sin servidor ni base (`ReglaDeLuzAcumuladaTest`): 05:59 NOCHE, 06:00 DIA, 17:59 DIA, 18:00 NOCHE y medianoche NOCHE; una lectura en UTC que en Bogotá es de noche (02:00Z son las 21:00) y los bordes en UTC (10:59Z, 11:00Z, 22:59Z, 23:00Z); tres horas de oscuridad a las 12:00 de Bogotá siguen siendo DIA; zona y horas por constructor (Madrid); una franja invertida se rechaza; el periodo no depende del historial.
- Evaluador (`EvaluadorConLuzAcumuladaTest`): oscuro a las 14:00 es DIA con luz `BAJO` y estado `CRITICO` con una recomendación; lámpara a las 23:00 es NOCHE y `SALUDABLE`; sin historial de noche la luz no cuenta (`exposicion` nula, base instantánea); con la serie vacía de día todo es como en el corte 1.
- Modelo (`LuzAcumuladaEnElModeloTest`): NOCHE sin exposición es válido.
- Configuración (`ConfiguracionDominioTest`, 2): valores por defecto y otra zona con otra franja.
- Ajustadas las que asumían la oscuridad: `ServicioMedicionesTest` (la planta que duerme, con la lectura a las 22:00 de Bogotá; el aviso con motivo `PERIODO` ahora lo causa el reloj al cruzar las 18:00 y las 06:00), `LecturasDeDispositivoIntegracionTest` (la marca de la lectura nocturna), `ResumenPorHoraContraCrudasTest` (el resumen y las crudas se comparan por `horasEnOscuridad`, que sigue existiendo; el periodo ya no es lo que difieren).
- Suite completa (`./mvnw -B test`, JDK 21): 1115 pruebas, 0 fallos (1107 en `develop` más 8 nuevas).

## Límites

- La franja es la misma para todas las plantas y todo el año. Otra ciudad se resuelve por configuración (`FITO_ZONA_HORARIA`, `FITO_DIA_INICIO`, `FITO_DIA_FIN`), pero una instancia tiene una sola zona: plantas en dos ciudades pedirían la zona en la planta o en el usuario.
- La franja no es por especie. Hay plantas que viven mejor con otro fotoperiodo; hacerlo por especie es una columna del catálogo, como las horas de luz (D125).
- Una luz artificial encendida de día cuenta como luz y una ventana tapada de noche no despierta a nadie: es lo que se quería. Pero una planta con luz de cultivo programada de noche (fotoperiodo invertido) queda NOCHE y la luz no le cuenta.
- A las 06:00 y a las 18:00 el estado puede cambiar de golpe aunque la planta no haya cambiado (la luz entra o sale del cómputo). Ya pasaba con la oscuridad y el aviso lo dice con motivo `PERIODO`; ahora ocurre a horas fijas y puede llegar un aviso por planta, a esa hora, cada día que la luz acumulada sea mala.
- La hora que manda es `tomadaEn`, la que informa el dispositivo. Si el reloj de la matera está mal, el periodo está mal. Es el mismo supuesto que tiene toda la regla de luz.

## Para el documento de arquitectura

Qué cambió en el dominio: la definición de periodo. Antes se deducía de la luz (2 h de oscuridad), ahora de la hora de la lectura (06:00 a 18:00, Bogotá). La regla sigue en `ReglaDeLuzAcumulada`, que ahora recibe la zona horaria y las horas de la franja por constructor, y el diagnóstico ya no exige historial para saber que es de noche.

Qué no cambió: el dominio sigue sin leer configuración ni reloj del sistema: la hora llega con la lectura y la zona llega por constructor. La composición (`config/ConfiguracionDominio`) lee `application.yml` y se las pasa. La aplicación, los puertos, los adaptadores, la mascota y la detección de cambios de estado no se tocaron.

Por qué demuestra la hexagonal: cambiar de dónde sale una decisión de negocio (de la medida a la hora) entró en un commit que solo toca el dominio y pasa las pruebas solo; llevar la zona a la configuración fue otro commit que no toca el dominio. El mapa de ánimo de la mascota y el aviso con motivo `PERIODO` siguieron funcionando sin cambios porque dependían del concepto de periodo y no de cómo se calculaba.
