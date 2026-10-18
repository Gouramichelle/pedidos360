#!/usr/bin/env bash
#
# Reapunta el API Gateway a la IP publica nueva de ec2-apps.
#
#   ./infra/aws/actualizar-gateway.sh
#
# Es el paso que mas se repite: cada vez que expira la sesion del lab, ec2-apps
# cambia de IP publica y las integraciones del Gateway quedan apuntando a una
# direccion muerta. Este script reescribe la IP de cada integracion y el origen
# permitido de CORS, sin tocar rutas ni authorizer.
#
# El Invoke URL del Gateway NO cambia, asi que el frontend no se recompila.

cd "$(dirname "$0")"
source ./comun.sh

NOMBRE_API="${NOMBRE_API:-pedidos360-api}"

azul "== Pedidos360: reapuntar el API Gateway =="
echo
verificar_credenciales

[ -f ./entorno-actual.env ] || fatal "Falta entorno-actual.env. Corre antes ./provisionar.sh"
source ./entorno-actual.env

API_ID="$(aws apigatewayv2 get-apis --region "$REGION" \
  --query "Items[?Name=='$NOMBRE_API'].ApiId | [0]" --output text)"
[ "$API_ID" != "None" ] && [ -n "$API_ID" ] \
  || fatal "No existe una API llamada '$NOMBRE_API' en $REGION."
ok "API $NOMBRE_API ($API_ID)"

INVOKE_URL="$(aws apigatewayv2 get-apis --region "$REGION" \
  --query "Items[?ApiId=='$API_ID'].ApiEndpoint | [0]" --output text)"
info "Invoke URL: $INVOKE_URL"

# ------------------------------------------------------------ integraciones

echo
azul "-- Integraciones --"

# Se reescribe solo el host de cada URI y se conserva puerto y ruta, asi el
# script sirve igual aunque cambien las rutas del backend.
aws apigatewayv2 get-integrations --region "$REGION" --api-id "$API_ID" \
  --query 'Items[].[IntegrationId,IntegrationUri]' --output text \
| while read -r INT_ID URI; do
    [ -n "${URI:-}" ] || continue
    NUEVA="$(printf '%s' "$URI" | sed -E "s#//[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+:#//${APPS_IP_PUBLICA}:#")"
    if [ "$NUEVA" = "$URI" ]; then
      info "$INT_ID sin IP que reescribir: $URI"
    else
      aws apigatewayv2 update-integration --region "$REGION" \
        --api-id "$API_ID" --integration-id "$INT_ID" --integration-uri "$NUEVA" >/dev/null
      ok "$INT_ID -> $NUEVA"
    fi
  done

# -------------------------------------------------------------------- CORS

echo
azul "-- CORS --"

ORIGEN="https://${APPS_IP_PUBLICA}:8085"
aws apigatewayv2 update-api --region "$REGION" --api-id "$API_ID" \
  --cors-configuration \
    "AllowOrigins=$ORIGEN,http://localhost:4200,AllowMethods=GET,POST,PUT,PATCH,DELETE,OPTIONS,AllowHeaders=authorization,content-type,MaxAge=3600" \
  >/dev/null
ok "origen permitido: $ORIGEN"

echo
azul "== Gateway actualizado =="
cat <<RESUMEN

Queda un paso manual, que es el unico que no se puede automatizar desde aca:

  Azure AD -> App registrations -> Pedidos360 -> Authentication
  Reemplazar el redirect URI por:

      https://${APPS_IP_PUBLICA}:8085/auth/callback

Despues: ./infra/aws/verificar.sh

RESUMEN
