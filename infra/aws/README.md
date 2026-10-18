# Infraestructura en AWS

Scripts para levantar Pedidos360 en el Learner Lab sin pasar por la consola.
Existen porque la sesion del lab caduca cada 4 horas y, al volver, las IPs
publicas cambian: hacer eso a mano son veinte minutos de clics y es donde mas
facil se cuela un error.

## Topologia

Dos instancias. RabbitMQ y Kafka conviven en una porque los dos nodos del
cluster mas Kafka entran holgados en 4 GB, y cada instancia extra es una IP
privada mas que verificar en cada reinicio.

| Instancia | Tipo | Que corre | Puertos |
|---|---|---|---|
| `ec2-apps` | `t3.medium` | 6 microservicios + nginx | 8080-8086 |
| `ec2-broker` | `t3.medium` | Cluster RabbitMQ (2 nodos) + Kafka + Zookeeper | 5672/5673, 15672/15673, 9092 |

RDS y el API Gateway **no** los tocan estos scripts: sobreviven a la expiracion
de la sesion y se configuran una sola vez.

| Security group | Reglas de entrada |
|---|---|
| `seg-ssh` | 22 desde tu IP publica |
| `seg-apps` | 8080-8086 desde internet |
| `seg-broker` | 5672, 5673, 15672, 15673 y 9092 **solo desde `seg-apps`** |
| `seg-rds` | 5432 desde `seg-apps` |

Zookeeper no necesita regla: Kafka lo alcanza por la red interna de Docker.

## Antes de empezar

1. **Start Lab** en Canvas y esperar el punto verde.
2. **AWS Details → AWS CLI → Show**, copiar el bloque y pegarlo en
   `~/.aws/credentials`. Caduca al terminar la sesion.
3. Descargar `labsuser.pem` y dejarlo en `~/Downloads/`.
4. Tener `infra/apps/.env` local con las credenciales de RDS y Azure. No se
   versiona: es la fuente de los secretos y los scripts solo le reescriben las
   IPs.

## Despliegue completo

```bash
./infra/aws/provisionar.sh          # security groups e instancias
./infra/aws/desplegar.sh            # codigo, broker y los 6 servicios
./infra/aws/actualizar-gateway.sh   # reapunta las integraciones y el CORS
./infra/aws/verificar.sh            # smoke test de todo
```

Y el unico paso que queda a mano, porque es de Azure y no de AWS:

> **App registrations → Pedidos360 → Authentication**, reemplazar el redirect
> URI por `https://<ip-publica-de-ec2-apps>:8085/auth/callback`

## Al reiniciar la sesion del lab

Las instancias quedan detenidas y cambian de IP publica. No hay que rehacer
nada: `provisionar.sh` detecta que ya existen y solo las arranca.

```bash
./infra/aws/provisionar.sh          # las arranca y reescribe entorno-actual.env
./infra/aws/actualizar-gateway.sh   # reapunta a la IP nueva
./infra/aws/verificar.sh
```

Los contenedores levantan solos (`restart: unless-stopped`), asi que no hace
falta volver a desplegar. Lo unico que sigue siendo manual es el redirect URI
de Azure.

Si la IP privada de `ec2-broker` cambiara (poco frecuente), ahi si hay que
correr `desplegar.sh` de nuevo para que las apps apunten al broker correcto.

## Consolas de RabbitMQ

`seg-broker` solo acepta trafico desde `seg-apps`, asi que las consolas no son
alcanzables desde tu navegador. Se abren por tunel SSH:

```bash
ssh -i ~/Downloads/labsuser.pem \
    -L 15672:localhost:15672 -L 15673:localhost:15673 \
    ec2-user@<ip-publica-de-ec2-broker>
```

Despues, `http://localhost:15672` es el nodo 1 y `http://localhost:15673` el
nodo 2, con `guest` / `guest`. En **Overview → Nodes** se ven los dos nodos del
cluster.

## Logs en CloudWatch

Opcional, para leer los logs de los consumidores desde la consola de AWS en vez
de por SSH. Requiere asignarle el instance profile `LabInstanceProfile` a
`ec2-apps` (**EC2 → Actions → Security → Modify IAM role**):

```bash
cd ~/infra/apps
sudo docker compose -f docker-compose.yml -f docker-compose.cloudwatch.yml up -d
```

Va aparte del compose principal a proposito: el driver `awslogs` impide que el
contenedor arranque si la instancia no tiene permisos sobre CloudWatch.

## Que hace cada script

| Script | Para que |
|---|---|
| `comun.sh` | Variables y utilidades. No se ejecuta solo |
| `provisionar.sh` | Crea o arranca security groups e instancias. Idempotente |
| `user-data.sh` | Instala Docker, Compose y buildx al primer arranque |
| `desplegar.sh` | Copia el codigo, levanta el broker y los 6 servicios |
| `actualizar-gateway.sh` | Reescribe la IP de las integraciones y el origen de CORS |
| `verificar.sh` | Smoke test: health, 401 sin token, preflight, cluster y colas |
