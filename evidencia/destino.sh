#!/usr/bin/env bash
#
# Resuelve a que despliegue se graba, para no tener la direccion escrita en
# ningun lado. La IP de ec2-apps cambia en cada sesion del laboratorio.
#
#   . ./destino.sh          -> local  (http://localhost:4200)
#   . ./destino.sh aws      -> la IP que dejo infra/aws/provisionar.sh
#
# Deja exportadas DESTINO y URL_APP, que es lo que leen specs/entorno.ts y,
# a traves de el, los dos specs que abren la aplicacion.
#
# El destino va como argumento y no como variable de entorno a proposito:
# tras `VAR=x . script`, bash restaura VAR al terminar el source, asi que la
# forma `DESTINO=aws . ./destino.sh` dejaba DESTINO vacia.

_destino="${1:-${DESTINO:-local}}"

if [ "$_destino" = "aws" ]; then
  _env="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/infra/aws/entorno-actual.env"
  if [ ! -f "$_env" ]; then
    echo "No existe $_env. Corre antes infra/aws/provisionar.sh" >&2
    return 1 2>/dev/null || exit 1
  fi
  # shellcheck disable=SC1090
  . "$_env"
  if [ -z "${APPS_IP_PUBLICA:-}" ]; then
    echo "entorno-actual.env no trae APPS_IP_PUBLICA" >&2
    return 1 2>/dev/null || exit 1
  fi
  URL_APP="https://${APPS_IP_PUBLICA}:8085"
else
  URL_APP="${URL_APP:-http://localhost:4200}"
fi

DESTINO="$_destino"
export DESTINO URL_APP
unset _destino _env
echo "  destino: $DESTINO  ->  $URL_APP" >&2
