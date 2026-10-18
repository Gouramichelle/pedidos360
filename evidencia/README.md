# Evidencia en video

Graba automaticamente el recorrido que hay que demostrar en EP3 y EP4, contra
el despliegue real en AWS. La idea es poder reproducirlo narrado en vez de
hacerlo en vivo, donde cualquier cosa puede fallar.

El guion de lo que se dice en cada momento esta en **[GUION.md](GUION.md)**.

## El entregable

**`entregables/pedidos360-demostracion.mp4`** — 5 min 46 s.

Un solo video con todo el recorrido, con tarjetas de transicion que anuncian
cada tramo. Se puede dejar correr de principio a fin mientras se narra; el
guion con las marcas de tiempo esta en [GUION.md](GUION.md).

Se arma con:

```bash
node montaje/tarjetas.mjs   # tarjetas de transicion, renderizadas con el navegador
./montaje/montar.sh         # normaliza todo a H.264 y lo une
```

Las tarjetas se renderizan como HTML y se capturan con Playwright, no con
ffmpeg: este ffmpeg viene sin freetype y no tiene el filtro `drawtext`.

## Los siete clips que lo componen

| Archivo | Duracion | Que demuestra |
|---|---|---|
| `1-cluster-dos-nodos` | 15 s | Los dos nodos integrados en un cluster |
| `2-seis-colas-y-sus-dlq` | 14 s | Tres colas de comando y sus tres DLQ |
| `3-exchanges-direct-y-topic` | 12 s | Los dos tipos de exchange, mas el de dead-lettering |
| `4-politicas-de-retencion` | 14 s | TTL, dead-lettering y tope de las colas muertas |
| `5-caida-de-consumidor` | 94 s | El escenario de fallo completo, con `x-death: expired` |
| `6-login-catalogo-y-pedido` | 49 s | OAuth 2.0/OIDC, catalogo real y creacion de un pedido |
| `7-auditoria-y-reportes` | 36 s | Los consumidores reaccionando al evento |
| `8-app-y-broker-en-paralelo` | 66 s | La app y la consola del broker **al mismo tiempo** |

Total: 303 segundos de grabacion, mas 43 de tarjetas y portada.
Con la narracion la presentacion queda entre 9 y 11 minutos.

El tramo en paralelo se graba con dos contextos de navegador a la vez, cada uno
con su propio video, y despues se componen lado a lado con ffmpeg. Es el que
demuestra que la conexion es real: una accion en la aplicacion y su efecto en
el broker, en el mismo instante.

## Como volver a grabarlos

Hace falta el despliegue arriba y el tunel SSH a la consola del broker:

```bash
ssh -i ~/Downloads/labsuser.pem -L 15672:localhost:15672 -L 15673:localhost:15673 \
    ec2-user@<ip-publica-de-ec2-broker>
```

Y la sesion de Azure AD guardada. El login lo hace una persona, una sola vez:

```bash
npm install
npm run sesion      # entrar con la cuenta y CERRAR el navegador
```

Playwright no escribe contraseñas. Lo que guarda son las cookies de SSO de
Azure AD, y con eso el redirect de OAuth vuelve ya autenticado. El video igual
muestra el redirect completo, que es justamente lo que hay que demostrar.

Despues:

```bash
npm run grabar      # los siete
./renombrar.sh      # los copia a entregables/ con nombres ordenables
```

O por partes: `npm run grabar:broker` y `npm run grabar:app`.

## Lo que NO graba Playwright

Dos tramos necesitan tu sesion autenticada y hay que mostrarlos en vivo o
grabarlos con captura de pantalla:

- **La consola de AWS**: la instancia del API Manager, sus rutas y las dos
  instancias EC2
- **El portal de Azure**: el tenant y los usuarios registrados

El guion incluye el recorrido y la narracion de los dos.

## Si cambia la IP de la sesion del lab

La URL de la app esta fija en dos lugares: el script `sesion` de
`package.json` y la constante `APP` de `specs/02-app/flujo.spec.ts`. Al
reiniciar el lab hay que actualizar ambas, ademas del redirect URI en Azure.

## Hacerlo a mano

Si el docente pide reproducirlo en vivo, el recorrido completo esta en
**[PASO-A-PASO.md](PASO-A-PASO.md)**: como abrir el tunel a la consola del
broker, donde mirar cada cosa, y como demostrar la caida de un consumidor.
