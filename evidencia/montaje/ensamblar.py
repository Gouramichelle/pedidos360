# -*- coding: utf-8 -*-
"""
Arma el video narrado.

El orden importa: primero se genera la voz de cada tramo y se mide cuanto dura,
y recien entonces se construye el video de ese tramo con esa duracion. La
narracion manda sobre el ritmo, no al reves. Asi ninguna explicacion queda
cortada ni ninguna imagen se va antes de que se termine de hablar de ella.

  python3 montaje/ensamblar.py
"""
import json, re, shutil, subprocess, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from narracion import TRAMOS

RAIZ = Path(__file__).parent.parent
VOZ = "Mónica"
# La pauta de EP4 exige que la presentacion dure entre cinco y diez minutos.
# A 155 palabras por minuto el video daba 11:11 y se pasaba. A 172 entra, y
# sigue siendo un ritmo de exposicion normal: no es lectura acelerada.
PALABRAS_POR_MINUTO = 172
COLA_SILENCIO = 0.9          # respiro al final de cada tramo
ESTIRADO_MAXIMO = 2.2        # mas alla de esto, el video se ve en camara lenta
# Cuando la grabacion dura mas que su narracion, se acelera en vez de dejarla
# correr en silencio. Hasta 1.6x una grabacion de pantalla se sigue leyendo sin
# problema, y evita tramos donde no se dice nada mientras algo se mueve.
ACELERADO_MAXIMO = 1.6

AUD = RAIZ / "montaje/audio"
SEG = RAIZ / "montaje/segmentos"
SALIDA = RAIZ / "entregables/pedidos360-demostracion.mp4"


def correr(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print("  FALLO:", " ".join(str(c) for c in cmd[:6]), "...")
        print("  ", (r.stderr or "").strip()[:400])
        raise SystemExit(1)
    return r.stdout.strip()


def duracion(p):
    return float(correr(["ffprobe", "-v", "error", "-show_entries",
                         "format=duration", "-of", "csv=p=0", str(p)]))


def rango_de_marcas(t):
    """Traduce `desde`/`hasta` (nombres de paso) a segundos del clip.

    Las marcas las anota la propia prueba mientras graba, asi que un tramo
    narrado siempre apunta al pedazo correcto del video aunque la grabacion
    cambie de ritmo entre corridas. Sin esto habria que adivinar los cortes, y
    la voz termina hablando de algo que ya salio de pantalla.
    """
    if not t.get("marcas"):
        return None, None
    datos = json.loads((RAIZ / f"montaje/marcas/{t['marcas']}.json").read_text())
    pasos = {m["paso"]: m["segundo"] for m in datos}
    return pasos.get(t.get("desde")), pasos.get(t.get("hasta"))


def limpiar(texto):
    """El texto viene con sangria del codigo; la voz necesita una sola linea."""
    return re.sub(r"\s+", " ", texto).strip()


def generar_voz():
    AUD.mkdir(parents=True, exist_ok=True)
    duraciones = {}
    for t in TRAMOS:
        destino = AUD / f"{t['id']}.aiff"
        correr(["say", "-v", VOZ, "-r", str(PALABRAS_POR_MINUTO),
                "-o", str(destino), limpiar(t["texto"])])
        duraciones[t["id"]] = duracion(destino)
        print(f"  voz  {t['id']:16} {duraciones[t['id']]:6.1f}s")
    return duraciones


def construir_tramo(t, dur_voz):
    """Un tramo = su imagen o clip, estirado a lo que dure la narracion."""
    destino = SEG / f"{t['id']}.mp4"
    objetivo = dur_voz + COLA_SILENCIO
    audio = AUD / f"{t['id']}.aiff"
    comun = ["-c:v", "libx264", "-preset", "medium", "-crf", "20",
             "-pix_fmt", "yuv420p", "-r", "25",
             "-c:a", "aac", "-b:a", "128k", "-ar", "48000", "-ac", "2"]

    if t["kind"] == "imagen":
        correr(["ffmpeg", "-y", "-loglevel", "error",
                "-loop", "1", "-t", f"{objetivo:.3f}", "-i", str(RAIZ / t["src"]),
                "-i", str(audio),
                "-filter_complex",
                f"[0:v]scale=1280:720,fade=t=in:st=0:d=0.4,"
                f"fade=t=out:st={objetivo-0.4:.3f}:d=0.4,format=yuv420p[v];"
                f"[1:a]apad=whole_dur={objetivo:.3f}[a]",
                "-map", "[v]", "-map", "[a]", *comun, "-shortest", str(destino)])
        return destino, objetivo

    desde, hasta = rango_de_marcas(t)
    entrada = ["-i", str(RAIZ / t["src"])]
    if desde is not None:
        entrada = ["-ss", f"{desde:.3f}"] + entrada
        if hasta is not None:
            entrada = entrada[:2] + ["-to", f"{hasta:.3f}"] + entrada[2:]
        dur_clip = (hasta if hasta is not None else duracion(RAIZ / t["src"])) - desde
    else:
        dur_clip = duracion(RAIZ / t["src"])
    factor = objetivo / dur_clip

    if factor <= 1.0:
        # El clip dura mas que la narracion. Se acelera hasta el tope para que
        # no queden segundos en silencio, sin cortar nada de lo mostrado.
        acelerado = max(dur_clip / ACELERADO_MAXIMO, objetivo)
        factor_v = acelerado / dur_clip
        objetivo = acelerado
        filtro_v = f"[0:v]setpts={factor_v:.4f}*PTS,scale=1280:720,setsar=1[v]"
    elif factor <= ESTIRADO_MAXIMO:
        # Se ralentiza para acompanar la narracion. En una grabacion de
        # pantalla no se nota, y da tiempo a leer lo que esta en pantalla.
        filtro_v = f"[0:v]setpts={factor:.4f}*PTS,scale=1280:720,setsar=1[v]"
    else:
        # Estirar tanto se veria en camara lenta: se ralentiza hasta el tope y
        # el resto se cubre congelando el ultimo fotograma.
        resto = objetivo - dur_clip * ESTIRADO_MAXIMO
        filtro_v = (f"[0:v]setpts={ESTIRADO_MAXIMO}*PTS,scale=1280:720,setsar=1,"
                    f"tpad=stop_duration={resto:.3f}:stop_mode=clone[v]")

    correr(["ffmpeg", "-y", "-loglevel", "error",
            *entrada, "-i", str(audio),
            "-filter_complex", f"{filtro_v};[1:a]apad=whole_dur={objetivo:.3f}[a]",
            "-map", "[v]", "-map", "[a]", *comun, "-t", f"{objetivo:.3f}", str(destino)])
    return destino, objetivo


def main():
    print("-- Generando la voz --")
    voces = generar_voz()

    print("\n-- Construyendo cada tramo --")
    if SEG.exists():
        shutil.rmtree(SEG)
    SEG.mkdir(parents=True)
    linea, reloj = [], 0.0
    for t in TRAMOS:
        destino, dur = construir_tramo(t, voces[t["id"]])
        linea.append(dict(id=t["id"], inicio=reloj, dur=dur))
        print(f"  {int(reloj)//60}:{int(reloj)%60:02d}  {t['id']:16} {dur:6.1f}s")
        reloj += dur

    orden = SEG / "orden.txt"
    orden.write_text("".join(f"file '{t['id']}.mp4'\n" for t in TRAMOS))
    correr(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0",
            "-i", str(orden), "-c", "copy", str(SALIDA)])

    (RAIZ / "montaje/linea-tiempo.json").write_text(json.dumps(linea, indent=2))
    total = duracion(SALIDA)
    print(f"\n-- Listo --\n  {SALIDA.relative_to(RAIZ)}")
    print(f"  {int(total)//60} min {int(total)%60} s · {SALIDA.stat().st_size//1024//1024} MB")


if __name__ == "__main__":
    main()
