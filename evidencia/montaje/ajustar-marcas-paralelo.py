# -*- coding: utf-8 -*-
"""
Traslada las marcas del tramo en paralelo al tiempo del clip ya compuesto.

La prueba las anota contra el inicio de la grabacion, pero la composicion le
recorta los primeros segundos (la carga de las dos paginas). Sin restar ese
recorte, la narracion apuntaria a momentos corridos.

  python3 montaje/ajustar-marcas-paralelo.py <recorte-en-segundos>
"""
import json, sys
from pathlib import Path

raiz = Path(__file__).parent.parent
recorte = float(sys.argv[1]) if len(sys.argv) > 1 else 0.0
crudas = json.loads((raiz / "montaje/marcas-paralelo.json").read_text())

# La consola de RabbitMQ refresca cada cinco segundos, asi que un cambio no
# aparece en pantalla en el instante en que ocurre sino en el refresco
# siguiente. Las marcas que usa la narracion tienen que apuntar a cuando se VE
# el cambio, no a cuando se produjo: si no, la voz anuncia que las colas se
# llenaron mientras en pantalla todavia hay ceros.
#
# Medido sobre la grabacion: la confirmacion ocurre a los 23 s y los contadores
# cambian a los 28; la reanudacion a los 45 y se vacian a los 53.
RETRASO_CONSOLA = 6.0
CON_RETRASO = {"confirmacion", "reanudacion"}

pasos = [("inicio", 0.0)] + [
    (k, crudas[k] / 1000 - recorte + (RETRASO_CONSOLA if k in CON_RETRASO else 0.0))
    for k in ("pausa", "confirmacion", "reanudacion", "fin")
    if k in crudas
]

destino = raiz / "montaje/marcas/paralelo.json"
destino.parent.mkdir(parents=True, exist_ok=True)
destino.write_text(json.dumps(
    [{"paso": k, "segundo": round(max(0.0, v), 2)} for k, v in pasos], indent=2))
for k, v in pasos:
    print(f"  {k:14} {max(0.0, v):6.1f}s")
