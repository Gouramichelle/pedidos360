# Evidencia en video

Graba el recorrido completo de la demostracion contra el despliegue real y lo
arma en **un solo video narrado**, con voz en español explicando cada cosa como
si uno se la estuviera presentando al docente.

## El entregable

**`entregables/pedidos360-demostracion.mp4`**

Incluye, en orden: la aplicacion con login OAuth 2.0 / OpenID Connect, los
consumidores reaccionando a los eventos, la aplicacion y el broker corriendo en
paralelo, **como esta construida cada cola y cada cola muerta en el codigo**,
el cluster de dos nodos, las seis colas, los dos tipos de exchange, las
politicas de retencion, **los dos caminos hacia la cola muerta**, y la caida de
un consumidor de punta a punta.

## Como se arma

```bash
./rehacer-todo.sh
```

Encadena todo: graba los clips, compone el tramo en paralelo, genera las
laminas de codigo, sintetiza la voz y ensambla el video.

Requisitos: sesion del Learner Lab activa, el tunel SSH al broker abierto, y la
sesion de Azure guardada con `npm run sesion`.

## Por que la narracion manda sobre el ritmo

`montaje/ensamblar.py` genera primero la voz de cada tramo, mide cuanto dura, y
**recien entonces** construye el video de ese tramo con esa duracion. Si la
narracion es mas larga que la grabacion, el clip se ralentiza; si es mas corta,
la voz queda con silencio al final. Asi ninguna explicacion queda cortada ni
una imagen se va antes de terminar de hablar de ella.

## Por que los tramos se anclan a marcas

Un clip largo no se narra de un tiron: se parte en sub-tramos, y cada uno
apunta a un pedazo concreto del video. Pero los cortes **no se escriben a
mano**: las propias pruebas anotan en `montaje/marcas/` el segundo exacto en
que ocurre cada paso mientras graban, y la narracion los referencia por nombre.

```python
dict(id='app-catalogo', kind='clip', src='...',
     marcas='app', desde='catalogo', hasta='formulario', texto="""...""")
```

Sin esto hay que adivinar donde empieza cada escena, y el sintoma es que la voz
habla de algo que ya salio de pantalla. Paso, y asi se corrigio.

## Archivos

| Archivo | Para que |
|---|---|
| `rehacer-todo.sh` | Rehace todo de una sola vez |
| `montaje/narracion.py` | El guion: que se ve y que se dice en cada tramo |
| `montaje/ensamblar.py` | Genera la voz y arma el video |
| `montaje/guion.py` | Escribe GUION.md con los tiempos que midio el ensamblador |
| `montaje/laminas.mjs` | Laminas de codigo de los tramos explicativos |
| `montaje/tarjetas.mjs` | Portada y cierre |
| `montaje/montar-paralelo.sh` | Compone la app y la consola lado a lado |
| `renombrar.sh` | Nombra los clips grabados |
| `specs/` | Las grabaciones, con sus marcas de tiempo |

## Hacerlo a mano

**[GUION.md](GUION.md)** es lo que conviene llevar a la defensa: el texto de la
narracion tramo por tramo, con el minuto en que va cada uno y **la ruta en vivo
de cada tramo** —que abrir o que correr para mostrar en el momento lo mismo que
se ve grabado—. Lo genera `montaje/guion.py` desde `narracion.py` y los tiempos
reales del video, asi que no puede quedar desfasado.

**[PASO-A-PASO.md](PASO-A-PASO.md)** es el recorrido completo contra el
despliegue en AWS, con el tunel SSH y los comandos por SSH.

## Si cambia la IP de la sesion del lab

La URL del despliegue esta fija en tres lugares: el script `sesion` de
`package.json`, y la constante `APP` de `specs/02-app/flujo.spec.ts` y
`specs/03-paralelo/en-vivo.spec.ts`.
