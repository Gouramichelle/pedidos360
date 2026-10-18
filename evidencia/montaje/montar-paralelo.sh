#!/usr/bin/env bash
#
# Compone el tramo en paralelo: la aplicacion a la izquierda y la consola del
# broker a la derecha, en un cuadro de 1280x720 con su rotulo.
#
#   ./montaje/montar-paralelo.sh
#
# Sincronizacion: las dos paginas se cierran en el mismo instante, asi que los
# dos videos terminan juntos. Si uno dura mas es porque arranco antes, y esa
# diferencia es exactamente el desfase. Se compensa congelando el primer
# fotograma del mas corto hasta igualar el arranque, en vez de apilarlos desde
# el cero de cada archivo, que es lo que los desincronizaba.
#
# El desfase se mide en cada corrida y no se fija a mano: depende de cuanto
# tarde el navegador en crear cada contexto y varia entre grabaciones.
set -euo pipefail
cd "$(dirname "$0")/.."

APP="$(ls videos/paralelo-app/*.webm 2>/dev/null | head -1)"
CON="$(ls videos/paralelo-consola/*.webm 2>/dev/null | head -1)"
[ -n "$APP" ] && [ -n "$CON" ] || { echo "Faltan las grabaciones. Corre antes: npx playwright test specs/03-paralelo --project=paralelo" >&2; exit 1; }

dur() { ffprobe -v error -show_entries format=duration -of csv=p=0 "$1"; }
DA="$(dur "$APP")"; DC="$(dur "$CON")"

# Cual arranco antes y por cuanto.
DESFASE="$(echo "$DC - $DA" | bc)"
if (( $(echo "$DESFASE >= 0" | bc) )); then
  PAD_APP="$DESFASE"; PAD_CON=0
else
  PAD_APP=0; PAD_CON="$(echo "0 - $DESFASE" | bc)"
fi
printf '  app     %.1fs   (se le anteponen %.1fs)\n' "$DA" "$PAD_APP"
printf '  consola %.1fs   (se le anteponen %.1fs)\n' "$DC" "$PAD_CON"

ffmpeg -y -loglevel error -i "$APP" -i "$CON" -i montaje/png/rotulo.png -filter_complex "
  [0:v]tpad=start_duration=$PAD_APP:start_mode=clone,scale=640:360:flags=lanczos[a];
  [1:v]tpad=start_duration=$PAD_CON:start_mode=clone,scale=640:360:flags=lanczos[b];
  [a][b]hstack=inputs=2[s];
  [s]pad=1280:720:0:180:black[p];
  [p][2:v]overlay=0:0[v]" \
  -map "[v]" -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -r 25 -an \
  entregables/8-app-y-broker-en-paralelo.mp4

printf '\n  entregables/8-app-y-broker-en-paralelo.mp4  %ss\n' \
  "$(dur entregables/8-app-y-broker-en-paralelo.mp4 | cut -d. -f1)"
