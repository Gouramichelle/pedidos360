# frontend-pedidos360

Angular 18 (standalone components) + MSAL Angular, con guards por rol y un
interceptor que adjunta el `Bearer <access_token>` a cada llamada al backend.

## Configuracion antes de correr

Completar `src/environments/environment.ts` (dev) y
`src/environments/environment.prod.ts` (build de produccion) con:

- `msal.clientId`: Application (client) ID de la App Registration "Pedidos360"
- `msal.authority`: `https://login.microsoftonline.com/<TENANT_ID>`
- `apiScopes`: `api://<API_CLIENT_ID>/access_as_user` (o el scope expuesto)
- `api.*`: URLs de cada microservicio en dev, o el Invoke URL de API Gateway
  en produccion (un solo host, ya que todas las rutas cuelgan del mismo HTTP
  API).

## Correr

```bash
npm install
npm start          # dev, contra los microservicios en localhost
npm run build      # build de produccion, contra API Gateway
```

## Estructura

- `core/auth`: configuracion de MSAL, lectura de roles desde los claims.
- `core/guards`: `roleGuard` (autorizacion), se combina con `MsalGuard`
  (autenticacion) en `app.routes.ts`.
- `core/api`: un servicio HTTP por microservicio.
- `features/*`: una carpeta por pantalla del caso (login, dashboard, orders,
  catalog, reports, audit), cada una con sus subcomponentes segun la
  propuesta de pantallas del Caso 0.

## Roles y rutas

| Ruta | Roles |
|---|---|
| `/dashboard` | cualquier usuario autenticado |
| `/orders` | Admin, Operador, Cliente (Cliente ve solo sus pedidos) |
| `/orders/nuevo` | Cliente, Operador |
| `/catalog` | Admin, Operador |
| `/reports` | Admin |
| `/audit` | Admin, Auditor |
