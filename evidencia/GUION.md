# Guion de la presentacion

Un solo video: **`entregables/pedidos360-demostracion.mp4`**, 5 min 59 s.

Cada tarjeta de transicion anuncia lo que viene detras, asi que funciona como
hilo conductor: se puede dejar correr de principio a fin mientras se narra.
Con las pausas y lo que se muestra en vivo, la presentacion queda entre 9 y 11
minutos.

---

## Antes de reproducir

```bash
cd /Users/gourarodriguez/Desktop/CloudNative
./infra/aws/verificar.sh ~/Downloads/labsuser.pem
```

Dejar abierto el tunel al broker, por si piden ver algo en vivo:

```bash
ssh -i ~/Downloads/labsuser.pem -L 15672:localhost:15672 -L 15673:localhost:15673 \
    ec2-user@<ip-publica-de-ec2-broker>
```

---

## Parte en vivo, ANTES del video (90 segundos)

Hay dos cosas que el video no puede mostrar porque necesitan tu sesion
autenticada. Conviene hacerlas primero, como contexto.

### La consola de AWS

**API Gateway → pedidos360-api → Routes**

> "Esta es la instancia del API Manager. Doce rutas sobre seis integraciones,
> una por microservicio.
>
> Fijense que cada servicio tiene dos rutas. La `ANY` lleva el authorizer JWT y
> exige token. La `OPTIONS` va sin authorizer, y no es un descuido: las rutas
> `ANY` incluyen el metodo OPTIONS, y con el authorizer adjunto el preflight de
> CORS —que el navegador manda sin header Authorization— respondia 401 antes de
> devolver los headers. La ruta OPTIONS explicita gana por especificidad de
> metodo y lo resuelve."

**Authorization → pedidos360-jwt**

> "El authorizer valida contra nuestro tenant de Azure AD. Acepta dos
> audiencias, `api://<client-id>` y el GUID pelado, porque los tokens version 2
> traen el GUID."

**EC2 → Instances**

> "Dos instancias: una con los seis microservicios y el frontend, otra con el
> cluster de RabbitMQ y Kafka, los dos levantados con docker-compose."

### El portal de Azure

**App registrations → Pedidos360 → App roles**, y despues **Users**

> "Este es el tenant en el proveedor de identidad. Aca estan los cuatro roles
> de la aplicacion —Admin, Operador, Cliente y Auditor— y los usuarios
> registrados."

---

## El video, tramo por tramo

### 0:00 — Portada

Dejar correr. Arrancar cuando aparezca la primera tarjeta.

### 0:09 — Login, catalogo y creacion de un pedido

**0:09 · El login**

> "El frontend inicia sesion con OAuth 2.0 y OpenID Connect. Vean el redirect:
> sale de nuestra aplicacion, pasa por login.microsoftonline.com y vuelve con
> un codigo que se intercambia por un access token. Es Authorization Code con
> PKCE, el flujo correcto para una aplicacion de pagina unica.
>
> Arriba aparece el usuario con sus cuatro roles. Vienen del claim `roles` del
> token, no de la base de datos."

**0:25 · El catalogo**

> "El catalogo lo sirve ms-catalog a traves del API Gateway. Productos reales,
> con su precio y su stock."

**0:35 · Crear el pedido**

> "Creo un pedido con dos productos. Un detalle que importa: **el precio no lo
> manda el navegador**. El frontend envia solo el identificador del producto y
> la cantidad; ms-orders consulta el precio a ms-catalog y calcula el total. Si
> alguien manipulara el precio en el navegador, no serviria de nada.
>
> El pedido queda en CREADO. Y esa sola accion acaba de publicar un evento a
> Kafka y tres comandos a RabbitMQ."

### 1:02 — Auditoria y reportes

> "Esta es la otra mitad del desacoplamiento. ms-audit y ms-report no consultan
> a ms-orders: se alimentan solos de los eventos de Kafka.
>
> En auditoria aparece el OrderCreated del pedido que acabo de crear. En
> reportes, los KPIs ya lo incluyen. Nadie los llamo.
>
> Si manana quisiera un cuarto servicio que reaccione a los pedidos, lo conecto
> al mismo topico y ms-orders no cambia una linea."

### 1:43 — La conexion, en vivo

El tramo que demuestra que no hay nada preparado: **dos navegadores corriendo
al mismo tiempo** contra el mismo despliegue.

**1:43 · Arranque**

> "A la izquierda la aplicacion, a la derecha la consola de RabbitMQ. Dos
> navegadores distintos, a la vez, contra el mismo despliegue. Las seis colas
> estan en cero."

**2:18 · La pausa, y por que**

> "Antes de confirmar el pedido voy a **pausar el consumidor**, y quiero
> explicar por que, porque es importante.
>
> Con los tres consumidores andando, un mensaje se publica y se procesa en
> milisegundos. La consola muestrea cada cinco segundos, asi que cuando
> refresca ya no queda nada: el contador se queda en cero y parece que no paso
> nada. Pausando el consumidor el mensaje se queda a la vista.
>
> Es el mismo trafico. Solo lo estoy haciendo visible."

**2:40 · El momento**

> "Confirmo el pedido. Y miren la derecha: **las tres colas de comando pasan a
> tener un mensaje**. Email, cocina y facturacion. Una sola accion del usuario,
> tres comandos.
>
> Fijense en que columna estan: **Unacked**, no Ready. Eso significa que el
> consumidor ya los recibio pero todavia no los confirmo. Es el modo de
> confirmacion MANUAL: el mensaje no se da por procesado hasta que el codigo lo
> confirma explicitamente."

**2:52 · Se reanuda**

> "Reanudo el consumidor. Y las tres colas vuelven a cero: los proceso y los
> confirmo.
>
> Eso es el ciclo completo, visto desde los dos lados a la vez."

### 3:06 — El cluster de RabbitMQ

> "Dos nodos, rabbit1 y rabbit2, integrados en un mismo cluster y levantados
> con docker-compose en la instancia del broker.
>
> Los microservicios se conectan a los dos: si el primero no responde, Spring
> hace failover al segundo."

### 3:26 — Las seis colas

> "Seis colas. Tres de comando —email, cocina y facturacion— y la cola muerta
> de cada una, que es su respaldo cuando un mensaje no se puede entregar."

### 3:44 — Los dos tipos de exchange

> "Dos tipos, y cada uno resuelve un caso distinto.
>
> El **direct** enruta por clave exacta. Lo usa el ticket de cocina, que tiene
> un unico destinatario y no admite variantes.
>
> El **topic** enruta por patron con comodines. Lo usan el correo y el
> documento de cobro: el dia que exista `email.send.urgente`, el patron
> `email.*` lo recibe sin tocar un solo binding.
>
> El tercero, `cmd.dead.dlx`, es el de dead-lettering."

### 4:00 — Retencion y limpieza

> "Estos son los argumentos con que se declara cada cola.
>
> `x-dead-letter-exchange` define a donde van los mensajes fallidos.
>
> `x-message-ttl` define cuanto vive un comando sin consumir antes de que el
> broker lo venza. Lo pusimos en 30 segundos para poder demostrarlo; en
> produccion serian minutos u horas.
>
> Las colas muertas tienen su propia politica: 14 dias de retencion y tope de
> mil mensajes, para que una tormenta de errores no llene el disco del nodo.
>
> Nada de esto esta escrito en el codigo: sale del application.yml."

### 4:19 — Que pasa si se cae un consumidor

Es el tramo mas largo, 94 segundos. Conviene narrarlo por partes.

**4:19 · Punto de partida**

> "La cola de cocina con su consumidor enganchado, trabajando normal."

**4:33 · Se cae el consumidor**

> "Bajo el microservicio consumidor. Los consumidores pasan a cero: la cola
> sigue existiendo, pero no hay nadie que la atienda."

**4:48 · Llega un comando valido**

> "Publico un comando perfectamente valido por el exchange direct. Ojo: **este
> mensaje no tiene nada malo**. El problema no es el mensaje, es que el
> servicio que tenia que atenderlo no esta."

**5:03 · El mensaje espera**

> "Queda encolado. Un mensaje, cero consumidores. Si no hicieramos nada, se
> quedaria ahi para siempre."

**5:23 · Vence y cae a la cola muerta**

> "Pero vence el TTL de 30 segundos y el broker lo enruta solo a la cola
> muerta. La cola de comando queda en cero, la DLQ pasa a uno. Nadie
> intervino: lo hizo RabbitMQ con la politica que configuramos."

**5:41 · La prueba**

> "Y aca esta la evidencia. Abro el mensaje en la cola muerta y el broker dejo
> escrito el motivo en el header `x-death`: **`reason: expired`**.
>
> Eso lo distingue del otro camino. Si un consumidor rechaza un mensaje por
> venir mal formado, el motivo dice `rejected`. Aca dice `expired`: nadie lo
> consumio a tiempo.
>
> Son dos caminos distintos hacia la misma cola muerta, y el sistema distingue
> entre 'el mensaje estaba malo' y 'el servicio no estaba disponible'."

### 5:53 — Cierre

> "Eso es todo el flujo: identidad federada, el API Gateway validando el token
> en el borde, un cluster de dos nodos, seis colas con sus colas muertas, los
> dos tipos de exchange con trafico real, y un fallo soportado sin perder el
> mensaje."

---

## Preguntas probables

**¿Por que Kafka y RabbitMQ, y no uno solo?**

> Resuelven problemas con requisitos opuestos. El evento necesita muchos
> lectores independientes y un historial releible: eso es el log particionado
> de Kafka. El comando necesita un solo ejecutor, con confirmacion, reintento y
> descarte: eso es la cola con DLQ de RabbitMQ. Si todo fuera Rabbit, cada
> nuevo interesado en los eventos obligaria a crear otra cola y a que ms-orders
> la conozca. Si todo fuera Kafka, habria que construir a mano la confirmacion
> y el descarte por mensaje.

**¿Que pasa si se cae un nodo del cluster?**

> Lo medimos. El failover de **conexion** funciona: los servicios reconectan
> solos al otro nodo. Pero la **cola** queda indisponible, porque son colas
> clasicas sin espejado y viven en el nodo donde se declararon. Con dos nodos
> las quorum queues no lo arreglan: el quorum necesita mayoria, y la mayoria de
> dos es dos. Haria falta un tercer nodo. El cluster da metadata compartida,
> vista unica de administracion y failover de conexion; no da alta
> disponibilidad de colas.

**¿Como manejan la confirmacion de mensajes?**

> En modo MANUAL: ningun mensaje se da por procesado hasta que el codigo lo
> confirma. Hay tres salidas, todas en una sola clase para que la politica sea
> identica en las tres colas. Trabajo terminado, ACK. Error recuperable con
> intentos restantes, NACK con requeue. Intentos agotados, error del mensaje
> mismo, o excepcion inesperada, NACK sin requeue y a la DLQ.

**¿Por que una excepcion desconocida va directo a la cola muerta?**

> Es la opcion conservadora. Si no sabemos por que fallo, reintentar a ciegas
> puede dejar el mensaje girando y bloqueando a los que vienen detras. Es
> preferible la DLQ, que es visible y revisable, antes que un bucle silencioso.

**¿Donde esta el microservicio administrador?**

> `ms-pedidos360-mqadmin`, puerto 8086. Ocho endpoints REST para crear y
> eliminar colas, exchanges y bindings en caliente. Toda la interaccion con
> RabbitMQ esta encapsulada en `RabbitAdminService`: los controladores no
> importan nada de la libreria de AMQP, se verifica con un grep. Solo rol
> Admin, porque esos endpoints pueden borrar las colas de las que depende el
> resto del sistema.

**¿Como monitorean el cluster?**

> Con la consola de RabbitMQ: Overview muestra los dos nodos con su memoria y
> disco, y Queues la profundidad de cada cola, su tasa de entrada y salida y
> los consumidores enganchados. Es donde se ve en vivo que una cola se llena
> cuando su consumidor se cae. Ademas los logs de los contenedores se pueden
> enviar a CloudWatch Logs.

**¿Por que no usaron Amazon MQ?**

> Porque entonces no habria nada que demostrar. Con un broker gestionado no hay
> docker-compose que mostrar, no hay dos nodos que nosotros configuramos en
> cluster, y el microservicio administrador pierde sentido: el cluster lo
> administraria AWS.

---

## Si el docente pide verlo en vivo

Todo lo del video se puede reproducir a mano. El recorrido completo —como
entrar a la consola del broker por tunel SSH, donde mirar cada cosa, y como
demostrar la caida de un consumidor paso a paso— esta en
**[PASO-A-PASO.md](PASO-A-PASO.md)**.

## Si hay que rehacer el video

```bash
node montaje/tarjetas.mjs   # regenera las tarjetas de transicion
./montaje/montar.sh         # vuelve a unir todo
```

Para regrabar los clips, ver [README.md](README.md).
