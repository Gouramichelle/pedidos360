#!/usr/bin/env bash
# Playwright nombra las carpetas de video con un hash del titulo de la prueba.
# Esto las copia a evidencia/entregables/ con nombres ordenables, listos para
# reproducir en la presentacion siguiendo GUION.md.
set -euo pipefail
cd "$(dirname "$0")"
rm -rf entregables && mkdir -p entregables

# Se matchea el final del nombre, no el principio: todas las carpetas de un
# mismo archivo .spec comparten prefijo y matchear por ahi las confunde.
clasificar() {
  case "$1" in
    *integrados-en-un-cluster*)   echo "1-cluster-dos-nodos" ;;
    *tres-DLQ*)                   echo "2-seis-colas-y-sus-dlq" ;;
    *mas-el-de-dead-lettering*)   echo "3-exchanges-direct-y-topic" ;;
    *retencion-y-dead-lettering*) echo "4-politicas-de-retencion" ;;
    *cola-muerta*)                echo "5-caida-de-consumidor" ;;
    *login*|*pedido*|*app*)       echo "6-flujo-completo-en-la-app" ;;
    *)                            echo "" ;;
  esac
}

sin_clasificar=0
for v in videos/*/video.webm; do
  [ -f "$v" ] || continue
  d="$(basename "$(dirname "$v")")"
  nombre="$(clasificar "$d")"
  if [ -z "$nombre" ]; then
    sin_clasificar=$((sin_clasificar+1))
    nombre="sin-clasificar-$sin_clasificar"
    printf '  \033[33m!\033[0m %-32s <- %s\n' "${nombre}.webm" "$d"
  else
    printf '    %-32s %s\n' "${nombre}.webm" "$(du -h "$v" | cut -f1)"
  fi
  cp "$v" "entregables/${nombre}.webm"
done

echo
echo "  $(ls entregables | wc -l | tr -d ' ') videos en evidencia/entregables/"
