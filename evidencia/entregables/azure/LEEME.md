# Capturas del portal de Azure

Tomadas el 2026-10-20 contra el inquilino real, con `montaje/azure-capturas.mjs`.
Solo navegan y fotografian: no cambian ninguna configuracion.

| Captura | Que prueba |
|---|---|
| `1-inquilino.png` | El inquilino `actividadcloudnative`, Tenant ID `2d0922c9-f3ff-4709-b9b7-0c4b54529639`, 4 usuarios, 3 aplicaciones, licencia Entra ID Free |
| `2-registro-autenticacion.png` | Plataforma **Single-page application** y los redirect URI. Es lo que habilita Authorization Code con PKCE en vez del flujo implicito |
| `3-aplicaciones-empresa.png` | La aplicacion `Pedidos360`, Application ID `d0120ca1-1d51-493b-...`, Identifier URI `api://d0120ca1-...` |
| `4-usuarios.png` | Los cuatro usuarios: una Member y tres Guest, una de ellas por invitacion externa |

## Lo que ata el IDaaS con el API Manager

No son capturas sueltas: los identificadores coinciden.

| En Azure | En el API Gateway |
|---|---|
| Tenant ID `2d0922c9-...` | Emisor `https://login.microsoftonline.com/2d0922c9-.../v2.0` |
| Application ID `d0120ca1-...` | Audiencia `d0120ca1-...` |
| Identifier URI `api://d0120ca1-...` | Audiencia `api://d0120ca1-...` |

El validador del Gateway acepta exactamente el inquilino y la aplicacion que
estan registrados en el portal, y nada mas.

## Dos cosas que conviene saber

**Queda un redirect URI colgando.** En `2-registro-autenticacion.png` aparece
`https://3.83.237.65:8085/auth/callback`, de una sesion anterior del
laboratorio. Esa direccion ya no es nuestra y AWS puede reasignarla a otra
cuenta, asi que conviene borrarla. Si preguntan por minimo privilegio, es un
buen ejemplo de higiene: cada redirect URI registrado es un destino al que el
proveedor esta dispuesto a enviar un codigo de autorizacion.

**`4-usuarios.png` muestra nombres reales** de personas invitadas al inquilino.
Conviene decidir si esa lamina entra en el entregable que se archiva o se
muestra solo en vivo.

## Rehacerlas

```bash
cd evidencia && node montaje/azure-capturas.mjs
```

La primera vez pide iniciar sesion y el codigo por mensaje. Despues guarda la
sesion en `.auth/azure.json` y ya no vuelve a pedirlo.
