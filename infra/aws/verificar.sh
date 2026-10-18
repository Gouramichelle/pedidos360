#!/usr/bin/env bash
#
# Comprueba que el despliegue quedo operativo y que la seguridad funciona.
#
#   ./infra/aws/verificar.sh [ruta-al-pem]
#
# Recorre los mismos puntos que hay que demostrar en la evaluacion, asi que
# sirve tanto de smoke test como de ensayo previo a la demo.

cd "$(dirname "$0")"
source ./comun.sh

PEM="${1:-$HOME/Downloads/labsuser.pem}"
[ -f ./entorno-actual.env ] || fatal "Falta entorno-actual.env. Corre antes ./provisionar.sh"
source ./entorno-actual.env

SSH_OPTS=(-i "$PEM" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o LogLevel=ERROR)
FALLOS=0

comprobar() {  # nombre, esperado, obtenido
  if [ "$2" = "$3" ]; then ok "$1 ($3)"; else
    printf '  \033[31mFALLA\033[0m %s: esperaba %s, obtuve %s\n' "$1" "$2" "$3"
    FALLOS=$((FALLOS+1))
  fi
}

codigo() { curl -sk -o /dev/null -w '%{http_code}' --max-time 15 "$@"; }

azul "== Verificacion del despliegue =="
echo
azul "-- Frontend y microservicios --"
comprobar "frontend sirve la SPA" 200 "$(codigo "https://$APPS_IP_PUBLICA:8085/")"
for p in 8080 8081 8082 8083 8084 8086; do
  comprobar "health del puerto $p" 200 "$(codigo "http://$APPS_IP_PUBLICA:$p/actuator/health")"
done

echo
azul "-- Seguridad: el Gateway rechaza lo que debe --"
API_ID="$(aws apigatewayv2 get-apis --region "$REGION" \
  --query "Items[?Name=='pedidos360-api'].ApiEndpoint | [0]" --output text 2>/dev/null)"
if [ -n "$API_ID" ] && [ "$API_ID" != "None" ]; then
  comprobar "sin token rechaza" 401 "$(codigo "$API_ID/api/orders")"
  comprobar "con token invalido rechaza" 401 \
    "$(codigo -H 'Authorization: Bearer token-falso' "$API_ID/api/orders")"
  PREFLIGHT="$(curl -sk -o /dev/null -w '%{http_code}' --max-time 15 -X OPTIONS \
    -H "Origin: https://$APPS_IP_PUBLICA:8085" \
    -H 'Access-Control-Request-Method: GET' \
    -H 'Access-Control-Request-Headers: authorization' \
    "$API_ID/api/orders")"
  comprobar "preflight de CORS pasa sin token" 204 "$PREFLIGHT"
else
  aviso "no pude resolver el Invoke URL del Gateway, me salto esta seccion"
fi

echo
azul "-- Cluster de RabbitMQ --"
if [ -f "$PEM" ]; then
  NODOS="$(ssh "${SSH_OPTS[@]}" "ec2-user@$BROKER_IP_PUBLICA" \
    "sudo docker exec mq-rabbit1-1 rabbitmq-diagnostics -q cluster_status 2>/dev/null | grep -c '^rabbit@rabbit'" 2>/dev/null || echo 0)"
  comprobar "nodos en el cluster" 2 "$NODOS"

  COLAS="$(ssh "${SSH_OPTS[@]}" "ec2-user@$BROKER_IP_PUBLICA" \
    "sudo docker exec mq-rabbit1-1 rabbitmqctl list_queues --quiet name 2>/dev/null | grep -c '^q\.cmd\.'" 2>/dev/null || echo 0)"
  comprobar "colas declaradas (3 comando + 3 DLQ)" 6 "$COLAS"

  TTL="$(ssh "${SSH_OPTS[@]}" "ec2-user@$BROKER_IP_PUBLICA" \
    "sudo docker exec mq-rabbit1-1 rabbitmqctl list_queues --quiet name arguments 2>/dev/null | grep -c 'x-message-ttl'" 2>/dev/null || echo 0)"
  comprobar "colas con politica de retencion" 6 "$TTL"
else
  aviso "sin la llave $PEM no puedo revisar el broker"
fi

echo
if [ "$FALLOS" -eq 0 ]; then
  azul "== Todo operativo =="
  echo
  info "Listo para el login en https://$APPS_IP_PUBLICA:8085/ (ventana de incognito)"
else
  printf '\033[31m== %s comprobaciones fallaron ==\033[0m\n' "$FALLOS"
  exit 1
fi
