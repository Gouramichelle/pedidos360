/**
 * Entorno de desarrollo: `ng serve` en http://localhost:4200.
 *
 * Las llamadas van directo a cada microservicio corriendo en el host, sin pasar
 * por el API Gateway: en local el Gateway no existe. Cada microservicio admite
 * http://localhost:4200 como origen (`allowed-origins` en su perfil `local`),
 * asi que el preflight de CORS lo resuelve el propio servicio.
 *
 * Para desarrollar contra el backend desplegado en AWS, reemplazar las URLs de
 * abajo por el Invoke URL del Gateway, que es lo que hace environment.prod.ts.
 */
export const environment = {
  production: false,
  msal: {
    clientId: 'd0120ca1-1d51-493b-9eaa-1121b5b7303a',
    authority: 'https://login.microsoftonline.com/2d0922c9-f3ff-4709-b9b7-0c4b54529639',
    redirectUri: 'http://localhost:4200/auth/callback',
    postLogoutRedirectUri: 'http://localhost:4200',
  },
  apiScopes: ['api://d0120ca1-1d51-493b-9eaa-1121b5b7303a/access_as_user'],
  api: {
    orders: 'http://localhost:8080/api/orders',
    catalog: 'http://localhost:8081/api/catalog',
    report: 'http://localhost:8083/api/report',
    audit: 'http://localhost:8084/api/audit',
  },
};
