# -*- coding: utf-8 -*-
"""
Guion narrado de la demostracion.

Cada tramo declara que se ve y que se dice encima. El ensamblador genera el
audio, mide cuanto dura, y recien entonces arma el video del tramo con esa
duracion: la narracion manda sobre el ritmo, no al reves. Asi ninguna
explicacion queda cortada a la mitad.

  kind = 'imagen'  una lamina fija (portada, codigo, cierre)
  kind = 'clip'    una grabacion de Playwright
"""

TRAMOS = [
    dict(id='portada', kind='imagen', src='montaje/png/portada.png', texto="""
        Buenas. Les voy a mostrar Pedidos trescientos sesenta, el sistema que
        desarrollamos para este ramo. Son seis microservicios en Spring Boot y
        un frontend en Angular, desplegados en Amazon Web Services.
        Por dentro usa dos sistemas de mensajeria con roles distintos: Kafka
        para eventos y RabbitMQ para comandos. Vamos a ver primero la
        aplicacion funcionando, y despues como se comunica por dentro.
    """),

    dict(id='app-login', kind='clip', src='entregables/6-login-catalogo-y-pedido.webm',
         marcas='app', desde='login', hasta='redirect', texto="""
        Empezamos por la pantalla de inicio de sesion. La aplicacion no tiene
        usuarios propios ni guarda contrasenas: delega la identidad en Azure.
        Cuando presione el boton, el navegador sale de nuestra aplicacion, va al
        proveedor, y vuelve con un codigo que se intercambia por un token de
        acceso. Es el flujo Authorization Code con PKCE, el que corresponde a
        una aplicacion de pagina unica.
    """),

    dict(id='app-redirect', kind='clip', src='entregables/6-login-catalogo-y-pedido.webm',
         marcas='app', desde='redirect', hasta='roles', texto="""
        Ahi fue, y volvio. El viaje de ida y vuelta al proveedor fue casi
        instantaneo porque la sesion ya estaba abierta en Azure. Si no lo
        estuviera, aca se veria la pantalla de credenciales del proveedor, que
        es justamente donde se escribe la contrasena: nunca en nuestra
        aplicacion.
    """),

    dict(id='app-roles', kind='clip', src='entregables/6-login-catalogo-y-pedido.webm',
         marcas='app', desde='roles', hasta='catalogo', texto="""
        Ya estamos dentro, y arriba aparece el usuario con sus cuatro roles:
        Admin, Operador, Cliente y Auditor. Esos roles no salen de nuestra base
        de datos: vienen dentro del token, en el claim roles, firmados por
        Azure. El backend los lee de ahi para decidir que puede hacer cada uno.
    """),

    dict(id='app-catalogo', kind='clip', src='entregables/6-login-catalogo-y-pedido.webm',
         marcas='app', desde='catalogo', hasta='formulario', texto="""
        Este es el catalogo, que sirve el microservicio de catalogo a traves del
        API Gateway. Son productos reales con su precio y su stock: pizzas,
        empanadas, bebidas. Cada llamada que ven aca viaja con el token, y el
        Gateway lo valida antes de dejarla pasar al backend.
    """),

    dict(id='app-formulario', kind='clip', src='entregables/6-login-catalogo-y-pedido.webm',
         marcas='app', desde='formulario', hasta='confirmar', texto="""
        Ahora armo un pedido con dos productos. Y aca hay un detalle que importa
        para la seguridad: el precio no lo manda el navegador. El frontend envia
        solamente el identificador del producto y la cantidad. El microservicio
        de pedidos le consulta el precio al catalogo y calcula el total del lado
        del servidor. Si alguien manipulara el precio desde el navegador, no
        serviria de nada.
    """),

    dict(id='app-creado', kind='clip', src='entregables/6-login-catalogo-y-pedido.webm',
         marcas='app', desde='confirmar', hasta='fin', texto="""
        Confirmo, y el pedido queda creado, en estado CREADO, con sus items y su
        total calculado. Esa sola accion acaba de publicar un evento a Kafka y
        tres comandos a RabbitMQ. En lo que sigue vamos a ver exactamente a
        donde fueron a parar.
    """),

    dict(id='consumidores', kind='clip', src='entregables/7-auditoria-y-reportes.webm', texto="""
        Aca esta la otra mitad del desacoplamiento. Auditoria y reportes no le
        preguntan nada al microservicio de pedidos: se alimentan solos de los
        eventos que viajan por Kafka.
        En auditoria aparece el evento OrderCreated del pedido que acabo de
        crear, y en reportes los indicadores ya lo incluyen. Nadie los llamo.
        Si manana quisiera un cuarto servicio que reaccione a los pedidos, lo
        conecto al mismo topico y el de pedidos no cambia una linea.
    """),

    dict(id='paralelo-inicio', kind='clip', src='entregables/8-app-y-broker-en-paralelo.mp4',
         marcas='paralelo', desde='inicio', hasta='pausa', texto="""
        Ahora quiero demostrar que la conexion entre la aplicacion y el broker
        es real. A la izquierda la aplicacion, a la derecha la consola de
        RabbitMQ: dos navegadores distintos, corriendo al mismo tiempo contra el
        mismo despliegue.
        Miren la derecha antes de que pase nada: las seis colas en cero.
    """),

    dict(id='paralelo-pausa', kind='clip', src='entregables/8-app-y-broker-en-paralelo.mp4',
         marcas='paralelo', desde='pausa', hasta='confirmacion', texto="""
        Antes de confirmar voy a pausar el consumidor, y conviene que explique
        por que.
        Con los tres consumidores andando, un mensaje se publica y se procesa en
        milisegundos. La consola muestrea cada cinco segundos: cuando refresca,
        el contador ya volvio a cero y parece que no paso nada. Pausando el
        consumidor, el mensaje se queda a la vista. Es el mismo trafico; lo
        unico que hago es hacerlo visible.
    """),

    dict(id='paralelo-mensajes', kind='clip', src='entregables/8-app-y-broker-en-paralelo.mp4',
         marcas='paralelo', desde='confirmacion', hasta='reanudacion', texto="""
        Confirmo el pedido. Y miren la derecha: las tres colas de comando pasan
        a tener un mensaje cada una. Correo, cocina y facturacion. Una sola
        accion del usuario, tres comandos publicados.
        Fijense ademas en que columna estan: Unacked, no Ready. El consumidor ya
        los recibio, pero todavia no los confirmo. Es la confirmacion manual a
        la vista: el mensaje no se da por procesado hasta que el codigo lo
        confirma explicitamente.
        Y si dudan de que sea el mismo instante, ahi estan los relojes, de dos
        navegadores distintos.
    """),

    dict(id='paralelo-drenaje', kind='clip', src='entregables/8-app-y-broker-en-paralelo.mp4',
         marcas='paralelo', desde='reanudacion', hasta='fin', texto="""
        Ahora reanudo el consumidor. Y las tres colas vuelven a cero: los tres
        mensajes se procesaron y se confirmaron.
        Ese es el ciclo completo visto desde los dos lados a la vez. La
        aplicacion publico, las colas los retuvieron mientras no hubo quien los
        atendiera, y el consumidor los proceso en cuanto volvio.
        Y noten que ninguno fue a parar a una cola muerta: se consumieron dentro
        del tiempo de vida. En un rato vamos a ver que pasa cuando no llegan a
        tiempo.
    """),

    dict(id='cfg-1-yml', kind='imagen', src='montaje/laminas/png/cfg-1-yml.png', texto="""
        Veamos ahora como esta construida cada cola, porque no hay nada
        improvisado.
        Toda la topologia se declara en el application punto yaml. Aca estan los
        tres exchanges, las politicas de retencion, y los tres flujos: correo,
        cocina y facturacion. Cada flujo define el nombre de su cola, su clave
        de enrutamiento exacta, y el patron con comodines para el exchange de
        tipo topic.
        No hay ni un nombre de cola escrito en el codigo Java. Cambiar como se
        llama una cola es cambiar esta configuracion.
    """),

    dict(id='cfg-2-cola', kind='imagen', src='montaje/laminas/png/cfg-2-cola.png', texto="""
        Y esta es la construccion de cada cola de comando, en una sola funcion
        que los tres flujos comparten.
        Se declara durable, para que sobreviva al reinicio del broker. Despues
        van dos argumentos de dead lettering: el exchange al que van los
        mensajes que no se pudieron entregar, y la clave con la que se enrutan.
        Y el tercero es el que mas importa, el resaltado: equis message t t l.
        Define cuanto vive un comando sin que nadie lo consuma. Sin el, con el
        consumidor caido el mensaje quedaria encolado para siempre y la cola
        muerta no se usaria nunca.
    """),

    dict(id='cfg-3-dlq', kind='imagen', src='montaje/laminas/png/cfg-3-dlq.png', texto="""
        La cola muerta de cada flujo se construye aparte, y tiene su propia
        politica.
        Guarda la evidencia catorce dias, y tiene un tope de mil mensajes, para
        que una tormenta de errores no llene el disco del nodo. Esas dos cosas
        juntas son la politica de retencion y limpieza.
        Y fijense en lo que no tiene: la cola muerta no tiene dead lettering
        propio. Eso es a proposito. Lo que vence aqui se descarta, y ese
        descarte es justamente la limpieza. Si la cola muerta reenviara a otro
        lado, los mensajes no se irian nunca.
    """),

    dict(id='cfg-4-bindings', kind='imagen', src='montaje/laminas/png/cfg-4-bindings.png', texto="""
        Cada flujo se conecta con tres bindings. Este es el ejemplo del flujo de
        cocina.
        El primero lo une al exchange direct, con una clave exacta. El segundo
        al exchange topic, con un patron que admite comodines. Y el tercero, el
        resaltado, cuelga la cola muerta del dead letter exchange, con la misma
        clave.
        Ese tercero es el que cierra el circuito: es lo que hace que un mensaje
        rechazado o vencido termine en la cola muerta correcta, y no en
        cualquier otra.
    """),

    dict(id='cluster', kind='clip', src='entregables/1-cluster-dos-nodos.webm',
         marcas='cluster-nodos', desde='listo', texto="""
        Pasemos a la infraestructura. Este es el cluster de RabbitMQ: dos nodos,
        rabbit uno y rabbit dos, integrados en un mismo cluster y levantados con
        docker compose en la instancia del broker.
        Los microservicios se conectan a los dos. Si el primero no responde,
        Spring hace failover al segundo.
    """),

    dict(id='colas', kind='clip', src='entregables/2-seis-colas-y-sus-dlq.webm',
         marcas='cluster-colas', desde='listo', texto="""
        Aca estan las seis colas que acabamos de ver declaradas. Tres de
        comando: correo, cocina y facturacion. Y la cola muerta de cada una, que
        es su respaldo cuando un mensaje no se puede entregar.
        En la columna de consumidores, las tres de comando tienen uno: es el
        microservicio de notificaciones conectado. Las colas muertas tienen
        cero, porque nadie consume de ellas: se revisan a mano.
    """),

    dict(id='exchanges', kind='clip', src='entregables/3-exchanges-direct-y-topic.webm',
         marcas='cluster-exchanges', desde='listo', texto="""
        Estos son los exchanges. Tenemos los dos tipos que pide la pauta, y cada
        uno resuelve un caso distinto.
        El direct enruta por clave exacta. Lo usa el ticket de cocina, que tiene
        un unico destinatario.
        El topic enruta por patron con comodines. Lo usan el correo y el
        documento de cobro: el dia que exista un email punto send punto urgente,
        el patron email punto asterisco lo recibe sin tocar un binding.
        El tercero es el exchange de dead lettering, a donde el broker enruta lo
        que no se pudo entregar.
    """),

    dict(id='retencion', kind='clip', src='entregables/4-politicas-de-retencion.webm',
         marcas='cluster-retencion', desde='listo', texto="""
        Y aca se ven aplicados, sobre una cola real, los argumentos que vimos en
        el codigo. El dead letter exchange, su clave de enrutamiento, y el
        tiempo de vida del mensaje en treinta mil milisegundos, o sea treinta
        segundos.
        Treinta segundos es deliberadamente corto, para poder demostrar en vivo
        que pasa cuando se cae un consumidor. Un despliegue real usaria minutos
        u horas, segun cuanto tolere el negocio que un comando llegue tarde.
    """),

    dict(id='dlq-caminos', kind='imagen', src='montaje/laminas/png/dlq-1-caminos.png', texto="""
        Antes de la ultima demostracion, quiero detenerme en las colas muertas.
        Un mensaje llega a una por dos caminos distintos, y el broker deja
        escrito cual fue, en un encabezado del propio mensaje llamado x death.
        El primero es rejected: el consumidor esta arriba y rechaza el mensaje
        porque viene mal formado, sin cliente o sin pedido. Lo decide nuestro
        codigo, con un rechazo sin reencolar, porque reintentarlo daria el mismo
        resultado.
        El segundo es expired: nadie lo consumio dentro del tiempo de vida de la
        cola, por ejemplo porque el consumidor esta caido. Lo decide el broker
        solo. El mensaje estaba bien; falto quien lo atendiera.
        El sistema diferencia entre el mensaje estaba malo y el servicio no
        estaba disponible. Son dos problemas distintos y se resuelven distinto.
    """),

    dict(id='fallo-partida', kind='clip', src='entregables/5-caida-de-consumidor.webm',
         marcas='fallo', desde='partida', hasta='caida', texto="""
        Vamos a demostrar el segundo camino, el de expired, en vivo.
        Partimos de la cola de cocina funcionando normal, con su consumidor
        enganchado. Un consumidor, cero mensajes encolados: todo lo que llega se
        procesa al instante.
    """),

    dict(id='fallo-caida', kind='clip', src='entregables/5-caida-de-consumidor.webm',
         marcas='fallo', desde='caida', hasta='publicar', texto="""
        Ahora bajo el microservicio consumidor. Vean como los consumidores pasan
        a cero. La cola sigue existiendo, con su configuracion intacta, pero no
        hay nadie del otro lado que la atienda.
    """),

    dict(id='fallo-publicar', kind='clip', src='entregables/5-caida-de-consumidor.webm',
         marcas='fallo', desde='publicar', hasta='espera', texto="""
        Publico un comando perfectamente valido por el exchange direct, con la
        clave kitchen punto ticket. Quiero subrayar esto: el mensaje no tiene
        nada malo. Trae su pedido, su cliente y su cantidad de items. El
        problema no es el mensaje; es que el servicio que tenia que atenderlo no
        esta.
    """),

    dict(id='fallo-espera', kind='clip', src='entregables/5-caida-de-consumidor.webm',
         marcas='fallo', desde='espera', hasta='vencido', texto="""
        El mensaje queda encolado. Un mensaje, cero consumidores. Si no
        hicieramos nada, se quedaria ahi para siempre, y la cola muerta no
        serviria de nada. Por eso configuramos el tiempo de vida.
        Ahora esperamos los treinta segundos.
    """),

    dict(id='fallo-vencido', kind='clip', src='entregables/5-caida-de-consumidor.webm',
         marcas='fallo', desde='vencido', hasta='motivo', texto="""
        Y ahi esta: vencio el tiempo de vida y el broker lo enruta solo a la
        cola muerta. La cola de comando queda en cero y la cola muerta pasa a
        uno. Nadie intervino, ni nuestro codigo ni nosotros: lo hizo RabbitMQ
        aplicando la politica que declaramos en la configuracion.
    """),

    dict(id='fallo-motivo', kind='clip', src='entregables/5-caida-de-consumidor.webm',
         marcas='fallo', desde='motivo', hasta='fin', texto="""
        Y esta es la evidencia. Abro el mensaje dentro de la cola muerta, y el
        broker dejo escrito el motivo en el encabezado x death: expired. Nadie
        lo consumio a tiempo. Tambien queda registrada la cola de origen.
        Si en cambio un consumidor lo hubiera rechazado por venir mal formado,
        ahi diria rejected. Son los dos caminos que vimos recien, y el sistema
        los distingue solo.
    """),

    dict(id='cierre', kind='imagen', src='montaje/png/cierre.png', texto="""
        Eso es todo el flujo. Identidad federada con OAuth dos y OpenID Connect,
        el API Gateway validando el token en el borde, un cluster de dos nodos
        levantado con docker compose, seis colas con sus colas muertas, los dos
        tipos de exchange con trafico real, politicas de retencion y limpieza, y
        un fallo soportado sin perder el mensaje.
        Todo lo que vieron corre en el despliegue real y se puede reproducir en
        vivo. Muchas gracias.
    """),
]
