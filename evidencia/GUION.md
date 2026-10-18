# Guion de la demostracion

El video **ya viene narrado**: `entregables/pedidos360-demostracion.mp4`,
10 min 37 s. No hace falta leer nada encima.

Este documento es el texto de esa narracion, para estudiar y para tener a mano
las respuestas si el docente interrumpe con preguntas. El texto fuente vive en
`montaje/narracion.py`.

## Indice

| Momento | Tramo | Que se ve |
|---|---|---|
| **0:00** | `portada` | Portada |
| **0:22** | `app-login` | La pantalla de inicio de sesion |
| **0:51** | `app-redirect` | La vuelta del proveedor de identidad |
| **1:07** | `app-roles` | El dashboard con los cuatro roles |
| **1:23** | `app-catalogo` | El catalogo con precios y stock |
| **1:39** | `app-formulario` | El formulario del pedido |
| **1:59** | `app-creado` | El pedido creado |
| **2:12** | `consumidores` | Auditoria y reportes |
| **2:47** | `paralelo` | La app y la consola del broker a la vez |
| **4:06** | `cfg-1-yml` | La topologia en el application.yml |
| **4:34** | `cfg-2-cola` | Como se construye una cola de comando |
| **5:05** | `cfg-3-dlq` | Como se construye una cola muerta |
| **5:33** | `cfg-4-bindings` | Los tres bindings por flujo |
| **5:59** | `cluster` | Los dos nodos del cluster |
| **6:16** | `colas` | Las seis colas |
| **6:38** | `exchanges` | Los exchanges direct y topic |
| **7:11** | `retencion` | Los argumentos de una cola real |
| **7:34** | `dlq-caminos` | Los dos caminos hacia la cola muerta |
| **8:25** | `fallo-partida` | La cola funcionando normal |
| **8:38** | `fallo-caida` | El consumidor caido |
| **8:50** | `fallo-publicar` | Se publica un comando valido |
| **9:07** | `fallo-espera` | El mensaje esperando |
| **9:37** | `fallo-vencido` | El mensaje vencido en la cola muerta |
| **9:52** | `fallo-motivo` | El header x-death |
| **10:13** | `cierre` | Resumen |

---

## 0:00 — Portada

> Buenas. Les voy a mostrar Pedidos trescientos sesenta, el sistema que desarrollamos para este ramo. Son seis microservicios en Spring Boot y un frontend en Angular, desplegados en Amazon Web Services. Por dentro usa dos sistemas de mensajeria con roles distintos: Kafka para eventos y RabbitMQ para comandos. Vamos a ver primero la aplicacion funcionando, y despues como se comunica por dentro.

## 0:22 — La pantalla de inicio de sesion

> Empezamos por la pantalla de inicio de sesion. La aplicacion no tiene usuarios propios ni guarda contrasenas: delega toda la identidad en Azure, que es nuestro proveedor. Por eso el unico boton dice iniciar sesion con Microsoft. Cuando lo presione, el navegador va a salir de nuestra aplicacion, va a ir al proveedor de identidad, y va a volver con un codigo que la aplicacion intercambia por un token de acceso. Es el flujo Authorization Code con PKCE, el que corresponde a una aplicacion de pagina unica, porque no puede guardar un secreto.

## 0:51 — La vuelta del proveedor de identidad

> Ahi fue, y volvio. El viaje de ida y vuelta al proveedor fue casi instantaneo porque la sesion ya estaba abierta en Azure. Si no lo estuviera, aca se veria la pantalla de credenciales del proveedor, que es justamente donde se escribe la contrasena: nunca en nuestra aplicacion.

## 1:07 — El dashboard con los cuatro roles

> Ya estamos dentro, y arriba aparece el usuario con sus cuatro roles: Admin, Operador, Cliente y Auditor. Esos roles no salen de nuestra base de datos: vienen dentro del token, en el claim roles, firmados por Azure. El backend los lee de ahi para decidir que puede hacer cada uno.

## 1:23 — El catalogo con precios y stock

> Este es el catalogo, que sirve el microservicio de catalogo a traves del API Gateway. Son productos reales con su precio y su stock: pizzas, empanadas, bebidas. Cada llamada que ven aca viaja con el token, y el Gateway lo valida antes de dejarla pasar al backend.

## 1:39 — El formulario del pedido

> Ahora armo un pedido con dos productos. Y aca hay un detalle que importa para la seguridad: el precio no lo manda el navegador. El frontend envia solamente el identificador del producto y la cantidad. El microservicio de pedidos le consulta el precio al catalogo y calcula el total del lado del servidor. Si alguien manipulara el precio desde el navegador, no serviria de nada.

## 1:59 — El pedido creado

> Confirmo, y el pedido queda creado, en estado CREADO, con sus items y su total calculado. Esa sola accion acaba de publicar un evento a Kafka y tres comandos a RabbitMQ. En lo que sigue vamos a ver exactamente a donde fueron a parar.

## 2:12 — Auditoria y reportes

> Aca esta la otra mitad del desacoplamiento. El microservicio de auditoria y el de reportes no le preguntan nada al de pedidos: se alimentan solos de los eventos que viajan por Kafka. En auditoria aparece el evento OrderCreated del pedido que acabo de crear. Y en reportes, los indicadores ya lo incluyen. Nadie los llamo: reaccionaron al evento. Eso es lo que da el desacoplamiento. Si manana quisiera un cuarto servicio que reaccione a los pedidos, lo conecto al mismo topico y el microservicio de pedidos no cambia ni una linea de codigo.

## 2:47 — La app y la consola del broker a la vez

> Ahora quiero demostrar que la conexion entre la aplicacion y el broker es real. A la izquierda la aplicacion, a la derecha la consola de RabbitMQ. Son dos navegadores distintos corriendo al mismo tiempo contra el mismo despliegue. Miren la derecha antes de que pase nada: las seis colas en cero. Esto hay que verlo, porque si no, lo que viene despues no probaria nada. Antes de confirmar el pedido voy a pausar el consumidor, y conviene que explique por que. Con los tres consumidores andando, un mensaje se publica y se procesa en milisegundos. La consola muestrea cada cinco segundos: cuando refresca ya no queda nada, y el contador se queda en cero. Pausando el consumidor, el mensaje se queda a la vista. Es el mismo trafico; solo lo estoy haciendo visible. Confirmo el pedido. Y miren la derecha: las tres colas de comando pasan a tener un mensaje. Correo, cocina y facturacion. Una sola accion del usuario, tres comandos. Fijense ademas en que columna estan: Unacked, no Ready. Eso significa que el consumidor ya los recibio pero todavia no los confirmo. Es el modo de confirmacion manual. Y si dudan de que los dos lados sean el mismo instante, estan los relojes: el pedido dice creado a una hora, y la consola refrescada unos segundos despues. Son dos relojes reales. Ahora reanudo el consumidor, y las tres colas vuelven a cero: los proceso y los confirmo. Ese es el ciclo completo, visto desde los dos lados a la vez.

## 4:06 — La topologia en el application.yml

> Veamos ahora como esta construida cada cola, porque no hay nada improvisado. Toda la topologia se declara en el application punto yaml. Aca estan los tres exchanges, las politicas de retencion, y los tres flujos: correo, cocina y facturacion. Cada flujo define el nombre de su cola, su clave de enrutamiento exacta, y el patron con comodines para el exchange de tipo topic. No hay ni un nombre de cola escrito en el codigo Java. Cambiar como se llama una cola es cambiar esta configuracion.

## 4:34 — Como se construye una cola de comando

> Y esta es la construccion de cada cola de comando, en una sola funcion que los tres flujos comparten. Se declara durable, para que sobreviva al reinicio del broker. Despues se le ponen dos argumentos de dead lettering: el exchange al que van los mensajes que no se pudieron entregar, y la clave con la que se enrutan. Y el tercero es el que mas importa, el que esta resaltado: equis message t t l. Define cuanto vive un comando sin que nadie lo consuma. Sin este argumento, si el consumidor estuviera caido el mensaje se quedaria encolado para siempre y la cola muerta no se usaria nunca.

## 5:05 — Como se construye una cola muerta

> La cola muerta de cada flujo se construye aparte, y tiene su propia politica. Guarda la evidencia catorce dias, y tiene un tope de mil mensajes, para que una tormenta de errores no llene el disco del nodo. Esas dos cosas juntas son la politica de retencion y limpieza. Y fijense en lo que no tiene: la cola muerta no tiene dead lettering propio. Eso es a proposito. Lo que vence aqui se descarta, y ese descarte es justamente la limpieza. Si la cola muerta reenviara a otro lado, los mensajes no se irian nunca.

## 5:33 — Los tres bindings por flujo

> Cada flujo se conecta con tres bindings. Este es el ejemplo del flujo de cocina. El primero lo une al exchange direct, con una clave exacta. El segundo al exchange topic, con un patron que admite comodines. Y el tercero, el resaltado, cuelga la cola muerta del dead letter exchange, con la misma clave. Ese tercero es el que cierra el circuito: es lo que hace que un mensaje rechazado o vencido termine en la cola muerta correcta, y no en cualquier otra.

## 5:59 — Los dos nodos del cluster

> Pasemos a la infraestructura. Este es el cluster de RabbitMQ: dos nodos, rabbit uno y rabbit dos, integrados en un mismo cluster y levantados con docker compose en la instancia del broker. Los microservicios se conectan a los dos. Si el primero no responde, Spring hace failover al segundo.

## 6:16 — Las seis colas

> Aca estan las seis colas que acabamos de ver declaradas. Tres de comando: correo, cocina y facturacion. Y la cola muerta de cada una, que es su respaldo cuando un mensaje no se puede entregar. En la columna de consumidores, las tres de comando tienen uno: es el microservicio de notificaciones conectado. Las colas muertas tienen cero, porque nadie consume de ellas: se revisan a mano.

## 6:38 — Los exchanges direct y topic

> Estos son los exchanges. Tenemos los dos tipos que pide la pauta, y cada uno resuelve un caso distinto. El direct enruta por clave exacta. Lo usa el ticket de cocina, que tiene un unico destinatario y no admite variantes. El topic enruta por patron con comodines. Lo usan el correo y el documento de cobro, que si las admiten. El dia que exista un email punto send punto urgente, el patron email punto asterisco lo recibe sin tocar un solo binding. El tercero, cmd punto dead punto dlx, es el exchange de dead lettering: es a donde el broker enruta lo que no se pudo entregar.

## 7:11 — Los argumentos de una cola real

> Y aca se ven aplicados, sobre una cola real, los argumentos que vimos en el codigo. El dead letter exchange, su clave de enrutamiento, y el tiempo de vida del mensaje en treinta mil milisegundos, o sea treinta segundos. Treinta segundos es deliberadamente corto, para poder demostrar en vivo que pasa cuando se cae un consumidor. Un despliegue real usaria minutos u horas, segun cuanto tolere el negocio que un comando llegue tarde.

## 7:34 — Los dos caminos hacia la cola muerta

> Antes de la ultima demostracion, quiero detenerme en las colas muertas, porque es donde esta lo mas interesante del diseno. Un mensaje llega a una cola muerta por dos caminos distintos, y el broker deja escrito cual fue, en un encabezado del propio mensaje llamado x death. El primero es rejected. El consumidor esta arriba y rechaza el mensaje porque viene mal formado: sin cliente, sin pedido, sin estado. Lo decide nuestro codigo, con un rechazo sin reencolar, porque reintentarlo daria exactamente el mismo resultado. El segundo es expired. Nadie lo consumio dentro del tiempo de vida de la cola, por ejemplo porque el consumidor esta caido. Lo decide el broker solo, sin que nadie intervenga. El mensaje estaba bien; lo que falto fue quien lo atendiera. Esa distincion importa: el sistema diferencia entre el mensaje estaba malo y el servicio no estaba disponible. Son dos problemas distintos y se resuelven distinto.

## 8:25 — La cola funcionando normal

> Vamos a demostrar el segundo camino, el de expired, en vivo. Partimos de la cola de cocina funcionando normal, con su consumidor enganchado. Un consumidor, cero mensajes encolados: todo lo que llega se procesa al instante.

## 8:38 — El consumidor caido

> Ahora bajo el microservicio consumidor. Vean como los consumidores pasan a cero. La cola sigue existiendo, con su configuracion intacta, pero no hay nadie del otro lado que la atienda.

## 8:50 — Se publica un comando valido

> Publico un comando perfectamente valido por el exchange direct, con la clave kitchen punto ticket. Quiero subrayar esto: el mensaje no tiene nada malo. Trae su pedido, su cliente y su cantidad de items. El problema no es el mensaje; es que el servicio que tenia que atenderlo no esta.

## 9:07 — El mensaje esperando

> El mensaje queda encolado. Un mensaje, cero consumidores. Si no hicieramos nada, se quedaria ahi para siempre, y la cola muerta no serviria de nada. Por eso configuramos el tiempo de vida. Ahora esperamos los treinta segundos.

## 9:37 — El mensaje vencido en la cola muerta

> Y ahi esta: vencio el tiempo de vida y el broker lo enruta solo a la cola muerta. La cola de comando queda en cero y la cola muerta pasa a uno. Nadie intervino, ni nuestro codigo ni nosotros: lo hizo RabbitMQ aplicando la politica que declaramos en la configuracion.

## 9:52 — El header x-death

> Y esta es la evidencia. Abro el mensaje dentro de la cola muerta, y el broker dejo escrito el motivo en el encabezado x death: expired. Nadie lo consumio a tiempo. Tambien queda registrada la cola de origen. Si en cambio un consumidor lo hubiera rechazado por venir mal formado, ahi diria rejected. Son los dos caminos que vimos recien, y el sistema los distingue solo.

## 10:13 — Resumen

> Eso es todo el flujo. Identidad federada con OAuth dos y OpenID Connect, el API Gateway validando el token en el borde, un cluster de dos nodos levantado con docker compose, seis colas con sus colas muertas, los dos tipos de exchange con trafico real, politicas de retencion y limpieza, y un fallo soportado sin perder el mensaje. Todo lo que vieron corre en el despliegue real y se puede reproducir en vivo. Muchas gracias.

---

## Preguntas probables

**¿Por que Kafka y RabbitMQ, y no uno solo?**

> Resuelven problemas con requisitos opuestos. El evento necesita muchos
> lectores independientes y un historial releible: eso es el log particionado de
> Kafka. El comando necesita un solo ejecutor, con confirmacion, reintento y
> descarte: eso es la cola con DLQ de RabbitMQ.

**¿Que pasa si se cae un nodo del cluster?**

> Lo medimos. El failover de conexion funciona: los servicios reconectan solos
> al otro nodo. Pero la cola queda indisponible, porque son colas clasicas sin
> espejado y viven en el nodo donde se declararon. Con dos nodos las quorum
> queues no lo arreglan: el quorum necesita mayoria, y la mayoria de dos es dos.
> Haria falta un tercer nodo.

**¿Como manejan la confirmacion de mensajes?**

> En modo MANUAL. Tres salidas, todas en una sola clase para que la politica sea
> identica en las tres colas: ACK si el trabajo termina bien; NACK con requeue
> si el error es recuperable y quedan intentos; NACK sin requeue hacia la DLQ si
> se agotan los intentos, si el error es del mensaje mismo, o si es inesperado.

**¿Por que una excepcion desconocida va directo a la cola muerta?**

> Es la opcion conservadora. Si no sabemos por que fallo, reintentar a ciegas
> puede dejar el mensaje girando y bloqueando a los que vienen detras.

**¿Donde esta el microservicio administrador?**

> ms-pedidos360-mqadmin, puerto 8086. Ocho endpoints REST para crear y eliminar
> colas, exchanges y bindings en caliente. Toda la interaccion con RabbitMQ esta
> encapsulada en RabbitAdminService. Solo rol Admin.

**¿Por que no usaron Amazon MQ?**

> Porque entonces no habria nada que demostrar: no hay docker-compose que
> mostrar, ni dos nodos que nosotros configuramos en cluster, y el
> microservicio administrador pierde sentido.

**¿Esto corre en la nube o en local?**

> La topologia es identica en los dos: el mismo cluster de dos nodos, las mismas
> seis colas, los mismos TTL, levantados con el mismo docker-compose y la misma
> configuracion. El despliegue en AWS esta en infra/aws y se levanta con un solo
> comando.
