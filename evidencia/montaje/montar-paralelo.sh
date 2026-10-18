#!/usr/bin/env bash
#
# Compone el tramo en paralelo: la aplicacion a la izquierda, la consola del
# broker a la derecha, en un cuadro de 1280x720 con su rotulo.
#
#   ./montaje/montar-paralelo.sh
#
# Sincronizacion: el desfase se MIDE durante la grabacion, no se deduce. La
# prueba anota en montaje/marcas-paralelo.json el instante exacto en que se
# crea cada pagina, y esa diferencia es el desfase, porque Playwright arranca
# el video de cada pagina cuando la pagina se crea.
#
# Deducirlo restando las duraciones de los dos archivos esta mal: no terminan
# juntos, porque los contextos se cierran en secuencia. Esa resta daba varios
# segundos de desfase que no existian.
set -euo pipefail
cd "$(dirname "$0")/.."

APP="$(ls videos/paralelo-app/*.webm 2>/dev/null | head -1)"
CON="$(ls videos/paralelo-consola/*.webm 2>/dev/null | head -1)"
MARCAS=montaje/marcas-paralelo.json
SALIDA=entregables/8-app-y-broker-en-paralelo.mp4

[ -n "$APP" ] && [ -n "$CON" ] || { echo "Faltan las grabaciones." >&2; exit 1; }
[ -f "$MARCAS" ] || { echo "Falta $MARCAS, que genera la propia prueba." >&2; exit 1; }

dur() { ffprobe -v error -show_entries format=duration -of csv=p=0 "$1"; }

DESF="$(python3 -c "import json;print(json.load(open('$MARCAS'))['app']/1000)")"
if (( $(echo "$DESF >= 0" | bc -l) )); then PAD_APP=0; PAD_CON="$DESF"
else PAD_APP="$(echo "0 - $DESF" | bc -l)"; PAD_CON=0; fi

printf '  desfase medido : %.3fs\n' "$DESF"
printf '  app            : %.1fs\n' "$(dur "$APP")"
printf '  consola        : %.1fs\n' "$(dur "$CON")"

# Se escribe a un temporal y recien al final se mueve al entregable: si el
# proceso se corta a mitad, el archivo bueno anterior queda intacto en vez de
# quedar truncado.
TMP="$(mktemp -t paralelo).mp4"
ffmpeg -y -loglevel error -i "$APP" -i "$CON" -i montaje/png/rotulo.png -filter_complex "
  [0:v]tpad=start_duration=$PAD_APP:start_mode=clone,scale=640:360:flags=lanczos[a];
  [1:v]tpad=start_duration=$PAD_CON:start_mode=clone,scale=640:360:flags=lanczos[b];
  [a][b]hstack=inputs=2:shortest=1[s];
  [s]pad=1280:720:0:180:black[p];
  [p][2:v]overlay=0:0[v]" \
  -map "[v]" -c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -r 25 -an "$TMP"

mv "$TMP" "$SALIDA"
printf '\n  %s  %ss\n' "$SALIDA" "$(dur "$SALIDA" | cut -d. -f1)"
