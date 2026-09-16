/**
 * Entorno de desarrollo: MSAL contra el tenant de Azure AD y llamadas HTTP
 * directas a cada microservicio (sin pasar por API Gateway), para poder
 * levantar `ng serve` contra los servicios corriendo en localhost.
 *
 * Reemplazar TENANT_ID, SPA_CLIENT_ID y API_CLIENT_ID por los valores reales
 * de la App Registration "Pedidos360" en Azure AD antes de correr la app.
 */
export const environment = {
  production: false,
  msal: {
    clientId: 'd0120ca1-1d51-493b-9eaa-1121b5b7303a',
    authority: 'https://login.microsoftonline.com/2d0922c9-f3ff-4709-b9b7-0c4b54529639',
    redirectUri: 'http://localhost:4200/auth/callback',
    postLogoutRedirectUri: 'http://localhost:4200',
  },
  // El scope se termina de armar cuando expongamos la API en el paso
  // "Expose an API" -- por ahora usa el mismo client ID como App ID URI
  // (formato que Azure AD ofrece por default: api://<client-id>).
  apiScopes: ['api://d0120ca1-1d51-493b-9eaa-1121b5b7303a/access_as_user'],
  api: {
    orders: 'http://localhost:8080/api/orders',
    catalog: 'http://localhost:8081/api/catalog',
    report: 'http://localhost:8083/api/report',
    audit: 'http://localhost:8084/api/audit',
  },
};
