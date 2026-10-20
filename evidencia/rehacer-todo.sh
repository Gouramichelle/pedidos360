#!/usr/bin/env bash
#
# Regraba toda la evidencia y rearma el video narrado, de una sola vez.
#
#   ./rehacer-todo.sh
#
# Requisitos, en este orden:
#   1. Sesion del Learner Lab iniciada y credenciales frescas en ~/.aws/credentials
#   2. ../infra/aws/provisionar.sh y actualizar-gateway.sh ya corridos
#   3. El tunel SSH al broker abierto:
#        ssh -i ~/Downloads/labsuser.pem -L 15672:localhost:15672 \
#            -L 15673:localhost:15673 ec2-user@<ip-publica-broker>
#   4. La sesion de Azure guardada: npm run sesion
#
# Si cambio la IP publica de ec2-apps, hay que actualizarla antes en
# package.json y en specs/02-app/flujo.spec.ts y specs/03-paralelo/en-vivo.spec.ts.
set -euo pipefail
cd "$(dirname "$0")"

azul() { printf '\033[34m%s\033[0m\n' "$*"; }

azul "[1/6] Comprobando que haya con que grabar"
curl -s -u guest:guest -o /dev/null --max-time 6 http://localhost:15672/api/overview \
  || { echo "  El tunel al broker no responde en localhost:15672" >&2; exit 1; }
[ -f .auth/sesion.json ] || { echo "  Falta la sesion de Azure: npm run sesion" >&2; exit 1; }
echo "  tunel y sesion listos"

azul "[2/6] Grabando los clips"
npx playwright test --reporter=line

azul "[3/6] Nombrando los entregables"
./renombrar.sh

azul "[4/6] Componiendo el tramo en paralelo"
./montaje/montar-paralelo.sh

azul "[5/6] Generando laminas, voz y video narrado"
node montaje/tarjetas.mjs >/dev/null
node montaje/laminas.mjs >/dev/null
python3 montaje/ensamblar.py

azul "[6/6] Regenerando el guion con los tiempos del video"
python3 montaje/guion.py
