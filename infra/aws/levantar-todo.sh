#!/usr/bin/env bash
#
# Levanta Pedidos360 en AWS desde cero, en el orden correcto.
#
#   ./infra/aws/levantar-todo.sh [ruta-al-pem]
#
# Usarlo cuando no queda nada en AWS. Si la infraestructura existe y solo
# expiro la sesion del lab, basta con:
#
#   ./provisionar.sh && ./actualizar-gateway.sh && ./verificar.sh
#
# El orden no es arbitrario: el frontend compila el Invoke URL del Gateway
# dentro del bundle, asi que el Gateway tiene que existir antes de construir y
# desplegar el frontend.

cd "$(dirname "$0")"
source ./comun.sh

PEM="${1:-$HOME/Downloads/labsuser.pem}"
RAIZ="$(cd ../.. && pwd)"
ENV_PROD="$RAIZ/frontend-pedidos360/src/environments/environment.prod.ts"

azul "== Pedidos360: levantar toda la infraestructura =="
echo
verificar_credenciales
info "esto crea recursos reales y consume credito del lab"
echo

azul "[1/6] Base de datos"
./crear-rds.sh

echo
azul "[2/6] Security groups e instancias"
./provisionar.sh
source ./entorno-actual.env

echo
azul "[3/6] API Gateway"
./crear-gateway.sh

INVOKE_URL="$(aws apigatewayv2 get-apis --region "$REGION" \
  --query "Items[?Name=='${NOMBRE_API:-pedidos360-api}'].ApiEndpoint | [0]" --output text)"

echo
azul "[4/6] Apuntar el frontend al Gateway nuevo"
if [ -n "$INVOKE_URL" ] && [ "$INVOKE_URL" != "None" ]; then
  # El Invoke URL cambia si se recrea la API, y va compilado dentro del bundle.
  sed -i.bak -E "s#^const API_GATEWAY_URL = '.*';#const API_GATEWAY_URL = '$INVOKE_URL';#" "$ENV_PROD"
  rm -f "${ENV_PROD}.bak"
  ok "environment.prod.ts -> $INVOKE_URL"
else
  fatal "No pude resolver el Invoke URL del Gateway."
fi

echo
azul "[5/6] Desplegar broker, microservicios y frontend"
./desplegar.sh "$PEM"

echo
azul "[6/6] Verificacion"
echo
aviso "Antes de verificar, hay un paso manual que no se puede automatizar:"
cat <<MANUAL

    Azure AD -> App registrations -> Pedidos360 -> Authentication
    Single-page application, reemplazar el redirect URI por:

        https://${APPS_IP_PUBLICA}:8085/auth/callback

MANUAL
read -r -p "  Cuando lo hayas guardado, Enter para verificar (o Ctrl-C para salir): " _
echo
./verificar.sh "$PEM"

echo
azul "== Listo =="
cat <<FIN

  Frontend      https://${APPS_IP_PUBLICA}:8085
  API Gateway   $INVOKE_URL

Recorda commitear el cambio de environment.prod.ts, que ahora apunta al
Gateway nuevo.

FIN
