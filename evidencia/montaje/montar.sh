#!/usr/bin/env bash
#
# Une las tarjetas de transicion y los siete videos en una sola pieza.
#
#   node montaje/tarjetas.mjs    # genera los PNG
#   ./montaje/montar.sh          # arma el video
#
# Sale un MP4 H.264, que se reproduce en cualquier parte sin instalar nada.
# Los clips de Playwright son VP9, asi que hay que recodificar igual; se
# aprovecha para normalizar todo a 1280x720 a 25 fps y que el concat no tenga
# que lidiar con parametros distintos.
set -euo pipefail
cd "$(dirname "$0")/.."

PNG=montaje/png
CLIPS=montaje/clips
SALIDA=entregables/pedidos360-demostracion.mp4
rm -rf "$CLIPS" && mkdir -p "$CLIPS"

comun=(-c:v libx264 -preset medium -crf 20 -pix_fmt yuv420p -r 25 -an)

# Tarjeta: imagen fija con un fundido de entrada y salida.
tarjeta() {  # $1=nombre  $2=segundos
  local dur="$2"
  ffmpeg -y -loglevel error -loop 1 -t "$dur" -i "$PNG/$1.png" \
    -vf "scale=1280:720,fade=t=in:st=0:d=0.4,fade=t=out:st=$(echo "$dur-0.4"|bc):d=0.4,format=yuv420p" \
    "${comun[@]}" "$CLIPS/$1.mp4"
  printf '  tarjeta  %-34s %ss\n' "$1" "$dur"
}

# Clip grabado: solo se normaliza.
clip() {  # $1=archivo sin extension
  ffmpeg -y -loglevel error -i "entregables/$1.webm" \
    -vf "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2" \
    "${comun[@]}" "$CLIPS/$1.mp4"
  printf '  video    %-34s %ss\n' "$1" \
    "$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$CLIPS/$1.mp4" | cut -d. -f1)"
}

echo "-- Normalizando piezas --"
tarjeta portada 5
clip 6-login-catalogo-y-pedido
tarjeta t1 4
clip 7-auditoria-y-reportes
tarjeta t2 4
clip 1-cluster-dos-nodos
tarjeta t3 4
clip 2-seis-colas-y-sus-dlq
tarjeta t4 4
clip 3-exchanges-direct-y-topic
tarjeta t5 4
clip 4-politicas-de-retencion
tarjeta t6 4
clip 5-caida-de-consumidor
tarjeta t7 4
tarjeta cierre 6

# El orden del montaje: cada tarjeta anuncia el clip que viene detras.
cat > "$CLIPS/orden.txt" <<ORDEN
file 'portada.mp4'
file 't1.mp4'
file '6-login-catalogo-y-pedido.mp4'
file 't2.mp4'
file '7-auditoria-y-reportes.mp4'
file 't3.mp4'
file '1-cluster-dos-nodos.mp4'
file 't4.mp4'
file '2-seis-colas-y-sus-dlq.mp4'
file 't5.mp4'
file '3-exchanges-direct-y-topic.mp4'
file 't6.mp4'
file '4-politicas-de-retencion.mp4'
file 't7.mp4'
file '5-caida-de-consumidor.mp4'
file 'cierre.mp4'
ORDEN

echo
echo "-- Uniendo --"
ffmpeg -y -loglevel error -f concat -safe 0 -i "$CLIPS/orden.txt" -c copy "$SALIDA"

DUR=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$SALIDA" | cut -d. -f1)
echo "  $SALIDA"
echo "  $((DUR/60)) min $((DUR%60)) s · $(du -h "$SALIDA" | cut -f1)"
