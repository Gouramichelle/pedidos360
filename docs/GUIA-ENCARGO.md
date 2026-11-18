# Guía para estudiar el encargo

Todo lo que hay que saber del código entregado, desde lo primero que se
construyó hasta lo último. Está pensada para estudiar: cada sección explica
**qué se hizo**, **dónde está** y **por qué se decidió así**, que es lo que se
pregunta en la defensa.

Lo nuevo de esta entrega es Kafka. Lo anterior —identidad, API Gateway,
RabbitMQ— sigue formando parte del sistema y se incluye entero, porque el
docente puede preguntar por cualquier cosa.

---

## Índice

| | |
|---|---|
| [1. El sistema de un vistazo](#1-el-sistema-de-un-vistazo) | Qué es Pedidos360 |
| [2. Kafka: la arquitectura](#2-kafka-la-arquitectura) | Tres Zookeeper, tres brokers |
| [3. Kafka: los dos tópicos](#3-kafka-los-dos-topicos) | Particiones, réplicas, retención |
| [4. Kafka: productores y consumidores](#4-kafka-productores-y-consumidores) | Quién escribe y quién lee |
| [5. Kafka: grupos de consumidores](#5-kafka-grupos-de-consumidores) | Por qué tres grupos |
| [6. Kafka: manejo de errores](#6-kafka-manejo-de-errores) | Reintentos y DLT |
| [7. El administrador de la plataforma](#7-el-administrador-de-la-plataforma) | Los endpoints |
| [8. RabbitMQ](#8-rabbitmq) | Lo de la entrega anterior |
| [9. Identidad y seguridad](#9-identidad-y-seguridad) | Azure AD, JWT, roles |
| [10. API Gateway](#10-api-gateway) | El API Manager |
| [11. Preguntas que pueden caer](#11-preguntas-que-pueden-caer) | Con su respuesta |

---

## 1. El sistema de un vistazo

Pedidos360 son **seis microservicios** en Spring Boot 3.3 con Java 21, más un
frontend en Angular, desplegados en AWS.

| Servicio | Puerto | Qué hace |
|---|---|---|
| ms-orders | 8080 | Los pedidos. Es la fuente de verdad |
| ms-catalog | 8081 | Productos y stock |
| ms-notify | 8082 | Consume los comandos de RabbitMQ |
| ms-report | 8083 | Indicadores, desde Kafka |
| ms-audit | 8084 | Trazabilidad, desde Kafka |
| ms-mqadmin | 8086 | Administra RabbitMQ y Kafka |

**La idea que ordena todo el diseño**: hay dos sistemas de mensajería y cada
uno resuelve un problema distinto.

> **Un evento** cuenta algo que ya pasó. Puede interesarle a muchos, o a
> ninguno. Quien lo publica no sabe ni le importa quién lo lee. Eso es Kafka.
>
> **Un comando** pide que alguien haga algo. Tiene un único destinatario, hay
> que saber si se hizo, y si falla hay que reintentarlo o guardarlo aparte.
> Eso es RabbitMQ.

Crear un pedido publica **un evento** a Kafka (`orders.events`) y **tres
comandos** a RabbitMQ (correo, cocina, facturación). Una sola acción del
usuario, dos mecanismos distintos según lo que haga falta.

---

## 2. Kafka: la arquitectura

`infra/kafka/docker-compose.yml` levanta **tres Zookeeper y tres brokers**.

### Por qué tres Zookeeper

Zookeeper elige un líder por mayoría. **La mayoría de tres es dos**, así que el
ensemble sobrevive a perder un nodo. Con dos nodos la mayoría también sería
dos, así que perder uno dejaría el ensemble sin quórum: no serviría de nada.

Por eso los ensembles van siempre en número impar.

### Por qué tres brokers

Es la misma aritmética. Tres brokers permiten topicos con **factor de
replicación 3** y `min.insync.replicas=2`: cada partición vive en los tres, y
se sigue aceptando escritura mientras queden dos copias sincronizadas.

Con un solo broker, pedir tres réplicas no es desaconsejable: **es imposible**.
El factor de replicación nunca puede superar el número de brokers.

### Los listeners, que es la parte que confunde

Cada broker declara **dos listeners**:

```
INTERNAL  kafka1:29092   entre brokers, y para Kafka UI
EXTERNAL  <ip>:9092      para los microservicios
```

La razón: un broker le entrega al cliente la dirección por la que debe volver a
conectarse. Si se anuncia como `kafka1`, ese nombre solo lo resuelve quien esté
dentro de la red de Docker. Los microservicios, que corren fuera, no sabrían a
dónde ir.

Por eso Kafka UI —que vive en la misma red— usa los nombres de contenedor, y
los microservicios usan la IP de la instancia.

| | Dónde |
|---|---|
| Local | `infra/local/docker-compose.yml` |
| Nube | `infra/kafka/docker-compose.yml` |
| Kafka UI | `infra/kafka/docker-compose.kafka-ui.yml` |

---

## 3. Kafka: los dos tópicos

| Tópico | Particiones | Réplicas | Retención | Limpieza |
|---|---|---|---|---|
| `orders.events` | 3 | 3 | 5 días | delete |
| `audit.timeline` | 3 | 3 | 21 días | **compact,delete** |

Ambos con `min.insync.replicas=2`.

Los declara el código al arrancar, no un script: `TopicBuilder` en
`KafkaProducerConfig` (orders) y `KafkaConfig` (audit). `auto.create.topics`
está **desactivado** a propósito, para que nada se cree por accidente con la
configuración por defecto.

### Por qué tres particiones

Las particiones fijan **el paralelismo máximo**: un grupo de consumidores nunca
tendrá más consumidores útiles que particiones tiene el tópico. Con tres, tres
instancias del mismo servicio pueden repartirse el trabajo.

**El detalle que importa**: la clave de partición es el `orderId`. Kafka
garantiza el orden *dentro* de una partición, así que todos los eventos de un
mismo pedido caen siempre en la misma y llegan en orden. Entre pedidos
distintos no hay orden garantizado, y no hace falta.

### Por qué tres réplicas

Cada partición vive en los tres brokers: uno es líder y atiende, los otros dos
copian. Si cae el líder, uno de los otros toma el relevo y **no se pierde nada**.

Con `min.insync.replicas=2`, si quedan menos de dos copias al día el tópico
deja de aceptar escrituras. Es deliberado: **preferible rechazar un mensaje que
aceptarlo con una sola copia y perderlo** si ese broker también cae.

### Por qué `audit.timeline` está compactado

`compact` significa que el broker conserva **el último mensaje de cada clave** y
descarta los anteriores. La clave es el `orderId`, así que el tópico mantiene
el estado final de cada pedido indefinidamente dentro de la ventana.

Eso lo hace servir para algo que un tópico normal no puede: **reconstruir una
proyección desde cero** leyéndolo entero una sola vez, sin arrastrar todo el
historial de eventos.

`compact,delete` combina las dos: compacta por clave **y además** borra lo que
pase de 21 días.

---

## 4. Kafka: productores y consumidores

```
ms-orders ──publica──> orders.events ──┬──> ms-audit   (audit-service)
                                       └──> ms-report  (report-service)

ms-audit  ──publica──> audit.timeline ────> ms-report  (report-service-timeline)
```

### Los productores

| Quién | Tópico | Dónde |
|---|---|---|
| ms-orders | `orders.events` | `messaging/OrderEventPublisher` |
| ms-audit | `audit.timeline` | `messaging/AuditTimelinePublisher` |

Ambos publican **DTOs tipados**, no mapas ni strings: `OrderEventPayload` y
`AuditTimelinePayload`, serializados con `JsonSerializer`.

`orders.events` va envuelto en un `EventEnvelope` con seis campos: `type`,
`eventId`, `timestamp`, `traceId`, `correlationId` y el `payload`.

El `eventId` identifica el mensaje de forma única, lo que permite detectar un
reproceso. El `correlationId` es lo que permite seguir una acción del usuario a
través de todo el sistema.

### Los consumidores

Los tres usan `@KafkaListener` y **separan la lógica de negocio de la
infraestructura de mensajería**: el listener traduce el mensaje y delega; no
mezcla reglas de negocio con el manejo del broker.

| Quién | Lee | Para qué |
|---|---|---|
| ms-audit | `orders.events` | Persiste el registro de auditoría |
| ms-report | `orders.events` | Mantiene la proyección de estados |
| ms-report | `audit.timeline` | Quién tocó cada pedido y desde dónde |

### Por qué los DTO están duplicados en cada servicio

Es a propósito, y es una buena pregunta de defensa. Si los dos servicios
dependieran de la misma clase compartida, cambiarla obligaría a desplegarlos a
la vez — que es justo lo que la mensajería viene a evitar.

**El contrato es el formato del mensaje, no el tipo de Java.**

---

## 5. Kafka: grupos de consumidores

Hay **tres grupos**:

| Grupo | Lee | Por qué separado |
|---|---|---|
| `audit-service` | orders.events | Auditoría necesita *todos* los eventos |
| `report-service` | orders.events | Reportes también los necesita todos |
| `report-service-timeline` | audit.timeline | Otro caso de uso, otro ritmo |

**La regla que hay que entender**: dentro de un grupo, cada mensaje va a **un
solo** consumidor. Entre grupos distintos, **cada grupo recibe todo**.

Por eso auditoría y reportes están en grupos separados aunque lean el mismo
tópico: si compartieran grupo, cada evento iría a uno solo de los dos y la
mitad del trabajo quedaría sin hacer.

Y por eso el listener de `audit.timeline` tiene su propio grupo aunque viva en
el mismo servicio: es otro caso de uso, con su propio avance.

**Para qué sirve tener varios consumidores en un mismo grupo**: ahí sí es
balanceo. Tres instancias de ms-report en el grupo `report-service` se
repartirían las tres particiones, una cada una. Si una se cae, Kafka reasigna
sus particiones a las otras dos. Eso es la resiliencia.

---

## 6. Kafka: manejo de errores

Los tres consumidores tienen la misma política, en `KafkaConfig` (audit) y
`KafkaConsumerConfig` (report):

```java
new DefaultErrorHandler(deadLetterPublishingRecoverer, new FixedBackOff(1000L, 3))
```

**Tres reintentos con un segundo de espera.** Si sigue fallando, el mensaje se
publica en un tópico de descarte con el nombre
`<tópico>.<grupo>.DLT`, junto con los metadatos del error.

Hay cuatro tópicos de descarte:

```
orders.events.audit-service.DLT
orders.events.report-service.DLT
audit.timeline.report-service-timeline.DLT
```

Además:

- **Deserialización defensiva**: `ErrorHandlingDeserializer` envuelve al
  deserializador real. Un mensaje con JSON corrupto no tumba al consumidor:
  se convierte en un error manejable que va al DLT.
- **Validación de payload**: `AuditTimelineListener` rechaza un registro sin
  pedido o sin fecha. No tiene sentido reintentar algo que siempre va a
  fallar igual, así que se descarta directo.
- **Logging con contexto**: cada consumo deja una línea con el pedido, el
  actor y el `correlationId`.

### La idea de fondo

Hay dos clases de error y se tratan distinto:

| | Qué es | Qué se hace |
|---|---|---|
| **Transitorio** | La base no respondió, la red falló | Reintentar: puede funcionar |
| **Del mensaje** | Viene mal formado, le falta un campo | Al DLT: reintentar daría igual |

---

## 7. El administrador de la plataforma

`ms-pedidos360-mqadmin` administra **las dos** plataformas. Todos los endpoints
exigen rol **Admin**.

### Kafka

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/mqadmin/kafka/topics` | Lista los tópicos |
| POST | `/api/mqadmin/kafka/topics` | Crea uno, con particiones, réplicas y configuración |
| GET | `/api/mqadmin/kafka/topics/{n}` | Lo describe: particiones, líder, réplicas, ISR |
| PATCH | `/api/mqadmin/kafka/topics/{n}/config` | Cambia configuración **en caliente** |
| POST | `/api/mqadmin/kafka/topics/{n}/partitions` | Amplía particiones |
| DELETE | `/api/mqadmin/kafka/topics/{n}` | Lo elimina |
| GET | `/api/mqadmin/kafka/consumer-groups` | Lista los grupos |
| GET | `/api/mqadmin/kafka/consumer-groups/{g}` | Estado, miembros y **retraso por partición** |
| DELETE | `/api/mqadmin/kafka/consumer-groups/{g}` | Elimina un grupo parado |

### RabbitMQ

Ocho endpoints bajo `/api/mqadmin/` para colas, exchanges y bindings.

### Dos cosas que conviene saber explicar

**Las particiones solo se pueden aumentar.** Kafka no permite reducirlas,
porque los mensajes ya repartidos no se podrían reubicar sin romper el orden
por clave.

**El retraso es la métrica que importa.** Es la diferencia entre el último
offset escrito y el último confirmado por el grupo: cuántos mensajes hay
publicados que ese grupo todavía no procesó. Un retraso que crece de forma
sostenida avisa de que un consumidor no da abasto **antes** de que se note en
la aplicación.

### La decisión de diseño

`KafkaAdminService` encapsula `AdminClient` igual que `RabbitAdminService`
encapsula AMQP. **Los controladores no importan nada de `org.apache.kafka` ni
de `org.springframework.amqp`**: solo traducen HTTP a llamadas del servicio.

Se comprueba así, y devuelve cero:

```bash
grep -rn "^import org.apache.kafka\|^import org.springframework.amqp" \
  ms-pedidos360-mqadmin/src/main/java/cl/pedidos360/mqadmin/web/
```

---

## 8. RabbitMQ

De la entrega anterior, y sigue en pie.

**Clúster de dos nodos** (`infra/mq/docker-compose.yml`), con cookie de Erlang
compartida y descubrimiento por configuración.

### Las seis colas

| Cola | Para qué | Respaldo |
|---|---|---|
| `q.cmd.email` | Notificar al cliente | `q.cmd.email.dlq` |
| `q.cmd.kitchen` | Ticket de cocina | `q.cmd.kitchen.dlq` |
| `q.cmd.invoice` | Documento de cobro | `q.cmd.invoice.dlq` |

Las de comando tienen `x-message-ttl` de **30 segundos**; las muertas, 14 días
y `x-max-length` de 1000.

### Los exchanges

| Exchange | Tipo | Lo usa |
|---|---|---|
| `cmd.direct` | direct | Cocina: clave exacta, un destinatario |
| `cmd.topic` | topic | Correo y facturación: patrón con comodín |
| `cmd.dead.dlx` | direct | A donde el broker manda lo no entregado |

### Confirmación manual

`AcknowledgeMode.MANUAL` con tres salidas, todas en una sola clase
(`ConfirmacionDeMensajes`) para que la política sea idéntica en las tres colas:

- **ACK** si el trabajo terminó bien
- **NACK con requeue** si el error es recuperable y quedan intentos
- **NACK sin requeue** → a la cola muerta

### Los dos caminos a la cola muerta

El broker deja escrito cuál fue, en el header `x-death`:

| `reason` | Qué pasó | Quién lo decidió |
|---|---|---|
| `rejected` | El mensaje venía mal formado | Nuestro código |
| `expired` | Nadie lo consumió a tiempo | El broker, solo |

Es la diferencia entre **"el mensaje estaba malo"** y **"el servicio no estaba
disponible"**. Dos problemas distintos que se resuelven distinto.

### Qué pasa si se cae un nodo del clúster

Respuesta honesta, medida tirando un nodo a propósito: el *failover de
conexión* funciona, pero **la cola queda indisponible**. Son colas clásicas sin
espejado y viven en el nodo donde se declararon.

Con dos nodos las quorum queues no lo arreglan: el quórum necesita mayoría, y
la mayoría de dos es dos. Haría falta un tercer nodo.

---

## 9. Identidad y seguridad

### Azure AD como IDaaS

| | |
|---|---|
| Inquilino | `actividadcloudnative` |
| Tenant ID | `2d0922c9-f3ff-4709-b9b7-0c4b54529639` |
| Aplicación | `Pedidos360`, ID `d0120ca1-1d51-493b-...` |

El frontend usa **Authorization Code con PKCE**, el flujo que corresponde a una
aplicación de página única, que no puede guardar un secreto.

La aplicación **no tiene usuarios propios ni guarda contraseñas**: la identidad
se delega entera.

### Los cuatro roles

`Admin`, `Operador`, `Cliente`, `Auditor`. Viajan en el claim `roles` del
token y se aplican con `@PreAuthorize` en cada controlador.

### La validación en dos capas

1. **El API Gateway** valida firma, emisor, vigencia y audiencia antes de
   enrutar. Lo que no pasa, no llega al backend.
2. **Cada microservicio** vuelve a validar y además comprueba el rol.

Parece redundante y no lo es: el Gateway no sabe nada de roles, y un
microservicio no debe confiar en que nadie lo llame por dentro de la red.

### El detalle de las dos audiencias

`AZURE_AUDIENCES` lleva **dos valores**: `api://<id>` y el `<id>` pelado. Azure
emite tokens v1 con el primero y v2 con el segundo. Aceptar solo uno produce
401 silenciosos si cambia `requestedAccessTokenVersion` en el manifiesto.

Costó encontrarlo: el login funcionaba y todas las llamadas daban 401.

---

## 10. API Gateway

El **API Manager** del caso es un API Gateway HTTP de AWS.

| | |
|---|---|
| Invoke URL | `https://0b7fhgzl22.execute-api.us-east-1.amazonaws.com` |
| Rutas | **12**: 6 con validador JWT + 6 `OPTIONS` sin él |
| Validador | Emisor del inquilino, dos audiencias |

### Por qué 12 rutas y no 6

Seis flujos (orders colección, orders detalle, catalog, report, audit,
mqadmin), cada uno con dos rutas: la real **con** validador, y la de
verificación previa (`OPTIONS`) **sin** él.

La petición de verificación que manda el navegador viaja **sin credencial**, así
que una ruta que exigiera token la rechazaría y la llamada real nunca se haría.

### CORS, con mínimo privilegio

```
access-control-allow-origin    https://<ip-frontend>:8085
access-control-allow-methods   DELETE, GET, PATCH, POST, PUT
access-control-allow-headers   authorization, content-type
access-control-max-age         3600
```

**Un solo origen**, no un comodín. **Cinco métodos**, los que la aplicación usa
de verdad —`PATCH` lo usa el cambio de estado del pedido—. Y `OPTIONS` **no**
aparece: esa cabecera declara los métodos de la petición *real*, no los de la
verificación.

---

## 11. Preguntas que pueden caer

**¿Por qué Kafka y RabbitMQ, y no uno solo?**

> Resuelven problemas con requisitos opuestos. El evento necesita muchos
> lectores independientes y un historial releíble: eso es el log particionado
> de Kafka. El comando necesita un solo ejecutor, con confirmación, reintento y
> descarte: eso es la cola con DLQ de RabbitMQ.

**¿Por qué tres particiones y no una, o diez?**

> Tres es el paralelismo máximo que puede aprovechar un grupo, y coincide con
> el número de brokers, así que el liderazgo queda repartido: una partición
> lidera en cada uno. Con una no habría paralelismo. Con diez habría más
> ficheros y más trabajo de coordinación sin consumidores que lo aprovechen.

**¿Qué pasa si se cae un broker?**

> Las particiones que lideraba pasan a liderarlas otro de los que tienen
> réplica, y el sistema sigue. Con `min.insync.replicas=2` se sigue aceptando
> escritura porque quedan dos copias. Si cayeran dos, el tópico dejaría de
> aceptar escrituras a propósito: es preferible rechazar que aceptar algo que
> se puede perder.

**¿Por qué desactivar la creación automática de tópicos?**

> Porque un tópico creado por accidente nace con la configuración por defecto:
> una partición, una réplica, retención genérica. Es mejor que un productor
> falle avisando a que publique durante semanas en un tópico sin réplicas.

**¿Para qué sirve el `correlationId`?**

> Para seguir una sola acción del usuario a través de todo el sistema. Crear un
> pedido publica un evento a Kafka y tres comandos a RabbitMQ: los cuatro
> llevan el mismo `correlationId`, así que en los logs se ve que vinieron de la
> misma acción.

**¿Qué diferencia hay entre `compact` y `delete`?**

> `delete` borra por antigüedad: lo que pase de la retención se va. `compact`
> borra por clave: conserva el último mensaje de cada una y descarta los
> anteriores. `orders.events` usa `delete` porque es un historial de hechos.
> `audit.timeline` usa las dos porque interesa el último estado de cada pedido,
> pero tampoco indefinidamente.

**¿Por qué el administrador no tiene productor ni consumidor?**

> Porque administra la plataforma, no participa en el flujo. Usa `AdminClient`,
> que es la API de administración. Si tuviera un productor, sería un
> microservicio más del sistema, no una herramienta.

**¿Esto corre en la nube o en local?**

> La topología es idéntica: los mismos tres Zookeeper, los mismos tres brokers,
> los mismos dos tópicos con tres particiones y tres réplicas, levantados con
> el mismo docker-compose. Lo único que cambia es cómo se anuncian los brokers,
> porque en local los microservicios corren en el host.
