# Guía para la presentación

Para EP6, que es **individual**, dura **entre 5 y 10 minutos** y se evalúa
sobre nueve indicadores. Esta guía está ordenada por lo que pide la pauta, con
lo que hay que decir y lo que hay que mostrar en cada punto.

> **Antes de entrar**: deja corriendo `docker compose -f infra/local/docker-compose.yml up -d`,
> los seis microservicios, el frontend, y abiertas tres pestañas: la
> aplicación, **Kafka UI** (`http://localhost:8090`) y la consola de RabbitMQ
> (`http://localhost:15672`, `guest`/`guest`).

---

## Cómo se reparten los puntos

| % | Indicador | Dónde se gana |
|---|---|---|
| 15% | Administrar Kafka: particiones, réplicas, retención, grupos | Hablando, con Kafka UI delante |
| 15% | Métricas: offsets, lag, distribución | Kafka UI, pestaña Consumers |
| 15% | Casos de uso que requieren streaming | Hablando |
| 12% | Flujo de producción y consumo entre microservicios | Demostrando |
| 12% | Reacción a eventos en tiempo real | Demostrando |
| 10% | Arquitectura con Docker Compose: 3 Zookeeper, 3 brokers | Kafka UI, pestaña Brokers |
| 10% | Justificar 2 tópicos con 3 particiones y 3 réplicas | Hablando |
| 6% | Diseño de los eventos | Hablando |
| 5% | Kafka UI | Mostrándolo |

**El 45% se gana explicando**, no demostrando. Conviene prepararse las
explicaciones más que la demo.

---

## Guion sugerido (8 minutos)

### 0:00 — Apertura (30 s)

> Pedidos360 son seis microservicios en Spring Boot sobre AWS. Por dentro usa
> dos sistemas de mensajería con roles distintos: RabbitMQ para comandos y
> Kafka para eventos. Hoy me voy a centrar en Kafka, que es lo que integramos
> en esta etapa.

**No te extiendas aquí.** El tiempo está justo y los puntos están en Kafka.

---

### 0:30 — La arquitectura (10%)

**Muestra**: Kafka UI → pestaña **Brokers**. Se ven los tres.

**Di**:

> El clúster son tres nodos Zookeeper y tres brokers, levantados con Docker
> Compose. El número tres no es arbitrario en ninguno de los dos casos.
>
> Zookeeper elige líder por mayoría, y la mayoría de tres es dos: el ensemble
> sobrevive a perder un nodo. Con dos nodos la mayoría también sería dos, así
> que perder uno lo dejaría sin quórum. Por eso los ensembles van en número
> impar.
>
> Tres brokers es la misma aritmética aplicada a los datos: permiten
> replicación tres con `min.insync.replicas` dos, que es lo que deja seguir
> aceptando escrituras cuando se cae uno.

**Si preguntan por los listeners**:

> Cada broker declara dos. Uno interno, con el nombre del contenedor, que usan
> los brokers entre sí y Kafka UI. Otro externo, con la IP de la instancia, que
> usan los microservicios. Hace falta porque el broker le entrega al cliente la
> dirección por la que debe volver a conectarse, y un nombre de contenedor solo
> lo resuelve quien está dentro de la red de Docker.

---

### 1:30 — Los dos tópicos (10%)

**Muestra**: Kafka UI → **Topics**. Se ven `orders.events` y `audit.timeline`,
ambos con 3 particiones y 3 réplicas. Entra en uno y muestra las particiones.

**Di**:

> Son dos tópicos con tres particiones y tres réplicas cada uno.
>
> Las particiones fijan el paralelismo máximo: un grupo nunca tendrá más
> consumidores útiles que particiones tiene el tópico. Con tres, tres
> instancias del mismo servicio pueden repartirse el trabajo. Y como la clave
> de partición es el id del pedido, todos los eventos de un mismo pedido caen
> en la misma partición y conservan su orden.
>
> Las réplicas son resiliencia: cada partición vive en los tres brokers, uno
> lidera y los otros copian. Si cae el líder, otro toma el relevo sin pérdida.
> Con `min.insync.replicas` en dos, si quedan menos de dos copias al día el
> tópico deja de aceptar escrituras. Es deliberado: prefiero rechazar un
> mensaje a aceptarlo con una sola copia y perderlo.

**El detalle que suma**: señala que el liderazgo está repartido.

> Fíjense en que la partición 0 la lidera el broker 1, la 1 el broker 2 y la 2
> el broker 3. La carga queda distribuida sola.

---

### 2:30 — El diseño de los eventos (6%)

**Muestra**: Kafka UI → `orders.events` → **Messages**. Abre uno.

**Di**:

> El evento va envuelto en un sobre con el tipo de evento, un `eventId` que lo
> identifica de forma unica, la marca de tiempo, un `traceId`, un
> `correlationId` y el payload.
>
> El `correlationId` es el que permite seguir una sola acción del usuario por
> todo el sistema. Crear un pedido publica un evento a Kafka y tres comandos a
> RabbitMQ: los cuatro llevan el mismo, así que en los logs se ve que vinieron
> de la misma acción.
>
> El payload lleva el pedido, el cliente, quién hizo la acción, el estado
> anterior, el nuevo, el total y cuándo pasó. Con el estado anterior y el nuevo
> en el mismo mensaje, un consumidor puede reconstruir la transición sin
> consultar a nadie, que es justamente lo que hace que estén desacoplados.

---

### 3:15 — El flujo entre microservicios (12%)

**Muestra**: crea un pedido en la aplicación. Vuelve a Kafka UI.

**Di**:

> ms-orders es el único que publica en `orders.events`. Dos servicios lo
> consumen sin saber el uno del otro: auditoría persiste el registro de
> trazabilidad, y reportes mantiene una proyección del estado de cada pedido.
>
> Ninguno de los dos le pregunta nada a ms-orders. Se alimentan solos del
> tópico. Si mañana quisiera un cuarto servicio que reaccione a los pedidos, lo
> conecto al mismo tópico y ms-orders no cambia una línea.
>
> Y hay un segundo salto: ms-audit republica cada evento en `audit.timeline`
> con forma de registro de trazabilidad, y ms-report consume ese otro tópico
> para saber quién tocó cada pedido y desde dónde.

---

### 4:15 — Reacción en tiempo real (12%)

**Muestra**: la aplicación en **Reportes** y **Auditoría**, con el pedido que
acabas de crear ya reflejado.

**Di**:

> Esto es lo que acaba de pasar, sin que nadie llame a nadie. El pedido se creó
> hace unos segundos y los indicadores ya lo incluyen, y en auditoría aparece
> su evento. Son dos servicios distintos, con su propia base, actualizándose
> solos a partir del flujo.

**Si quieres lucirte**: ten el log de ms-report a la vista. Se ve la línea de
consumo en el momento.

---

### 5:00 — Administrar la plataforma (15%)

Es el indicador que más pesa junto con las métricas. **Explica, no solo
muestres.**

**Muestra**: Kafka UI → un tópico → **Settings**, donde se ven `retention.ms`,
`cleanup.policy`, `min.insync.replicas`.

**Di**:

> La administración de Kafka son cuatro conceptos, y cada uno resuelve una
> necesidad distinta del sistema.
>
> **Particiones**: rendimiento. Definen cuánto paralelismo admite el tópico.
> Solo se pueden aumentar, nunca reducir, porque los mensajes ya repartidos no
> se podrían reubicar sin romper el orden por clave.
>
> **Réplicas**: disponibilidad. Cuántas copias hay y cuántas tienen que estar
> al día para seguir aceptando escrituras.
>
> **Retención**: cuánto se guarda. `orders.events` guarda cinco días porque es
> un historial de hechos. `audit.timeline` guarda veintiuno porque la evidencia
> de trazabilidad tiene que durar más.
>
> **Política de limpieza**: `delete` borra por antigüedad. `compact` borra por
> clave, conservando el último mensaje de cada una. `audit.timeline` usa las
> dos: conserva el último estado de cada pedido, y además borra lo que pase de
> veintiún días.

**Añade** que además hay un microservicio administrador:

> Aparte de Kafka UI, tenemos un microservicio administrador que expone todo
> esto como API: crear tópicos, cambiar configuración en caliente, ampliar
> particiones e inspeccionar grupos de consumidores. Sirve para automatizar lo
> que en la consola se hace a mano.

---

### 6:00 — Métricas y supervisión (15%)

**Muestra**: Kafka UI → **Consumers**. Se ven los tres grupos con su lag.

**Di**:

> Estos son los tres grupos de consumidores, y la columna que importa es el
> retraso.
>
> El retraso es la diferencia entre el último offset escrito en la partición y
> el último que el grupo dio por procesado: cuántos mensajes hay publicados que
> ese grupo todavía no leyó.
>
> Un retraso cercano a cero significa que el consumidor va al día. Un retraso
> que crece de forma sostenida avisa de que no da abasto, y avisa **antes** de
> que se note en la aplicación. Por eso es la métrica que hay que vigilar.
>
> También se ve la distribución: qué particiones tiene asignadas cada
> consumidor. Si hubiera tres instancias del mismo servicio, aquí se vería una
> partición por instancia.

**Si preguntan por qué hay tres grupos y no uno**:

> Porque dentro de un grupo cada mensaje va a un solo consumidor, y entre
> grupos distintos cada grupo recibe todo. Auditoría y reportes leen el mismo
> tópico pero necesitan los dos todos los eventos, así que van en grupos
> separados. Si compartieran grupo, cada evento iría a uno solo de los dos y la
> mitad del trabajo quedaría sin hacer.

---

### 7:00 — Casos de uso de streaming (15%)

El otro indicador de 15%, y es **puro discurso**. Prepáratelo bien.

**Di**:

> Definimos tres casos de uso, y cada uno exige una configuración distinta.
>
> **El primero es trazabilidad.** Auditoría necesita todos los eventos, en
> orden por pedido, y conservados un tiempo largo. Eso se resuelve con un grupo
> propio, clave de partición por pedido, y retención de días.
>
> **El segundo son indicadores.** Reportes necesita lo mismo pero para
> agregar, no para registrar. Por eso va en otro grupo: los dos reciben todo,
> cada uno lleva su propio avance, y si uno se atrasa no frena al otro.
>
> **El tercero es reconstruir estado.** Para saber quién tocó cada pedido por
> última vez no hace falta el historial completo, solo el último registro de
> cada uno. Ese es el caso que resuelve un tópico compactado: `audit.timeline`
> conserva el último mensaje por pedido, así que releerlo entero reconstruye la
> proyección sin arrastrar todo el historial.
>
> Esa es la diferencia entre un tópico de eventos y uno compactado, y es la
> razón de que haya dos tópicos y no uno.

---

### 8:00 — Cierre (20 s)

> En resumen: tres Zookeeper y tres brokers por quórum, dos tópicos con tres
> particiones y tres réplicas, tres grupos de consumidores para tres casos de
> uso distintos, y Kafka complementando a RabbitMQ sin tocar nada de lo que ya
> estaba: los eventos por Kafka, los comandos por colas.

---

## Preguntas probables

**¿Por qué no usaron solo Kafka?**

> Porque un comando no es un evento. El comando tiene un único destinatario,
> necesita confirmación, reintento y descarte. Kafka no tiene cola muerta ni
> confirmación por mensaje: tiene offsets. Se podría simular, pero sería
> reimplementar lo que RabbitMQ ya hace.

**¿Qué pasa si se cae un broker?**

> Las particiones que lideraba pasan a otro de los que tienen réplica. Con
> `min.insync.replicas` en dos se sigue escribiendo porque quedan dos copias.
> Si cayeran dos, el tópico dejaría de aceptar escrituras a propósito.

**¿Y si se cae un Zookeeper?**

> El ensemble sigue: dos de tres son mayoría. Si cayeran dos, el clúster se
> quedaría sin quórum y los brokers no podrían elegir controlador.

**¿Por qué tres particiones si solo tienen una instancia de cada servicio?**

> Porque las particiones no se pueden reducir después. Dejarlas en tres permite
> escalar a tres instancias sin tocar el tópico. Y además reparte el liderazgo
> entre los tres brokers, que es trabajo distribuido aunque haya un solo
> consumidor.

**¿Cómo evitan procesar dos veces el mismo evento?**

> La proyección es idempotente: se hace *upsert* por id de pedido, no un
> insert. Reprocesar un evento deja el mismo resultado. Es la forma de
> convivir con la garantía *al menos una vez* de Kafka.

**¿Qué pasa si un mensaje viene mal formado?**

> Se reintenta tres veces con un segundo de espera y, si sigue fallando, va a
> un tópico de descarte con el nombre del tópico y el grupo. Ahí queda con los
> metadatos del error para revisarlo, sin bloquear la partición.

**¿Por qué desactivaron la creación automática de tópicos?**

> Porque un tópico creado por accidente nace con una partición y una réplica.
> Es mejor que un productor falle avisando a que publique durante semanas en un
> tópico sin réplicas.

---

## Lista de comprobación, cinco minutos antes

- [ ] `docker compose -f infra/local/docker-compose.yml up -d` — diez contenedores
- [ ] Los seis microservicios respondiendo en 8080–8086
- [ ] Frontend en `http://localhost:4200`, sesión de Azure ya iniciada
- [ ] **Kafka UI** en `http://localhost:8090`, en la pestaña Brokers
- [ ] Consola de RabbitMQ en `http://localhost:15672`
- [ ] Un pedido creado de antes, para que los reportes no estén vacíos
- [ ] Cronómetro: el límite son 10 minutos y se penaliza pasarse
