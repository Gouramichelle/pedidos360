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
| **[El video](entregables/pedidos360-demostracion.mp4)**, 9:56 | La aplicacion funcionando y **toda** la mensajeria: colas, exchanges, retencion, confirmacion manual, los dos caminos a la cola muerta y la caida de un consumidor de punta a punta |
| **El deck**, `presentacion/Pedidos360-Presentacion.pptx`, 43 diapositivas | El inquilino de Azure y sus usuarios, la red y los servidores, el API Manager con sus rutas y su CORS, y desde ahora la seccion de mensajeria (29 a 36) |
| **[El guion](GUION.md)** | El texto de la narracion con la ruta en vivo de cada tramo, para responder en el momento |

---

## El hueco, dicho sin rodeos

Todo lo que es **mensajeria** —que es donde estan los ocho indicadores y el 100%
de la nota tecnica— esta cubierto por el video y reproducible en vivo en local.

Lo que **no** puede demostrarse ahora mismo es lo que exige la nube levantada:

| Punto de la lista de demo | Estado |
|---|---|
| La instancia de API Manager y su configuracion | Deck 25 a 27. **El Gateway sigue vivo**: responde 401 sin token |
| El API Manager validando el JWT | Comprobable ahora: ver abajo |
| El frontend consumiendo a traves del API Manager | **No demostrable**: las EC2 estan caidas, y el video muestra el frontend local llamando directo |
| El inquilino de IDaaS y sus usuarios | Deck 6 a 8. En vivo necesita la consola de Azure |
| El despliegue en la nube y el docker-compose corriendo alla | **No demostrable**: las EC2 estan caidas |

### Lo que si se puede comprobar ahora, sin credenciales

El API Gateway no se apaga con la sesion del laboratorio, asi que esto responde
de verdad:

```bash
G=https://0b7fhgzl22.execute-api.us-east-1.amazonaws.com

curl -s -o /dev/null -w "sin token:      %{http_code}\n" $G/api/orders
curl -s -o /dev/null -H "Authorization: Bearer token-falso" \
     -w "token inventado: %{http_code}\n" $G/api/orders

curl -s -i -X OPTIONS -H "Origin: https://3.83.237.65:8085" \
     -H "Access-Control-Request-Method: GET" $G/api/orders | grep -i "^access-control"
```

Devuelve **401** en los dos casos, con la cabecera `www-authenticate: Bearer` y
un `apigw-requestid`. Esas dos cabeceras son la prueba de que quien rechaza es
**el validador del Gateway**, no el backend: el backend ni se entera, y de hecho
ahora mismo esta apagado.

El preflight devuelve el origen unico del frontend, las dos cabeceras que la
aplicacion envia, y **cinco** metodos sin `OPTIONS`. Es minimo privilegio: esa
cabecera declara los metodos de la peticion real, y la verificacion previa pasa
por su propia ruta sin validador.

### Para cerrar el resto

Hace falta reiniciar el Learner Lab. Con credenciales frescas:

```bash
./infra/aws/provisionar.sh          # levanta red, RDS, las dos EC2 y el Gateway
./infra/aws/desplegar.sh            # construye y levanta los contenedores
./infra/aws/verificar.sh ~/Downloads/labsuser.pem
```

Y para que el video deje de mostrar el frontend local llamando directo:

```bash
# 1. El tunel SSH al broker, que rehacer-todo.sh comprueba antes de grabar
ssh -i ~/Downloads/labsuser.pem -L 15672:localhost:15672 \
    -L 15673:localhost:15673 ec2-user@<ip-broker>

# 2. La sesion de Azure contra el despliegue nuevo
cd evidencia && DESTINO=aws npm run sesion

# 3. Regrabar
./rehacer-todo.sh aws
```

Ninguno de los tres pasos lleva una direccion escrita a mano: `destino.sh` lee
`APPS_IP_PUBLICA` de `infra/aws/entorno-actual.env`, que es lo que deja
`provisionar.sh`, y la exporta como `URL_APP` para los specs.

Esto regraba los clips contra el despliegue, con el frontend pasando por el API
Manager, y vuelve a armar el video y el guion con los tiempos nuevos. La
topologia de mensajeria es identica en local y en la nube, asi que lo que ya
esta verificado no cambia de sentido.
