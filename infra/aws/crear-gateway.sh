#!/usr/bin/env bash
#
# Crea el API Gateway completo: HTTP API, authorizer JWT, integraciones,
# rutas y CORS.
#
#   ./infra/aws/crear-gateway.sh
#   RECREAR=1 ./infra/aws/crear-gateway.sh    # borra el existente y lo rehace
#
# Normalmente se corre una sola vez: el API Gateway es serverless y sobrevive a
# la expiracion de la sesion del lab. Si solo cambio la IP de ec2-apps, lo que
# hay que correr es actualizar-gateway.sh, no esto.
#
# Deja montadas doce rutas sobre seis integraciones. Por cada servicio hay dos:
#
#   ANY     ... con authorizer JWT     el trafico real, que exige token
#   OPTIONS ... sin authorizer         el preflight de CORS
#
# Esa separacion no es un adorno. Las rutas ANY incluyen el metodo OPTIONS, y
# como llevan el authorizer adjunto, el preflight (que el navegador manda SIN
# header Authorization) respondia 401 antes de poder devolver los headers de
# CORS. La ruta OPTIONS explicita gana por especificidad de metodo y resuelve
# el preflight sin token.

cd "$(dirname "$0")"
source ./comun.sh

NOMBRE_API="${NOMBRE_API:-pedidos360-api}"
TENANT="${AZURE_TENANT_ID:-2d0922c9-f3ff-4709-b9b7-0c4b54529639}"
CLIENTE="${AZURE_CLIENT_ID:-d0120ca1-1d51-493b-9eaa-1121b5b7303a}"
ISSUER="https://login.microsoftonline.com/${TENANT}/v2.0"

azul "== Pedidos360: crear el API Gateway =="
echo
verificar_credenciales

[ -f ./entorno-actual.env ] || fatal "Falta entorno-actual.env. Corre antes ./provisionar.sh"
source ./entorno-actual.env
info "backend en $APPS_IP_PUBLICA"

EXISTE="$(aws apigatewayv2 get-apis --region "$REGION" \
  --query "Items[?Name=='$NOMBRE_API'].ApiId | [0]" --output text)"
if [ -n "$EXISTE" ] && [ "$EXISTE" != "None" ]; then
  if [ "${RECREAR:-0}" = "1" ]; then
    aws apigatewayv2 delete-api --region "$REGION" --api-id "$EXISTE" >/dev/null
    ok "API anterior borrada ($EXISTE)"
  else
    fatal "Ya existe una API '$NOMBRE_API' ($EXISTE).
       Si solo cambio la IP del backend:   ./actualizar-gateway.sh
       Si de verdad quieres rehacerla:     RECREAR=1 ./crear-gateway.sh
       Ojo: rehacerla cambia el Invoke URL y obliga a recompilar el frontend."
  fi
fi

# ------------------------------------------------------------------- API

ORIGEN="https://${APPS_IP_PUBLICA}:8085"
API_ID="$(aws apigatewayv2 create-api --region "$REGION" \
  --name "$NOMBRE_API" --protocol-type HTTP \
  --cors-configuration "AllowOrigins=$ORIGEN,http://localhost:4200,AllowMethods=GET,POST,PUT,PATCH,DELETE,OPTIONS,AllowHeaders=authorization,content-type,MaxAge=3600" \
  --query 'ApiId' --output text)"
ok "API creada ($API_ID)"

# --------------------------------------------------------- authorizer JWT

# Dos audiencias a proposito: Azure AD emite tokens v1 con aud "api://<id>" y
# tokens v2 con el GUID pelado. Aceptar ambas evita 401 silenciosos si cambia
# requestedAccessTokenVersion en el manifiesto.
AUTH_ID="$(aws apigatewayv2 create-authorizer --region "$REGION" \
  --api-id "$API_ID" --name pedidos360-jwt --authorizer-type JWT \
  --identity-source '$request.header.Authorization' \
  --jwt-configuration "{\"Audience\":[\"api://${CLIENTE}\",\"${CLIENTE}\"],\"Issuer\":\"${ISSUER}\"}" \
  --query 'AuthorizerId' --output text)"
ok "authorizer JWT creado ($AUTH_ID)"
info "issuer: $ISSUER"

# --------------------------------------------- integraciones y rutas

crear_flujo() {  # nombre, uri destino, route-key
  local nombre="$1" uri="$2" clave="$3" int_id
  int_id="$(aws apigatewayv2 create-integration --region "$REGION" \
    --api-id "$API_ID" --integration-type HTTP_PROXY \
    --integration-method ANY --payload-format-version 1.0 \
    --integration-uri "$uri" --query 'IntegrationId' --output text)"

  # Trafico real: exige un token valido.
  aws apigatewayv2 create-route --region "$REGION" --api-id "$API_ID" \
    --route-key "ANY $clave" --target "integrations/$int_id" \
    --authorization-type JWT --authorizer-id "$AUTH_ID" >/dev/null

  # Preflight: misma integracion, sin authorizer.
  aws apigatewayv2 create-route --region "$REGION" --api-id "$API_ID" \
    --route-key "OPTIONS $clave" --target "integrations/$int_id" \
    --authorization-type NONE >/dev/null

  ok "$nombre  ->  $uri"
}

echo
azul "-- Integraciones y rutas --"

# orders necesita dos: {proxy+} exige al menos un segmento, asi que el listado
# y la creacion en /api/orders a secas daban 404 sin la ruta exacta.
crear_flujo "orders (listado y creacion)" "http://${APPS_IP_PUBLICA}:8080/api/orders"                    "/api/orders"
crear_flujo "orders (detalle y estado)"   "http://${APPS_IP_PUBLICA}:8080/api/orders/{proxy}"            "/api/orders/{proxy+}"
crear_flujo "catalog"                     "http://${APPS_IP_PUBLICA}:8081/api/catalog/{proxy}"           "/api/catalog/{proxy+}"
crear_flujo "report"                      "http://${APPS_IP_PUBLICA}:8083/api/report/{proxy}"            "/api/report/{proxy+}"
crear_flujo "audit"                       "http://${APPS_IP_PUBLICA}:8084/api/audit/{proxy}"             "/api/audit/{proxy+}"
crear_flujo "mqadmin"                     "http://${APPS_IP_PUBLICA}:8086/api/mqadmin/{proxy}"           "/api/mqadmin/{proxy+}"

# ------------------------------------------------------------------ stage

aws apigatewayv2 create-stage --region "$REGION" --api-id "$API_ID" \
  --stage-name '$default' --auto-deploy >/dev/null
ok "stage \$default con despliegue automatico"

INVOKE_URL="$(aws apigatewayv2 get-api --region "$REGION" --api-id "$API_ID" \
  --query 'ApiEndpoint' --output text)"

echo
azul "== API Gateway listo =="
cat <<RESUMEN

  Invoke URL   $INVOKE_URL
  Rutas        12 (6 ANY con token + 6 OPTIONS para el preflight)
  CORS         $ORIGEN

Este Invoke URL es nuevo, asi que hay dos cosas que actualizar:

  1. frontend-pedidos360/src/environments/environment.prod.ts
     const API_GATEWAY_URL = '$INVOKE_URL';
     y volver a desplegar el frontend.

  2. Azure AD -> App registrations -> Pedidos360 -> Authentication
     redirect URI: https://${APPS_IP_PUBLICA}:8085/auth/callback

Despues: ./infra/aws/verificar.sh

RESUMEN
