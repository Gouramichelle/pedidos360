#!/usr/bin/env bash
# Variables y utilidades compartidas por los scripts de infraestructura.
# No se ejecuta solo: los demas scripts hacen `source comun.sh`.

set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
KEY_PAIR="${KEY_PAIR:-vockey}"

# El Learner Lab restringe los tipos de instancia. t3.medium es el mayor que
# nos ha funcionado; si lo rechaza, bajar a t3.small y recortar los heaps.
TIPO_APPS="${TIPO_APPS:-t3.medium}"
TIPO_BROKER="${TIPO_BROKER:-t3.medium}"

SG_SSH="seg-ssh"
SG_APPS="seg-apps"
SG_BROKER="seg-broker"

EC2_APPS="ec2-apps"
EC2_BROKER="ec2-broker"

# Todo el progreso va a stderr, no a stdout. Es deliberado: varias funciones
# devuelven un id por stdout para capturarlo con $(...), y si los mensajes
# salieran por ahi se mezclarian con el valor devuelto.
azul()  { printf '\033[34m%s\033[0m\n' "$*" >&2; }
ok()    { printf '  \033[32mOK\033[0m   %s\n' "$*" >&2; }
info()  { printf '       %s\n' "$*" >&2; }
aviso() { printf '  \033[33m!\033[0m    %s\n' "$*" >&2; }
fatal() { printf '  \033[31mERROR\033[0m %s\n' "$*" >&2; exit 1; }

# Falla temprano y con un mensaje util si la sesion del lab expiro.
verificar_credenciales() {
  if ! aws sts get-caller-identity --region "$REGION" >/dev/null 2>&1; then
    fatal "Credenciales de AWS invalidas o vencidas.
       En el Learner Lab: AWS Details -> AWS CLI -> Show, y pegar el bloque
       en ~/.aws/credentials. Caducan al terminar la sesion."
  fi
}

# Devuelve el id de un security group por nombre, o vacio si no existe.
id_sg() {
  aws ec2 describe-security-groups --region "$REGION" \
    --filters "Name=group-name,Values=$1" \
    --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null | grep -v '^None$' || true
}

# Devuelve el id de una instancia por tag Name, solo si esta viva.
id_instancia() {
  aws ec2 describe-instances --region "$REGION" \
    --filters "Name=tag:Name,Values=$1" \
              "Name=instance-state-name,Values=pending,running,stopping,stopped" \
    --query 'Reservations[0].Instances[0].InstanceId' --output text 2>/dev/null | grep -v '^None$' || true
}

ip_publica() {
  aws ec2 describe-instances --region "$REGION" --instance-ids "$1" \
    --query 'Reservations[0].Instances[0].PublicIpAddress' --output text 2>/dev/null | grep -v '^None$' || true
}

ip_privada() {
  aws ec2 describe-instances --region "$REGION" --instance-ids "$1" \
    --query 'Reservations[0].Instances[0].PrivateIpAddress' --output text 2>/dev/null | grep -v '^None$' || true
}

estado_instancia() {
  aws ec2 describe-instances --region "$REGION" --instance-ids "$1" \
    --query 'Reservations[0].Instances[0].State.Name' --output text 2>/dev/null || echo "desconocido"
}
