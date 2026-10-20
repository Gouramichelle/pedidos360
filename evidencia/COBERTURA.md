# Cobertura de las rubricas

Donde queda demostrada cada cosa, y con que se comprueba. EP3 y EP4 comparten
la misma rubrica de ocho indicadores; lo que cambia es que EP4 ademas pide
mostrarlo en vivo.

Los comandos de la columna **Como se comprueba** se corren desde la raiz del
repositorio y devuelven lo que dice la fila. Si alguna vez dejan de devolverlo,
la afirmacion dejo de ser cierta.

---

## Los ocho indicadores, en el codigo

| # | Indicador | Donde esta | Como se comprueba |
|---|---|---|---|
| 1 | Topologia declarada por codigo | `config/RabbitTopologyConfig.java` en orders y notify, 18 beans cada uno | `grep -c '@Bean' ms-pedidos360-notify/.../RabbitTopologyConfig.java` → 18 |
| 2 | Nombres centralizados, no incrustados | Bloque `mensajeria` del `application.yml` | `grep -rn '"q\.cmd\|"cmd\.' --include="*.java" . \| grep -v /test/` → sin resultados |
| 3 | Tres flujos de comando en tres dominios | `EmailNotificationListener`, `KitchenTicketListener`, `InvoiceGenerationListener` | `grep -rln "@RabbitListener" --include="*.java" .` → los tres |
| 4 | Los dos tipos de exchange | `cmd.direct` y `cmd.topic`, mas `cmd.dead.dlx` | Consola → Exchanges, o `curl -u guest:guest localhost:15672/api/exchanges` |
| 5 | Confirmacion manual con rutas diferenciadas | `messaging/comun/ConfirmacionDeMensajes.java` | `grep -rn "AcknowledgeMode.MANUAL" --include="*.java" .` → 1, y tres llamadas al canal: `basicAck`, `basicNack(...,true)`, `basicNack(...,false)` |
| 6 | Microservicio administrador | `ms-pedidos360-mqadmin`, puerto 8086, ocho endpoints | Diapositiva 36 del deck tiene la tabla completa |
| 7 | La capa web no conoce el broker | Todo encapsulado en `RabbitAdminService` | `grep -rn "^import org.springframework.amqp" ms-pedidos360-mqadmin/.../web/` → 0 |
| 8 | Validacion de entrada | 22 anotaciones en los DTO de mqadmin: 7 `@Pattern`, 7 `@Size`, 5 `@NotBlank`, 3 `@Valid` | `grep -rho "@NotBlank\|@Pattern\|@Size\|@Valid" ms-pedidos360-mqadmin/src/main/java \| sort \| uniq -c` |

**Estado de las pruebas:** los seis servicios pasan, **126 tests**, cero
fallos y cero saltados, corridos con el cluster levantado el 2026-10-20.

| Servicio | Tests |
|---|---|
| orders | 34 |
| notify | 34 |
| mqadmin | 28 |
| report | 12 |
| audit | 10 |
| catalog | 8 |

Las de integracion contra el broker se saltan solas si no hay broker levantado,
asi que sin el cluster arriba el total baja. El frontend compila limpio.

---

## Lo que pidio el profesor de viva voz

No esta en la pauta escrita, pero lo dijo en clase.

| Lo que pidio | Donde queda |
|---|---|
| Seis colas: tres de comando mas su respaldo | Video 6:03 · deck 31 |
| El escenario del consumidor caido, con el tiempo en cola configurado | Video 7:58 a 9:25 · deck 33 |
| Un dibujo de productores, colas y consumidores | `docs/mensajeria.md` |
| Logs de los consumidores mostrando que se consumio | Guion, ruta en vivo del tramo 3:33 |
| Los dos escenarios: camino feliz y camino con fallo | Video 3:04 (feliz) y 7:58 (fallo) |
| Metricas de monitoreo del cluster | Consola → Overview, y el deck 30 |

---

## Donde se demuestra cada cosa

Tres soportes que se reparten el trabajo. Ninguno cubre todo solo.

| | Que cubre |
|---|---|
| **[El video](entregables/pedidos360-demostracion.mp4)**, 9:58, grabado contra AWS | La aplicacion funcionando y **toda** la mensajeria: colas, exchanges, retencion, confirmacion manual, los dos caminos a la cola muerta y la caida de un consumidor de punta a punta |
| **El deck**, `presentacion/Pedidos360-Presentacion.pptx`, 43 diapositivas | El inquilino de Azure y sus usuarios, la red y los servidores, el API Manager con sus rutas y su CORS, y desde ahora la seccion de mensajeria (29 a 36) |
| **[El guion](GUION.md)** | El texto de la narracion con la ruta en vivo de cada tramo, para responder en el momento |

---

## El punto 3, cerrado

El 20 de octubre se regrabo todo contra el despliegue en AWS. Los ocho clips y
el video armado salen del entorno desplegado, no del local.

| Punto de la lista de demo | Donde queda |
|---|---|
| La instancia de API Manager y su configuracion | Deck 25 a 27 · el Invoke URL responde |
| El API Manager validando el JWT | Deck 37 · 401 sin token y con token falso, verificado |
| **El frontend consumiendo a traves del API Manager** | **Video, desde 0:22.** El bundle servido en `https://54.224.223.31:8085` referencia unicamente el Invoke URL del Gateway, con **cero** referencias a localhost |
| El inquilino de IDaaS y sus usuarios | Deck 6 a 8 · el login del video es contra el tenant real |
| El despliegue en la nube y el docker-compose corriendo alla | Video completo · `verificar.sh` en verde |

**Como se comprueba que el frontend no llama a localhost:**

```bash
JS=$(curl -sk https://54.224.223.31:8085/ | grep -oE 'main[^"]*\.js' | head -1)
curl -sk "https://54.224.223.31:8085/$JS" > /tmp/main.js
grep -oE 'https://[a-z0-9]+\.execute-api\.[a-z0-9-]+\.amazonaws\.com' /tmp/main.js | sort -u
grep -c 'localhost:80' /tmp/main.js
```

Devuelve el Invoke URL del Gateway, y **0** para localhost.

**Verificacion del despliegue:** 13 de 13 comprobaciones en verde —frontend, los
seis health, 401 sin token, 401 con token invalido, preflight 200, dos nodos del
cluster, seis colas con politica de retencion.

El escenario de caida del consumidor se ejecuto **sobre la instancia real**: el
test detuvo `apps-ms-notify-1`, espero a que venciera el TTL, comprobo que el
mensaje llegara a la cola muerta con `reason: expired`, y volvio a levantarlo.

---

## El API Manager y el despliegue, capturados

Salida real del 20 de octubre, no transcrita de la pantalla de configuracion.

### La instancia y su configuracion

```
Nombre     pedidos360-api        Protocolo  HTTP
Endpoint   https://0b7fhgzl22.execute-api.us-east-1.amazonaws.com

CORS   AllowOrigins   https://54.224.223.31:8085
       AllowMethods   GET, POST, PUT, PATCH, DELETE
       AllowHeaders   authorization, content-type
       MaxAge         3600
```

`aws apigatewayv2 get-api --api-id 0b7fhgzl22`

### El validador de token

```
Nombre      pedidos360-jwt       Tipo  JWT
Fuente      $request.header.Authorization
Emisor      https://login.microsoftonline.com/2d0922c9-.../v2.0
Audiencias  api://d0120ca1-...   y   d0120ca1-...
```

Las dos audiencias son a proposito: Azure AD emite tokens v1 con `aud`
`api://<id>` y v2 con el GUID pelado. Aceptar ambas evita 401 silenciosos si
cambia `requestedAccessTokenVersion` en el manifiesto.

### Las doce rutas

```
JWT   ANY /api/orders              NONE  OPTIONS /api/orders
JWT   ANY /api/orders/{proxy+}     NONE  OPTIONS /api/orders/{proxy+}
JWT   ANY /api/catalog/{proxy+}    NONE  OPTIONS /api/catalog/{proxy+}
JWT   ANY /api/report/{proxy+}     NONE  OPTIONS /api/report/{proxy+}
JWT   ANY /api/audit/{proxy+}      NONE  OPTIONS /api/audit/{proxy+}
JWT   ANY /api/mqadmin/{proxy+}    NONE  OPTIONS /api/mqadmin/{proxy+}
```

Seis con validador y seis sin el. Las segundas son la verificacion previa del
navegador, que viaja sin credencial y por eso no puede exigir token.

### Los contenedores corriendo en la nube

```
ec2-apps    54.224.223.31          ec2-broker  3.94.98.145
  apps-frontend-1                    mq-rabbit1-1    (healthy)
  apps-ms-orders-1                   mq-rabbit2-1    (healthy)
  apps-ms-catalog-1                  kafka-kafka-1
  apps-ms-notify-1                   kafka-zookeeper-1
  apps-ms-report-1
  apps-ms-audit-1
  apps-ms-mqadmin-1
```

Once contenedores en dos instancias, todos levantados con docker compose.

---

## El inquilino de IDaaS, capturado

Cuatro capturas del portal, en `entregables/azure/`, tomadas contra el
inquilino real. Su `LEEME.md` explica que prueba cada una.

| Captura | Que prueba |
|---|---|
| El inquilino | `actividadcloudnative`, Tenant ID `2d0922c9-...`, 4 usuarios, 3 aplicaciones |
| Authentication | Plataforma **Single-page application** y el redirect URI del frontend desplegado |
| Enterprise applications | `Pedidos360`, Application ID `d0120ca1-...`, Identifier URI `api://d0120ca1-...` |
| Users | Cuatro usuarios: una Member y tres Guest, una por invitacion externa |

**Lo que ata el IDaaS con el API Manager** no son las capturas, son los
identificadores, que coinciden:

| En Azure | En el API Gateway |
|---|---|
| Tenant ID `2d0922c9-...` | Emisor `.../2d0922c9-.../v2.0` |
| Application ID `d0120ca1-...` | Audiencia `d0120ca1-...` |
| Identifier URI `api://d0120ca1-...` | Audiencia `api://d0120ca1-...` |

El validador acepta exactamente el inquilino y la aplicacion registrados en el
portal. Nada mas.

Se rehacen con `node montaje/azure-capturas.mjs`.

> **Un cabo suelto que encontramos mirando esto:** quedo registrado el redirect
> URI `https://3.83.237.65:8085/auth/callback`, de una sesion anterior del
> laboratorio. Esa IP ya no es nuestra y AWS puede reasignarla, asi que conviene
> borrarla. Cada redirect URI registrado es un destino al que el proveedor esta
> dispuesto a mandar un codigo de autorizacion.

---

## Comprobarlo todo de una vez

```bash
./infra/aws/verificar.sh ~/Downloads/labsuser.pem
```

Trece comprobaciones: el frontend, los seis health, 401 sin token, 401 con token
invalido, el preflight en 200, los dos nodos del cluster, las seis colas y sus
politicas de retencion. El 20 de octubre pasaron las trece.

## Si la sesion del laboratorio expira

Las direcciones publicas cambian y hay que rehacer tres pasos:

```bash
./infra/aws/provisionar.sh        # reutiliza lo que exista y arranca lo detenido
./infra/aws/actualizar-gateway.sh # reapunta las integraciones y el CORS
./infra/aws/desplegar.sh          # copia el codigo y levanta los contenedores
```

Despues, el redirect URI nuevo en Azure, y para regrabar:

```bash
cd evidencia
DESTINO=aws npm run sesion        # el login lo hace la persona, una vez
./rehacer-todo.sh aws
```

`destino.sh` lee la IP de `infra/aws/entorno-actual.env`, asi que no hay ninguna
direccion escrita a mano.

> **Antes de regrabar, respaldar.** El ensamblador sobrescribe
> `entregables/pedidos360-demostracion.mp4`, y Playwright vacia `videos/` en
> cada corrida: grabar un spec despues de otro se lleva por delante lo anterior.
> Los specs de la app y el del tramo en paralelo van en la misma corrida.
