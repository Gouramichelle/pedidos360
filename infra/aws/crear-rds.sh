#!/usr/bin/env bash
#
# Crea la base de datos PostgreSQL en RDS.
#
#   ./infra/aws/crear-rds.sh
#
# Solo hace falta cuando se perdio la base. RDS sobrevive a la expiracion de la
# sesion del lab (se detiene, no se borra), asi que normalmente esto se corre
# una sola vez.
#
# La contrasena se genera sola y se escribe en infra/apps/.env, que no se
# versiona. No se imprime en pantalla.

cd "$(dirname "$0")"
source ./comun.sh

ID_DB="${ID_DB:-pedidos360-db}"
NOMBRE_DB="${NOMBRE_DB:-pedidos360}"
USUARIO_DB="${USUARIO_DB:-pedidos360}"
CLASE_DB="${CLASE_DB:-db.t3.micro}"
ENV_LOCAL="$(cd ../.. && pwd)/infra/apps/.env"

azul "== Pedidos360: crear la base de datos en RDS =="
echo
verificar_credenciales

ID_RDS="$(id_sg seg-rds)"
if [ -z "$ID_RDS" ]; then
  VPC_ID="$(aws ec2 describe-vpcs --region "$REGION" \
    --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)"
  ID_RDS="$(aws ec2 create-security-group --region "$REGION" \
    --group-name seg-rds --description 'PostgreSQL de Pedidos360' \
    --vpc-id "$VPC_ID" --query 'GroupId' --output text)"
  ok "seg-rds creado ($ID_RDS)"
else
  ok "seg-rds ya existe ($ID_RDS)"
fi

# La regla de entrada desde seg-apps la agrega provisionar.sh, que es quien
# conoce el id de ese grupo. Aqui solo se garantiza que el grupo exista.
ID_APPS="$(id_sg seg-apps)"
if [ -n "$ID_APPS" ]; then
  aws ec2 authorize-security-group-ingress --region "$REGION" \
    --group-id "$ID_RDS" --protocol tcp --port 5432 --source-group "$ID_APPS" >/dev/null 2>&1 || true
  ok "5432 abierto desde seg-apps"
else
  aviso "seg-apps todavia no existe; corre provisionar.sh despues para abrir el 5432"
fi

ESTADO="$(aws rds describe-db-instances --region "$REGION" \
  --db-instance-identifier "$ID_DB" \
  --query 'DBInstances[0].DBInstanceStatus' --output text 2>/dev/null || true)"

if [ -n "$ESTADO" ] && [ "$ESTADO" != "None" ]; then
  ok "la base $ID_DB ya existe (estado: $ESTADO)"
  if [ "$ESTADO" = "stopped" ]; then
    aws rds start-db-instance --region "$REGION" --db-instance-identifier "$ID_DB" >/dev/null
    info "estaba detenida, arrancando..."
  fi
  CLAVE=""
else
  # 25 caracteres alfanumericos: RDS rechaza varios simbolos en la contrasena
  # maestra, y los que acepta complican el .env de Docker Compose.
  CLAVE="$(LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 25)"

  info "creando la base (tarda entre 5 y 10 minutos)..."
  aws rds create-db-instance --region "$REGION" \
    --db-instance-identifier "$ID_DB" \
    --db-instance-class "$CLASE_DB" \
    --engine postgres \
    --db-name "$NOMBRE_DB" \
    --master-username "$USUARIO_DB" \
    --master-user-password "$CLAVE" \
    --allocated-storage 20 \
    --storage-type gp2 \
    --vpc-security-group-ids "$ID_RDS" \
    --backup-retention-period 0 \
    --no-multi-az \
    --no-publicly-accessible \
    --no-auto-minor-version-upgrade \
    >/dev/null
  ok "base $ID_DB solicitada"
fi

info "esperando a que quede disponible..."
aws rds wait db-instance-available --region "$REGION" --db-instance-identifier "$ID_DB"

ENDPOINT="$(aws rds describe-db-instances --region "$REGION" \
  --db-instance-identifier "$ID_DB" \
  --query 'DBInstances[0].Endpoint.Address' --output text)"
ok "disponible en $ENDPOINT"

# ------------------------------------------------- escribir el .env local

mkdir -p "$(dirname "$ENV_LOCAL")"
touch "$ENV_LOCAL"
TMP="$(mktemp)"
grep -vE '^(DB_HOST|DB_PORT|DB_NAME|DB_USER|DB_PASSWORD|DDL_AUTO)=' "$ENV_LOCAL" > "$TMP" || true
{
  echo "DB_HOST=$ENDPOINT"
  echo "DB_PORT=5432"
  echo "DB_NAME=$NOMBRE_DB"
  echo "DB_USER=$USUARIO_DB"
  if [ -n "$CLAVE" ]; then
    echo "DB_PASSWORD=$CLAVE"
  else
    grep '^DB_PASSWORD=' "$ENV_LOCAL" 2>/dev/null || echo "DB_PASSWORD=CAMBIAR-no-se-pudo-recuperar"
  fi
  echo "DDL_AUTO=update"
} >> "$TMP"
mv "$TMP" "$ENV_LOCAL"
chmod 600 "$ENV_LOCAL"

echo
azul "== Base de datos lista =="
cat <<RESUMEN

  Endpoint  $ENDPOINT
  Base      $NOMBRE_DB
  Usuario   $USUARIO_DB

Las credenciales quedaron en infra/apps/.env (permisos 600, fuera de git).
La contrasena no se imprime a proposito: leela de ese archivo si la necesitas.

Los cuatro esquemas (orders, catalog, report, audit) los crea Hibernate solo
al arrancar los microservicios.

RESUMEN
