#!/usr/bin/env bash
#
# Despliega Pedidos360 en las instancias que creo provisionar.sh.
#
#   ./infra/aws/desplegar.sh [ruta-al-pem]
#
# Por defecto busca la llave en ~/Downloads/labsuser.pem.
#
# Que hace, en orden:
#   1. espera a que las instancias terminen de instalar Docker
#   2. copia el codigo (sin target/ ni node_modules/) a cada host
#   3. levanta en ec2-broker el cluster de RabbitMQ y Kafka
#   4. arma el .env de las apps con las IPs de esta sesion y levanta los 6 servicios
#
# Los secretos (RDS, Azure) salen de tu infra/apps/.env local, que no se versiona.
# Lo unico que este script reescribe son las IPs, que cambian en cada sesion.

cd "$(dirname "$0")"
source ./comun.sh

PEM="${1:-$HOME/Downloads/labsuser.pem}"
RAIZ="$(cd ../.. && pwd)"
ENV_LOCAL="$RAIZ/infra/apps/.env"

azul "== Pedidos360: desplegar =="
echo

[ -f ./entorno-actual.env ] || fatal "Falta entorno-actual.env. Corre antes ./provisionar.sh"
source ./entorno-actual.env
[ -f "$PEM" ] || fatal "No encuentro la llave en $PEM. Descargala del Learner Lab o pasa la ruta como argumento."
[ -f "$ENV_LOCAL" ] || fatal "Falta $ENV_LOCAL con las credenciales de RDS y Azure."
chmod 400 "$PEM"

SSH_OPTS=(-i "$PEM" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR)
en_apps()   { ssh "${SSH_OPTS[@]}" "ec2-user@$APPS_IP_PUBLICA" "$@"; }
en_broker() { ssh "${SSH_OPTS[@]}" "ec2-user@$BROKER_IP_PUBLICA" "$@"; }

esperar_docker() {
  local host="$1" nombre="$2" i=0
  info "esperando a que $nombre termine de instalar Docker..."
  until ssh "${SSH_OPTS[@]}" "ec2-user@$host" 'test -f ~/.docker-listo' 2>/dev/null; do
    i=$((i+1)); [ $i -ge 60 ] && fatal "$nombre no quedo lista despues de 5 minutos."
    sleep 5
  done
  ok "$nombre lista"
}

esperar_docker "$APPS_IP_PUBLICA" "ec2-apps"
esperar_docker "$BROKER_IP_PUBLICA" "ec2-broker"

# ------------------------------------------------------------- ec2-broker

echo
azul "-- ec2-broker: cluster RabbitMQ y Kafka --"

rsync -az -e "ssh ${SSH_OPTS[*]}" "$RAIZ/infra/mq" "$RAIZ/infra/kafka" \
  "ec2-user@$BROKER_IP_PUBLICA:~/"
ok "compose copiados"

# La cookie de Erlang se genera una vez y se conserva: regenerarla en cada
# despliegue rompe el cluster, porque los nodos ya formados no se reconocerian.
en_broker "test -f ~/mq/.env || sed 's|^RABBITMQ_ERLANG_COOKIE=.*|RABBITMQ_ERLANG_COOKIE='\$(openssl rand -hex 32)'|' ~/mq/.env.example > ~/mq/.env"
ok "cookie de Erlang lista"

# Kafka se anuncia con la IP privada: es la que marcan las apps desde la otra
# instancia. Con localhost los consumidores conectan pero no reciben nada.
en_broker "cd ~/kafka && sudo KAFKA_ADVERTISED_HOST=$BROKER_IP_PRIVADA docker compose up -d" >/dev/null
ok "Kafka y Zookeeper arriba"

en_broker "cd ~/mq && sudo docker compose up -d" >/dev/null
info "esperando a que los dos nodos de RabbitMQ se agrupen..."
en_broker "sudo docker exec mq-rabbit1-1 rabbitmqctl await_startup --timeout 180" >/dev/null
en_broker "sudo docker exec mq-rabbit2-1 rabbitmqctl await_startup --timeout 180" >/dev/null
NODOS="$(en_broker "sudo docker exec mq-rabbit1-1 rabbitmq-diagnostics -q cluster_status" \
         | grep -c '^rabbit@rabbit' || echo 0)"
[ "$NODOS" -ge 2 ] && ok "cluster formado con los dos nodos" || aviso "el cluster reporta $NODOS nodos, revisar a mano"

# --------------------------------------------------------------- ec2-apps

echo
azul "-- ec2-apps: los seis microservicios y el frontend --"

rsync -az -e "ssh ${SSH_OPTS[*]}" \
  --exclude 'target/' --exclude 'node_modules/' --exclude '.git/' --exclude 'dist/' \
  "$RAIZ/infra" "$RAIZ"/ms-pedidos360-* "$RAIZ/frontend-pedidos360" \
  "ec2-user@$APPS_IP_PUBLICA:~/"
ok "codigo copiado"

# El .env local aporta los secretos; aca solo se reescriben las IPs de la sesion.
TMP_ENV="$(mktemp)"
grep -vE '^(RABBITMQ_ADDRESSES|KAFKA_BOOTSTRAP_SERVERS|ALLOWED_ORIGINS)=' "$ENV_LOCAL" > "$TMP_ENV"
cat >> "$TMP_ENV" <<ENV

# Reescritas por desplegar.sh el $(date '+%Y-%m-%d %H:%M')
RABBITMQ_ADDRESSES=$BROKER_IP_PRIVADA:5672,$BROKER_IP_PRIVADA:5673
KAFKA_BOOTSTRAP_SERVERS=$BROKER_IP_PRIVADA:9092
# Un unico origen: el del frontend desplegado. Sin localhost, que en
# produccion permitiria que cualquier pagina local llame a esta API.
ALLOWED_ORIGINS=https://$APPS_IP_PUBLICA:8085
ENV
scp "${SSH_OPTS[@]}" -q "$TMP_ENV" "ec2-user@$APPS_IP_PUBLICA:~/infra/apps/.env"
rm -f "$TMP_ENV"
ok "variables de entorno con las IPs de esta sesion"

info "construyendo las imagenes (la primera vez tarda varios minutos)..."
en_apps "cd ~/infra/apps && sudo docker compose build" >/dev/null 2>&1 \
  || fatal "Fallo la construccion. Revisar con: ssh ... 'cd ~/infra/apps && sudo docker compose build'"
ok "imagenes construidas"

en_apps "cd ~/infra/apps && sudo docker compose up -d" >/dev/null
ok "servicios levantados"

echo
azul "== Desplegado =="
cat <<RESUMEN

  Frontend          https://$APPS_IP_PUBLICA:8085
  Consola RabbitMQ  tunel SSH, ver abajo

Falta actualizar lo que depende de la IP publica nueva:

  ./infra/aws/actualizar-gateway.sh      integraciones y CORS del API Gateway
  Azure AD -> Authentication             redirect URI https://$APPS_IP_PUBLICA:8085/auth/callback

Para abrir las consolas de RabbitMQ desde el navegador:

  ssh -i $PEM -L 15672:localhost:15672 -L 15673:localhost:15673 ec2-user@$BROKER_IP_PUBLICA

Verificar que todo responde:

  ./infra/aws/verificar.sh

RESUMEN
