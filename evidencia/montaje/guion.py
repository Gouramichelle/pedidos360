# -*- coding: utf-8 -*-
"""
Genera GUION.md desde la misma fuente que produce la voz.

Toma el texto de narracion.py y los tiempos reales de linea-tiempo.json, que
escribe el ensamblador despues de medir cada pista de audio. Asi los minutos
del indice no pueden quedar desfasados del video: salen del video.

Cada tramo lleva ademas su ruta en vivo: que abrir o que correr para mostrar
en el momento lo mismo que se ve en pantalla, por si el docente lo pide.

    python3 montaje/guion.py
"""

import json
import pathlib
import re
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import narracion  # noqa: E402

RAIZ = pathlib.Path(__file__).resolve().parent.parent

TITULOS = {
    'portada':           'Portada',
    'app-login':         'La pantalla de inicio de sesion',
    'app-redirect':      'La vuelta del proveedor de identidad',
    'app-roles':         'El dashboard con los cuatro roles',
    'app-catalogo':      'El catalogo con precios y stock',
    'app-formulario':    'El formulario del pedido',
    'app-creado':        'El pedido creado',
    'consumidores':      'Auditoria y reportes',
    'paralelo-inicio':   'Las dos pantallas, seis colas en cero',
    'paralelo-pausa':    'Por que se pausa el consumidor',
    'paralelo-mensajes': 'Las tres colas con su mensaje, en Unacked',
    'paralelo-drenaje':  'Las colas vuelven a cero',
    'cfg-1-yml':         'La topologia en el application.yml',
    'cfg-2-cola':        'Como se construye una cola de comando',
    'cfg-3-dlq':         'Como se construye una cola muerta',
    'cfg-4-bindings':    'Los tres bindings por flujo',
    'cluster':           'Los dos nodos del cluster',
    'colas':             'Las seis colas',
    'exchanges':         'Los exchanges direct y topic',
    'retencion':         'Los argumentos de una cola real',
    'dlq-caminos':       'Los dos caminos hacia la cola muerta',
    'fallo-partida':     'La cola con su consumidor',
    'fallo-caida':       'El consumidor caido, cero consumidores',
    'fallo-publicar':    'Se publica un comando valido',
    'fallo-espera':      'El mensaje esperando sin quien lo atienda',
    'fallo-vencido':     'Vencido, ya en la cola muerta',
    'fallo-motivo':      'El header x-death con reason expired',
    'cierre':            'Resumen',
}

# Que abrir o que correr para mostrar en vivo lo que el tramo muestra grabado.
# El primer elemento es el camino corto; lo demas es por si piden mas detalle.
RUTAS = {
    'app-login': [
        'Abrir la aplicacion: **`http://localhost:4200`** (en AWS, el `APPS_IP_PUBLICA` '
        'de `infra/aws/entorno-actual.env`, puerto 8085).',
        'Si preguntan donde se escribe la contrasena: presionar el boton y mostrar '
        'que el navegador cambia de dominio al de Microsoft.',
    ],
    'app-redirect': [
        'Con la sesion de Azure ya abierta el salto es instantaneo. Para mostrar la '
        'pantalla de credenciales, abrir la app en una ventana de incognito.',
        'Para mostrar el token: **F12 → Application → Session Storage**, y pegar el '
        '`access_token` en `jwt.ms` para ver el claim `roles`.',
    ],
    'app-roles': [
        'El selector **Ver segun rol** de la barra superior cambia los modulos visibles '
        'sin volver a entrar.',
        'Para probar que el backend tambien lo valida, no solo el frontend: ver el '
        'punto 9 de PASO-A-PASO.md (401 sin token).',
    ],
    'app-catalogo': [
        'Menu lateral → **Catalogo**. Lo sirve ms-catalog, no ms-orders.',
    ],
    'app-formulario': [
        'Menu lateral → **Pedidos → + Nuevo pedido**.',
    ],
    'app-creado': [
        'Confirmar el pedido y quedarse en el detalle: ahi estan el numero, el total '
        'y la hora de creacion.',
    ],
    'consumidores': [
        'Menu lateral → **Auditoria** y → **Reportes**. Los alimenta Kafka, no una '
        'llamada a ms-orders.',
        'Para mostrar que nadie llamo a nadie, mirar la terminal donde corre '
        '**ms-audit**: se ve el consumo del topico `orders.events`, no una peticion '
        'HTTP entrante. (En AWS: `docker logs apps-ms-audit-1 --tail 10`.)',
    ],
    'paralelo-inicio': [
        'Dos ventanas lado a lado: la aplicacion y **`http://localhost:15672`** '
        '(usuario `guest`, clave `guest`), pestaña **Queues and Streams**.',
        'En AWS la consola no esta expuesta a internet: hay que abrir el tunel SSH '
        'primero. Ver el punto 1 de PASO-A-PASO.md.',
        'Poner el **Refresh every 5 seconds** del selector de arriba a la derecha.',
    ],
    'paralelo-pausa': [
        'Para que los mensajes se queden a la vista, **suspender** ms-notify antes de '
        'confirmar. Suspenderlo en vez de matarlo deja la conexion abierta, asi que la '
        'cola sigue mostrando su consumidor y los mensajes quedan en **Unacked**:\n'
        '    ```bash\n'
        '    pkill -STOP -f ms-pedidos360-notify\n'
        '    ```\n'
        '    En AWS: `docker compose pause ms-notify` en `~/infra/apps`.',
        'Si no se baja, igual se ve: en **Overview → Message rates** el pico queda '
        'una ventana movil de un minuto.',
    ],
    'paralelo-mensajes': [
        'Confirmar el pedido y mirar **Queues and Streams**: las tres colas de comando '
        'pasan a 1.',
        'La columna importante es **Unacked**, no Ready: el consumidor recibio el '
        'mensaje pero todavia no lo confirmo. Es el modo MANUAL.',
    ],
    'paralelo-drenaje': [
        'Reanudar el consumidor y ver las colas volver a cero:\n'
        '    ```bash\n'
        '    pkill -CONT -f ms-pedidos360-notify\n'
        '    ```\n'
        '    En AWS: `docker compose unpause ms-notify`.',
        'Los ACK quedan en el log de ms-notify, uno por flujo, con el **mismo** '
        '`correlationId`: los tres comandos vinieron de la misma accion del usuario.',
    ],
    'cfg-1-yml': [
        'Abrir `ms-pedidos360-notify/src/main/resources/application.yml`, bloque '
        '`mensajeria`.',
        'El argumento de que no hay nombres sueltos en el codigo:\n'
        '    ```bash\n'
        '    grep -rn \'"q.cmd\\|"cmd.direct\\|"cmd.topic\' --include=*.java . | grep -v test\n'
        '    ```\n'
        '    No devuelve nada.',
    ],
    'cfg-2-cola': [
        'Abrir `ms-pedidos360-notify/.../config/RabbitTopologyConfig.java`, metodo '
        '`colaDeFlujo`.',
        'Para mostrar que el argumento llego de verdad al broker, ver el tramo de '
        'retencion: la consola lo muestra en **Features**.',
    ],
    'cfg-3-dlq': [
        'Mismo archivo, metodo `colaMuertaDeFlujo`.',
        'La diferencia que conviene subrayar: la cola muerta **no** tiene '
        '`x-dead-letter-exchange`. Es el final del camino, no un eslabon mas.',
    ],
    'cfg-4-bindings': [
        'Mismo archivo, los beans de `Binding`.',
        'En la consola: **Exchanges → cmd.topic → Bindings** muestra los mismos '
        'patrones que el codigo declara.',
    ],
    'cluster': [
        'Consola → pestaña **Overview** → bajar hasta **Nodes**: `rabbit@rabbit1` y '
        '`rabbit@rabbit2`.',
        'La prueba de que es un cluster y no dos brokers sueltos: abrir '
        '**`http://localhost:15673`** (el nodo 2) y ver **las mismas colas**.',
        'Desde la terminal, en **Running Nodes** salen los dos:\n'
        '    ```bash\n'
        '    docker exec local-rabbit1-1 rabbitmq-diagnostics -q cluster_status\n'
        '    ```',
    ],
    'colas': [
        'Consola → **Queues and Streams**. Seis: tres de comando y sus tres DLQ.',
        'La columna **Consumers**: 1 en las de comando, 0 en las DLQ. Nadie consume '
        'de una cola muerta; se revisan a mano.',
    ],
    'exchanges': [
        'Consola → **Exchanges**. Los nuestros son `cmd.direct`, `cmd.topic` y '
        '`cmd.dead.dlx`; el resto (`amq.*`) los trae RabbitMQ de fabrica.',
        'Click en **cmd.direct** → claves exactas. Click en **cmd.topic** → patrones '
        'con comodin.',
    ],
    'retencion': [
        'Consola → **Queues → q.cmd.kitchen → Details → Features**: ahi estan '
        '`x-message-ttl`, `x-dead-letter-exchange` y `x-dead-letter-routing-key`.',
        'Despues **q.cmd.kitchen.dlq**: tiene su propia politica, TTL de 14 dias y '
        '`x-max-length` 1000. Esa es la politica de limpieza.',
    ],
    'dlq-caminos': [
        'El camino `expired` se demuestra entero en los tramos siguientes.',
        'Para el camino `rejected`, publicar un mensaje **mal formado** con el '
        'consumidor **arriba**: **Exchanges → cmd.direct → Publish message**, routing '
        'key `kitchen.ticket`, payload `{"type":"KitchenTicketRequested"}` (sin '
        '`payload`). Llega a la DLQ al instante, con `reason: rejected`.',
    ],
    'fallo-partida': [
        'Consola → **q.cmd.kitchen**: **Consumers 1**. Ese es el punto de partida.',
    ],
    'fallo-caida': [
        'Bajar el consumidor del todo —aca si matarlo, no suspenderlo, para que la '
        'cola pase a **Consumers 0**:\n'
        '    ```bash\n'
        '    pkill -f ms-pedidos360-notify\n'
        '    ```\n'
        '    En AWS: `docker compose stop ms-notify`.',
    ],
    'fallo-publicar': [
        'Consola → **Exchanges → cmd.direct → Publish message**.',
        'Routing key `kitchen.ticket`, property `content_type` = `application/json`, '
        'payload:\n'
        '    ```json\n'
        '    {"type":"KitchenTicketRequested","eventId":"demo-manual",\n'
        '     "correlationId":"demo",\n'
        '     "payload":{"orderId":99,"customerId":"cliente-99","cantidadItems":3}}\n'
        '    ```',
        'Subrayar que **el mensaje no tiene nada malo**. El problema no es el mensaje: '
        'es que el servicio que debia atenderlo no esta.',
    ],
    'fallo-espera': [
        'Consola → **Queues → q.cmd.kitchen**: 1 mensaje, 0 consumidores.',
        'Son 30 segundos de espera. Es buen momento para mostrar el `x-message-ttl` '
        'en **Features** de esa misma pantalla: eso es lo que esta corriendo.',
    ],
    'fallo-vencido': [
        'Sin tocar nada: `q.cmd.kitchen` vuelve a 0 y **q.cmd.kitchen.dlq pasa a 1**.',
        'Lo hizo el broker solo. Nadie intervino, y el consumidor sigue abajo.',
    ],
    'fallo-motivo': [
        'Consola → **q.cmd.kitchen.dlq → Get messages → Get Message(s)**.',
        'En los **headers** aparece `x-death`, y adentro `reason: expired` y '
        '`queue: q.cmd.kitchen`.',
        'Levantar el consumidor para dejar todo como estaba. Tarda unos quince '
        'segundos en reconectar:\n'
        '    ```bash\n'
        '    cd ms-pedidos360-notify && mvn spring-boot:run -Dspring-boot.run.profiles=dev\n'
        '    ```',
    ],
    'cierre': [
        'Si piden verificar todo de una vez, con los seis servicios arriba:\n'
        '    ```bash\n'
        '    for p in 8080 8081 8082 8083 8084 8086; do \\\n'
        '      curl -s -o /dev/null -w "$p: %{http_code}\\n" http://localhost:$p/actuator/health; \\\n'
        '    done\n'
        '    ```\n'
        '    Contra AWS: `./infra/aws/verificar.sh ~/Downloads/labsuser.pem`, que recorre '
        'ademas el 401 sin token, el preflight de CORS, los dos nodos y las seis colas.',
    ],
}

PREAMBULO_VIVO = """\
## Antes de presentar

Dejar esto andando y probado **antes** de entrar a la sala. Es lo unico que no
se puede improvisar.

**1. La infraestructura** (PostgreSQL, el cluster de dos nodos y Kafka):

```bash
docker compose -f infra/local/docker-compose.yml up -d
```

**2. Los seis microservicios**, cada uno en su terminal, con el perfil `dev` que
ya apunta a esa infraestructura:

```bash
cd ms-pedidos360-orders && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Lo mismo para `catalog`, `notify`, `report`, `audit` y `mqadmin`.

**3. El frontend:**

```bash
cd frontend-pedidos360 && npm start
```

| Que | Donde |
|---|---|
| Aplicacion | `http://localhost:4200` |
| Consola RabbitMQ, nodo 1 | `http://localhost:15672` (`guest` / `guest`) |
| Consola RabbitMQ, nodo 2 | `http://localhost:15673` |
| Health de los seis | `8080` `8081` `8082` `8083` `8084` `8086` `/actuator/health` |

Dejar la consola en **Queues and Streams** con el refresco en **5 seconds**, y
la aplicacion ya con la sesion de Azure iniciada. El login usa el tenant real de
Azure AD: la identidad es lo unico que no se levanta en local.

> **Si la demostracion es contra AWS**, las IPs salen de
> `infra/aws/entorno-actual.env`, los servicios corren en contenedores
> (`docker compose` en `~/infra/apps`), y la consola del broker **no** esta
> expuesta a internet: hay que abrir el tunel SSH primero. El procedimiento
> completo esta en [PASO-A-PASO.md](PASO-A-PASO.md), punto 1.

---
"""

def minutos(segundos):
    return f"{int(segundos) // 60}:{int(segundos) % 60:02d}"


def parrafo(texto):
    """El texto del tramo viene indentado en el fuente; lo deja en una sola linea."""
    return re.sub(r'\s+', ' ', texto).strip()


def main():
    linea = {x['id']: x for x in json.loads((RAIZ / 'montaje/linea-tiempo.json').read_text())}
    faltan = [t['id'] for t in narracion.TRAMOS if t['id'] not in linea]
    if faltan:
        sys.exit(f"linea-tiempo.json no tiene {faltan}. Corre primero ensamblar.py.")

    total = max(x['inicio'] + x['dur'] for x in linea.values())
    preguntas = (RAIZ / 'GUION.md').read_text().split('## Preguntas probables', 1)
    preguntas = '## Preguntas probables' + preguntas[1] if len(preguntas) > 1 else ''

    out = [
        "# Guion de la demostracion",
        "",
        "El video **ya viene narrado**: `entregables/pedidos360-demostracion.mp4`,",
        f"{minutos(total)} min, con voz en español. No hace falta leer nada encima.",
        "",
        "Este documento es el texto de esa narracion, con **la ruta en vivo de cada",
        "tramo**: que abrir o que correr para mostrar en el momento lo mismo que se ve",
        "grabado, por si el docente lo pide. Se genera desde `montaje/narracion.py` y de",
        "los tiempos que mide el ensamblador, asi que no puede quedar desfasado del",
        "video.",
        "",
        "Cada tramo se verifico extrayendo un fotograma del video y comprobando que",
        "muestre lo que la voz dice en ese momento. Ademas, las afirmaciones con numeros",
        "—cuantos consumidores tiene una cola, cuantos mensajes— las comprueban las",
        "propias pruebas contra la API del broker mientras graban: si no fueran ciertas,",
        "la grabacion falla en vez de producir un video que miente.",
        "",
        "---",
        "",
        PREAMBULO_VIVO,
        "## Indice",
        "",
        "| Momento | Que se ve |",
        "|---|---|",
    ]
    for t in narracion.TRAMOS:
        out.append(f"| **{minutos(linea[t['id']]['inicio'])}** | {TITULOS[t['id']]} |")

    out += ["", "---", ""]
    for t in narracion.TRAMOS:
        out += [f"## {minutos(linea[t['id']]['inicio'])} — {TITULOS[t['id']]}", ""]
        out += [f"> {parrafo(t['texto'])}", ""]
        if t['id'] in RUTAS:
            out.append("**En vivo:**")
            out += [f"- {r}" for r in RUTAS[t['id']]]
            out.append("")

    out += ["---", "", preguntas] if preguntas else []
    (RAIZ / 'GUION.md').write_text("\n".join(out).rstrip() + "\n")
    print(f"GUION.md: {len(narracion.TRAMOS)} tramos, {minutos(total)} min")


if __name__ == '__main__':
    main()
