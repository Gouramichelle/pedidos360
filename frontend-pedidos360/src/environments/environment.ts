/**
 * Entorno de desarrollo: `ng serve` en http://localhost:4200.
 *
 * Las llamadas van al API Gateway desplegado, no a microservicios locales:
 * el CORS del Gateway ya admite http://localhost:4200 como origen, asi que se
 * puede desarrollar el frontend contra el backend real sin levantar nada mas.
 * Para trabajar contra servicios corriendo en la propia maquina, reemplazar
 * las URLs de abajo por http://localhost:8080 a 8084.
 */
const API_GATEWAY_URL = 'https://7x6u8dfoh0.execute-api.us-east-1.amazonaws.com';

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
    orders: `${API_GATEWAY_URL}/api/orders`,
    catalog: `${API_GATEWAY_URL}/api/catalog`,
    report: `${API_GATEWAY_URL}/api/report`,
    audit: `${API_GATEWAY_URL}/api/audit`,
  },
};
