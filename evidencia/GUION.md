# Guion de la presentacion

Siete videos en `entregables/`, 237 segundos en total. Con la narracion de
abajo la presentacion queda entre 7 y 9 minutos, dentro del rango que pide la
pauta.

El orden va de lo general a lo particular: primero se muestra que el sistema
esta desplegado y funcionando, despues como se comunica por dentro, y al final
que soporta un fallo.

---

## Antes de empezar

```bash
cd /Users/gourarodriguez/Desktop/CloudNative
./infra/aws/verificar.sh ~/Downloads/labsuser.pem    # todo en verde
```

Y dejar abierto el tunel a la consola del broker, por si el docente pide ver
algo en vivo:

```bash
ssh -i ~/Downloads/labsuser.pem -L 15672:localhost:15672 -L 15673:localhost:15673 \
    ec2-user@<ip-publica-de-ec2-broker>
```

---

## 1. Arquitectura (sin video, 45 segundos)

Abrir el diagrama de `docs/mensajeria.md`.

> "Pedidos360 son seis microservicios en Spring Boot y un frontend en Angular,
> desplegados en AWS. El frontend nunca habla directo con los microservicios:
> todas las llamadas pasan por el API Gateway, que valida el JWT antes de
> enrutar.
>
> Por dentro hay dos sistemas de mensajeria con roles distintos. Kafka lleva
> **eventos**: hechos que ya ocurrieron y que le interesan a varios servicios a
> la vez. RabbitMQ lleva **comandos**: ordenes de trabajo con un unico dueño.
> Esa distincion es la que explica por que estan los dos."

---

## 2. La nube: API Manager e instancias (sin video, 60 segundos)

**Esto se muestra en vivo en la consola de AWS**, porque necesita tu sesion.

Recorrido: **API Gateway → pedidos360-api → Routes**, despues **Authorization**,
y finalmente **EC2 → Instances**.

> "Esta es la instancia del API Manager en AWS. Tiene doce rutas sobre seis
> integraciones, una por microservicio.
>
> Fijense que cada servicio tiene dos rutas. La `ANY` lleva el authorizer JWT
> adjunto y es la que exige token. La `OPTIONS` va sin authorizer, y no es un
> descuido: las rutas `ANY` incluyen el metodo OPTIONS, y con el authorizer
> adjunto el preflight de CORS —que el navegador manda sin header
> Authorization— respondia 401 antes de poder devolver los headers. La ruta
> OPTIONS explicita gana por especificidad de metodo y resuelve el preflight.
>
> El authorizer valida contra el tenant de Azure AD, y acepta dos audiencias:
> `api://<client-id>` y el GUID pelado, porque los tokens v2 traen el GUID.
>
> Y aca estan las dos instancias EC2: una con los seis microservicios y el
> frontend, otra con el cluster de RabbitMQ y Kafka."

---

## 3. Video `6-login-catalogo-y-pedido` (49 s)

**0:00 - 0:12 — Login**

> "El frontend inicia sesion con OAuth 2.0 y OpenID Connect contra Azure AD.
> Vean el redirect: sale de nuestra aplicacion, pasa por
> login.microsoftonline.com y vuelve con un codigo que MSAL intercambia por un
> access token. Es Authorization Code con PKCE, que es el flujo correcto para
> una aplicacion de pagina unica.
>
> Arriba a la derecha aparece el usuario y sus roles: Admin, Operador, Cliente
> y Auditor. Esos roles vienen del claim `roles` del token, no de la base de
> datos."

**0:12 - 0:25 — Catalogo**

> "El catalogo lo sirve ms-catalog a traves del API Gateway. Son productos
> reales con sus precios y su stock."

**0:25 - 0:49 — Crear un pedido**

> "Creo un pedido con dos productos. Un detalle importante: el precio no lo
> manda el navegador. El frontend solo envia el identificador del producto y la
> cantidad; ms-orders consulta el precio a ms-catalog y calcula el total. Si el
> cliente manipulara el precio en el navegador, no serviria de nada.
>
> El pedido queda en estado CREADO. Y esa sola accion acaba de publicar tres
> comandos a RabbitMQ y un evento a Kafka."

---

## 4. Video `7-auditoria-y-reportes` (36 s)

> "Aca esta la otra mitad. ms-audit y ms-report no consultan a ms-orders: se
> alimentan solos de los eventos que ms-orders publica en Kafka.
>
> En auditoria aparece el evento OrderCreated del pedido que acabo de crear. En
> reportes, los KPIs ya lo incluyen. Nadie los llamo: reaccionaron al evento.
>
> Esto es lo que da el desacoplamiento. Si manana quisiera un cuarto servicio
> que haga algo con los pedidos, lo conecto al mismo topico y ms-orders no
> cambia ni una linea."

---

## 5. Video `1-cluster-dos-nodos` (15 s)

> "Este es el cluster de RabbitMQ. Dos nodos, rabbit1 y rabbit2, integrados en
> un mismo cluster y corriendo con docker-compose en la instancia del broker.
>
> Los microservicios se conectan a los dos: si el primero no responde, Spring
> hace failover al segundo."

**Si preguntan que pasa si se cae un nodo**, la respuesta honesta —y la
medimos— esta al final de este guion.

---

## 6. Video `2-seis-colas-y-sus-dlq` (14 s)

> "Seis colas. Tres de comando —email, cocina y facturacion— y la cola muerta
> de cada una.
>
> Cada cola de comando tiene su DLQ como respaldo: ahi van los mensajes que no
> se pudieron entregar."

---

## 7. Video `3-exchanges-direct-y-topic` (12 s)

> "Dos tipos de exchange, y cada uno resuelve un caso distinto.
>
> El **direct** enruta por clave exacta. Lo usa el ticket de cocina, que tiene
> un unico destinatario.
>
> El **topic** enruta por patron con comodines. Lo usan el correo y el
> documento de cobro, que si admiten variantes: el dia que exista
> `email.send.urgente`, el patron `email.*` lo recibe sin tocar un solo
> binding.
>
> El tercero, `cmd.dead.dlx`, es el exchange de dead-lettering: es a donde el
> broker enruta lo que no se pudo entregar."

---

## 8. Video `4-politicas-de-retencion` (14 s)

> "Estos son los argumentos con que se declara cada cola, y son las politicas
> de retencion y limpieza.
>
> `x-dead-letter-exchange` define a donde van los mensajes fallidos.
>
> `x-message-ttl` define cuanto vive un comando sin consumir antes de que el
> broker lo venza. Lo pusimos en 30 segundos para poder demostrarlo en vivo; en
> produccion serian minutos u horas.
>
> Las colas muertas tienen su propia politica: 14 dias de retencion y un tope
> de mil mensajes, para que una tormenta de errores no llene el disco del nodo.
>
> Nada de esto esta escrito en el codigo Java: sale del application.yml."

---

## 9. Video `5-caida-de-consumidor` (94 s) — el escenario de fallo

Es el mas largo y el mas importante. Conviene narrarlo por tramos.

**0:00 - 0:15 — Punto de partida**

> "La cola de cocina con su consumidor enganchado, trabajando normal."

**0:15 - 0:30 — Se cae el consumidor**

> "Ahora bajo el microservicio consumidor. Vean que los consumidores pasan a
> cero: la cola sigue existiendo, pero no hay nadie que la atienda."

**0:30 - 0:45 — Llega un comando valido**

> "Publico un comando perfectamente valido por el exchange direct. Ojo: este
> mensaje no tiene nada malo. El problema no es el mensaje, es que el servicio
> que tenia que atenderlo no esta."

**0:45 - 1:05 — El mensaje espera**

> "El mensaje queda encolado. Un mensaje, cero consumidores. Si no hicieramos
> nada, se quedaria ahi para siempre."

**1:05 - 1:25 — Vence y cae a la cola muerta**

> "Pero vence el TTL de 30 segundos y el broker lo enruta solo a la cola
> muerta. La cola de comando queda en cero y la DLQ pasa a uno. Nadie
> intervino: lo hizo el propio RabbitMQ con la politica que configuramos."

**1:25 - 1:34 — La prueba**

> "Y aca esta la evidencia. Abro el mensaje en la cola muerta y el broker dejo
> escrito el motivo en el header `x-death`: **`reason: expired`**.
>
> Eso lo distingue del otro camino. Si un consumidor rechaza un mensaje por
> venir mal formado, el motivo dice `rejected`. Aca dice `expired`, que
> significa que nadie lo consumio a tiempo.
>
> Son dos caminos distintos hacia la misma cola muerta, y el sistema distingue
> entre 'el mensaje estaba malo' y 'el servicio no estaba disponible'."

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

> Los consumidores corren en modo MANUAL: ningun mensaje se da por procesado
> hasta que el codigo lo confirma. Hay tres salidas, todas en una sola clase
> para que la politica sea identica en las tres colas. Si el trabajo termina
> bien, ACK. Si falla con un error recuperable y quedan intentos, NACK con
> requeue. Si se agotan los intentos, o el error es del mensaje mismo, o es una
> excepcion inesperada, NACK sin requeue y va a la DLQ.

**¿Por que una excepcion desconocida va directo a la cola muerta?**

> Es la opcion conservadora. Si no sabemos por que fallo, reintentar a ciegas
> puede dejar el mensaje girando y bloqueando a los que vienen detras. Es
> preferible la DLQ, que es visible y revisable, antes que un bucle silencioso.

**¿Donde esta el microservicio administrador?**

> `ms-pedidos360-mqadmin`, en el puerto 8086. Expone ocho endpoints REST para
> crear y eliminar colas, exchanges y bindings en caliente. Toda la interaccion
> con RabbitMQ esta encapsulada en `RabbitAdminService`: los controladores no
> importan nada de la libreria de AMQP, se puede verificar con un grep. Y es
> solo para rol Admin, porque esos endpoints pueden borrar las colas de las que
> depende el resto del sistema.

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
