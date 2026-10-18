# Como hacerlo a mano

Todo lo que muestra el video se puede reproducir en vivo. Esta guia es para
cuando el docente pida demostrarlo en el momento, y para estudiar.

Los datos de esta sesion:

| | |
|---|---|
| Frontend | `https://3.83.237.65:8085` |
| API Gateway | `https://0b7fhgzl22.execute-api.us-east-1.amazonaws.com` |
| ec2-apps | `3.83.237.65` |
| ec2-broker | `75.101.249.200` (privada `172.31.19.198`) |

> Si la sesion del lab caduco, las IPs cambian. Para recuperarlas:
> `cat infra/aws/entorno-actual.env` despues de correr `provisionar.sh`.

---

## 1. Entrar a la consola de RabbitMQ

La consola **no esta expuesta a internet**: `seg-broker` solo acepta trafico
desde `seg-apps`. Eso es a proposito, y es un buen punto a favor si lo
preguntan. Se entra por un tunel SSH.

En una terminal que vas a dejar abierta:

```bash
ssh -i ~/Downloads/labsuser.pem \
    -L 15672:localhost:15672 \
    -L 15673:localhost:15673 \
    ec2-user@75.101.249.200
```

Esa terminal se queda ocupada: es el tunel. No la cierres.

En el navegador:

- `http://localhost:15672` → **nodo 1**
- `http://localhost:15673` → **nodo 2**

Usuario `guest`, contraseña `guest`.

> **Que decir si preguntan por guest/guest:** el broker no es alcanzable desde
> internet, solo desde las instancias de la aplicacion. En un entorno real se
> crearia un usuario propio y se borraria guest.

---

## 2. Ver los dos nodos del cluster

En `http://localhost:15672`, pestaña **Overview**, bajar hasta **Nodes**.

Aparecen `rabbit@rabbit1` y `rabbit@rabbit2`, con su memoria, disco y
descriptores de archivo.

**La prueba de que es un cluster y no dos brokers sueltos:** abri
`http://localhost:15673` (el nodo 2) y fijate que muestra **las mismas colas**.
Comparten metadata porque estan en el mismo cluster.

Desde la terminal tambien:

```bash
ssh -i ~/Downloads/labsuser.pem ec2-user@75.101.249.200 \
  'sudo docker exec mq-rabbit1-1 rabbitmq-diagnostics -q cluster_status'
```

En **Running Nodes** salen los dos.

---

## 3. Ver las seis colas

Pestaña **Queues and Streams**.

| Cola | Para que |
|---|---|
| `q.cmd.email` | Notificar al cliente |
| `q.cmd.kitchen` | Ticket de cocina |
| `q.cmd.invoice` | Documento de cobro |
| `q.cmd.email.dlq` | Respaldo de email |
| `q.cmd.kitchen.dlq` | Respaldo de cocina |
| `q.cmd.invoice.dlq` | Respaldo de facturacion |

En la columna **Consumers**, las tres de comando tienen **1**: es ms-notify
conectado. Las DLQ tienen **0**, porque nadie consume de ahi: se revisan a
mano.

---

## 4. Ver las politicas de retencion

Click en **`q.cmd.kitchen`**, seccion **Details → Features**.

```
x-dead-letter-exchange:     cmd.dead.dlx
x-dead-letter-routing-key:  kitchen.ticket
x-message-ttl:              30000
durable:                    true
```

Que decir de cada uno:

- **`x-dead-letter-exchange`**: a donde van los mensajes que no se pudieron
  entregar.
- **`x-message-ttl`**: 30 segundos. Es lo que hace que un comando sin consumir
  termine en la cola muerta en vez de quedarse encolado para siempre. Esta
  corto a proposito para poder demostrarlo; en produccion serian minutos.
- **`durable`**: la cola sobrevive al reinicio del broker.

Ahora click en **`q.cmd.kitchen.dlq`**: tiene su propia politica,
`x-message-ttl` de 14 dias y `x-max-length` de 1000. Es la politica de
limpieza: la evidencia se guarda dos semanas y el disco no se llena.

---

## 5. Ver los dos tipos de exchange

Pestaña **Exchanges**. Entre los del sistema (`amq.*`) estan los nuestros:

| Exchange | Tipo |
|---|---|
| `cmd.direct` | direct |
| `cmd.topic` | topic |
| `cmd.dead.dlx` | direct |

Click en **`cmd.topic`** → seccion **Bindings**. Se ven los patrones
`email.*`, `kitchen.#` e `invoice.*`.

Click en **`cmd.direct`** → claves exactas: `email.send`, `kitchen.ticket`,
`invoice.gen`.

> **La diferencia en una frase:** direct enruta por clave exacta y lo usa el
> ticket de cocina, que tiene un unico destinatario. Topic enruta por patron y
> lo usan el correo y el documento, que admiten variantes: el dia que exista
> `email.send.urgente`, el patron `email.*` lo recibe sin tocar un binding.

---

## 6. Crear un pedido y verlo llegar a las colas

Esta es la demostracion de que todo esta conectado de verdad. Conviene tener
**dos ventanas lado a lado**: la consola y la aplicacion.

1. En la consola, andate a **Overview** y mira el grafico **Message rates**.
   Esta en cero.
2. En la aplicacion, `https://3.83.237.65:8085`, entra con tu cuenta.
3. **Pedidos → + Nuevo pedido**, elegi un par de productos, **Agregar** a cada
   uno, y **Confirmar pedido**.
4. Volve a la consola. En unos segundos el grafico muestra el pico. El grafico
   conserva una ventana movil de un minuto, asi que el pico sigue ahi un rato.
5. En **Queues and Streams**, las columnas de **Message rates** registran el
   trafico de las tres colas.

**Que decir:** una sola accion del usuario publico un evento a Kafka y tres
comandos a RabbitMQ. Los tres consumidores los atendieron casi al instante, por
eso el contador de mensajes vuelve a cero: no es que no hayan llegado, es que
ya se procesaron.

**Si el docente quiere verlos acumularse**, ver el punto 7: con el consumidor
caido los mensajes se quedan a la vista.

---

## 7. Ver los logs de los consumidores

```bash
ssh -i ~/Downloads/labsuser.pem ec2-user@3.83.237.65 \
  'sudo docker logs apps-ms-notify-1 --tail 20'
```

Despues de crear un pedido se ven las tres lineas:

```
EmailNotificationListener : Enviando email al cliente ...   (correlationId=...)
KitchenTicketListener     : Ticket de cocina para el pedido ... (correlationId=...)
InvoiceGenerationListener : Documento de cobro del pedido ...   (correlationId=...)
ConfirmacionDeMensajes    : [email] Evento ... confirmado (ACK)
```

**El detalle que vale oro:** los tres comparten el **mismo `correlationId`**.
Para resaltarlo:

```bash
ssh -i ~/Downloads/labsuser.pem ec2-user@3.83.237.65 \
  'sudo docker logs apps-ms-notify-1 2>&1 | grep -oE "correlationId=[a-f0-9-]+" | tail -3 | sort -u'
```

Si devuelve **un solo valor**, los tres comandos vinieron de la misma accion.

---

## 8. Demostrar la caida de un consumidor

El escenario completo, en vivo. Toma unos 90 segundos.

**Paso 1 — Bajar el consumidor**

```bash
ssh -i ~/Downloads/labsuser.pem ec2-user@3.83.237.65 \
  'cd ~/infra/apps && sudo docker compose stop ms-notify'
```

En la consola, `q.cmd.kitchen` pasa a **Consumers: 0**.

**Paso 2 — Publicar un comando valido**

En la consola: **Exchanges → cmd.direct → Publish message**.

- **Routing key**: `kitchen.ticket`
- **Payload**:

```json
{"type":"KitchenTicketRequested","eventId":"demo-manual","correlationId":"demo",
 "payload":{"orderId":99,"customerId":"cliente-99","cantidadItems":3}}
```

- **Properties**: agregar `content_type` = `application/json`

Click en **Publish message**.

> Subraya que **este mensaje no tiene nada malo**. El problema no es el
> mensaje: es que el servicio que debia atenderlo no esta.

**Paso 3 — Verlo esperar**

**Queues → q.cmd.kitchen**: 1 mensaje, 0 consumidores. Ahi se queda.

**Paso 4 — Esperar 30 segundos**

Vence el TTL. `q.cmd.kitchen` vuelve a 0 y **`q.cmd.kitchen.dlq` pasa a 1**.
Nadie intervino: lo hizo el broker.

**Paso 5 — Leer el motivo**

**Queues → q.cmd.kitchen.dlq → Get messages → Get Message(s)**.

En los **headers** del mensaje aparece `x-death`, y adentro:

```
reason: expired
queue:  q.cmd.kitchen
```

> **La frase de cierre:** `expired` significa que nadie lo consumio a tiempo.
> Si en cambio un consumidor lo hubiera rechazado por venir mal formado, diria
> `rejected`. Son dos caminos distintos hacia la misma cola muerta, y el
> sistema distingue entre "el mensaje estaba malo" y "el servicio no estaba".

**Paso 6 — Levantar el consumidor**

```bash
ssh -i ~/Downloads/labsuser.pem ec2-user@3.83.237.65 \
  'cd ~/infra/apps && sudo docker compose up -d ms-notify'
```

Tarda unos 30 segundos. Despues `q.cmd.kitchen` vuelve a tener su consumidor.

---

## 9. Demostrar que el Gateway valida el token

Desde cualquier terminal, sin necesidad de SSH:

```bash
# Sin token: 401
curl -s -o /dev/null -w "%{http_code}\n" \
  https://0b7fhgzl22.execute-api.us-east-1.amazonaws.com/api/orders

# Con un token inventado: tambien 401
curl -s -o /dev/null -w "%{http_code}\n" \
  -H "Authorization: Bearer token-falso" \
  https://0b7fhgzl22.execute-api.us-east-1.amazonaws.com/api/orders
```

Y el preflight de CORS, que si pasa sin token porque tiene que pasar:

```bash
curl -s -i -X OPTIONS \
  -H "Origin: https://3.83.237.65:8085" \
  -H "Access-Control-Request-Method: GET" \
  https://0b7fhgzl22.execute-api.us-east-1.amazonaws.com/api/orders | grep -i "^access-control"
```

Devuelve el origen permitido y los cinco metodos. **Un solo origen**: el del
frontend desplegado, no localhost. Y **sin OPTIONS** en la lista de metodos,
porque esa cabecera declara los metodos de la peticion real.

---

## 10. Verificar todo de una vez

```bash
cd /Users/gourarodriguez/Desktop/CloudNative
./infra/aws/verificar.sh ~/Downloads/labsuser.pem
```

Recorre los mismos puntos: health de los seis servicios, 401 sin token, el
preflight, los dos nodos del cluster, las seis colas y sus politicas de
retencion.

---

## Si algo no responde

**Los servicios dan 000 y el SSH no entra.** La instancia esta saturada.
Reinicia desde la consola de AWS (**EC2 → Instance state → Reboot**) o:

```bash
aws ec2 reboot-instances --instance-ids <id-de-ec2-apps>
```

Los contenedores vuelven solos a los 90 segundos, y la IP publica **no**
cambia con un reinicio.

**No construyas las seis imagenes a la vez.** Seis compilaciones de Maven en
paralelo no entran en los 4 GB de una t3.medium y la dejan sin responder. De a
una:

```bash
for s in ms-orders ms-catalog ms-notify ms-report ms-audit ms-mqadmin; do
  sudo docker compose build "$s" && sudo docker compose up -d "$s" && sleep 20
done
```

**ms-orders tarda unos 90 segundos en arrancar.** Si da 000 recien levantado,
es eso. Los que no usan base de datos (notify y mqadmin) arrancan en 15.
