#!/usr/bin/env bash
#
# Crea la infraestructura de EC2 de Pedidos360 en el Learner Lab.
#
#   ./infra/aws/provisionar.sh
#
# Es idempotente: si un security group o una instancia ya existen los reutiliza,
# y si una instancia esta detenida la arranca. Se puede correr las veces que
# haga falta sin duplicar nada.
#
# NO toca RDS ni el API Gateway: esos sobreviven a la expiracion de la sesion
# del lab y se configuran aparte.
#
# Topologia que deja montada:
#   ec2-apps    los 6 microservicios + nginx   puertos 8080-8086
#   ec2-broker  cluster RabbitMQ + Kafka       puertos 5672/5673, 15672/15673, 9092

cd "$(dirname "$0")"
source ./comun.sh

azul "== Pedidos360: provisionar infraestructura EC2 =="
echo
verificar_credenciales
ok "credenciales validas en $REGION"

MI_IP="$(curl -s --max-time 10 https://checkip.amazonaws.com || true)"
[ -n "$MI_IP" ] || fatal "No pude averiguar tu IP publica para la regla de SSH."
info "tu IP publica: $MI_IP"

# --------------------------------------------------------- security groups

VPC_ID="$(aws ec2 describe-vpcs --region "$REGION" \
  --filters Name=isDefault,Values=true --query 'Vpcs[0].VpcId' --output text)"
[ "$VPC_ID" != "None" ] || fatal "No hay VPC por defecto en $REGION."
info "VPC por defecto: $VPC_ID"
echo
azul "-- Security groups --"

crear_sg() {
  local nombre="$1" descripcion="$2" id
  id="$(id_sg "$nombre")"
  if [ -n "$id" ]; then
    ok "$nombre ya existe ($id)"
  else
    id="$(aws ec2 create-security-group --region "$REGION" \
      --group-name "$nombre" --description "$descripcion" --vpc-id "$VPC_ID" \
      --query 'GroupId' --output text)"
    ok "$nombre creado ($id)"
  fi
  echo "$id"
}

# Las reglas duplicadas devuelven InvalidPermission.Duplicate: se ignora para
# que el script se pueda volver a correr sin ruido.
regla() {
  aws ec2 authorize-security-group-ingress --region "$REGION" "$@" >/dev/null 2>&1 \
    || true
}

ID_SSH="$(crear_sg "$SG_SSH" 'Acceso SSH desde la IP del operador')"
ID_APPS="$(crear_sg "$SG_APPS" 'Microservicios y frontend de Pedidos360')"
ID_BROKER="$(crear_sg "$SG_BROKER" 'RabbitMQ y Kafka de Pedidos360')"

# SSH solo desde la IP actual. Si tu IP cambio, borra la regla vieja a mano.
regla --group-id "$ID_SSH" --protocol tcp --port 22 --cidr "${MI_IP}/32"
ok "$SG_SSH: 22 desde ${MI_IP}/32"

# 8080-8086: los seis microservicios mas el frontend en 8085.
regla --group-id "$ID_APPS" --protocol tcp --port 8080-8086 --cidr 0.0.0.0/0
ok "$SG_APPS: 8080-8086 desde internet"

# El broker solo acepta trafico desde las apps, nunca desde internet.
# Zookeeper no necesita regla: Kafka lo alcanza por la red interna de Docker.
for p in 5672 5673 15672 15673 9092; do
  regla --group-id "$ID_BROKER" --protocol tcp --port "$p" --source-group "$ID_APPS"
done
ok "$SG_BROKER: 5672, 5673, 15672, 15673 y 9092 desde $SG_APPS"

ID_RDS="$(id_sg seg-rds)"
if [ -n "$ID_RDS" ]; then
  regla --group-id "$ID_RDS" --protocol tcp --port 5432 --source-group "$ID_APPS"
  ok "seg-rds: 5432 desde $SG_APPS"
else
  aviso "seg-rds no existe. Si la base ya esta creada, agregale a mano la regla
       5432 desde $SG_APPS, o el backend no va a poder conectarse."
fi

# ---------------------------------------------------------------- instancias

echo
azul "-- Instancias --"

AMI="$(aws ssm get-parameter --region "$REGION" \
  --name /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64 \
  --query 'Parameter.Value' --output text)"
info "AMI Amazon Linux 2023: $AMI"

SUBNET="$(aws ec2 describe-subnets --region "$REGION" \
  --filters "Name=vpc-id,Values=$VPC_ID" "Name=map-public-ip-on-launch,Values=true" \
  --query 'Subnets[0].SubnetId' --output text)"
[ "$SUBNET" != "None" ] || fatal "No hay subred publica con asignacion automatica de IP."
info "subred publica: $SUBNET"

USER_DATA="$(base64 < ./user-data.sh)"

lanzar() {
  local nombre="$1" tipo="$2" sg_extra="$3" id estado
  id="$(id_instancia "$nombre")"

  if [ -n "$id" ]; then
    estado="$(estado_instancia "$id")"
    if [ "$estado" = "stopped" ]; then
      aws ec2 start-instances --region "$REGION" --instance-ids "$id" >/dev/null
      ok "$nombre estaba detenida, arrancando ($id)"
    else
      ok "$nombre ya existe y esta $estado ($id)"
    fi
  else
    id="$(aws ec2 run-instances --region "$REGION" \
      --image-id "$AMI" --instance-type "$tipo" --key-name "$KEY_PAIR" \
      --subnet-id "$SUBNET" --associate-public-ip-address \
      --security-group-ids "$ID_SSH" "$sg_extra" \
      --user-data "$USER_DATA" \
      --block-device-mappings 'DeviceName=/dev/xvda,Ebs={VolumeSize=30,VolumeType=gp3}' \
      --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$nombre}]" \
      --query 'Instances[0].InstanceId' --output text)"
    ok "$nombre creada ($id, $tipo)"
  fi
  echo "$id"
}

ID_I_APPS="$(lanzar "$EC2_APPS" "$TIPO_APPS" "$ID_APPS")"
ID_I_BROKER="$(lanzar "$EC2_BROKER" "$TIPO_BROKER" "$ID_BROKER")"

info "esperando a que las dos instancias esten corriendo..."
aws ec2 wait instance-running --region "$REGION" --instance-ids "$ID_I_APPS" "$ID_I_BROKER"

# ------------------------------------------------------------------ resumen

APPS_PUB="$(ip_publica "$ID_I_APPS")"
BROKER_PUB="$(ip_publica "$ID_I_BROKER")"
BROKER_PRIV="$(ip_privada "$ID_I_BROKER")"

echo
azul "== Infraestructura lista =="
cat <<RESUMEN

  ec2-apps     publica  $APPS_PUB
  ec2-broker   publica  $BROKER_PUB
  ec2-broker   privada  $BROKER_PRIV   <- la que va en el .env de las apps

Guardado en infra/aws/entorno-actual.env para los scripts siguientes.

Las instancias tardan 2 o 3 minutos en terminar de instalar Docker. Despues:

  ./infra/aws/desplegar.sh

RESUMEN

cat > ./entorno-actual.env <<ENV
# Generado por provisionar.sh el $(date '+%Y-%m-%d %H:%M')
APPS_IP_PUBLICA=$APPS_PUB
BROKER_IP_PUBLICA=$BROKER_PUB
BROKER_IP_PRIVADA=$BROKER_PRIV
ENV
