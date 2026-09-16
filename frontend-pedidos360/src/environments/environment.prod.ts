/**
 * Entorno de produccion.
 *
 * El frontend llama directamente al Invoke URL del API Gateway. Es una
 * peticion cruzada, asi que el navegador dispara el preflight y se aplica la
 * configuracion de CORS del Gateway, que es justamente lo que hay que poder
 * mostrar y verificar.
 *
 * Nginx conserva un proxy de /api/* hacia el mismo Invoke URL (ver
 * nginx.conf), que queda como alternativa de respaldo: apuntando estas URLs a
 * rutas relativas, todo vuelve a viajar por el mismo origen y CORS deja de
 * intervenir.
 */
const API_GATEWAY_URL = 'https://7x6u8dfoh0.execute-api.us-east-1.amazonaws.com';

export const environment = {
  production: true,
  msal: {
    clientId: 'd0120ca1-1d51-493b-9eaa-1121b5b7303a',
    authority: 'https://login.microsoftonline.com/2d0922c9-f3ff-4709-b9b7-0c4b54529639',
    // Se calculan desde el origen en que se sirve la app, no se hardcodean:
    // la IP publica de ec2-apps cambia en cada sesion del Learner Lab y asi el
    // mismo build sirve sin recompilar. Lo unico que hay que mantener al dia
    // es el redirect URI registrado en la App Registration de Azure AD, que
    // tiene que coincidir exactamente con este valor.
    redirectUri: `${window.location.origin}/auth/callback`,
    postLogoutRedirectUri: window.location.origin,
  },
  apiScopes: ['api://d0120ca1-1d51-493b-9eaa-1121b5b7303a/access_as_user'],
  api: {
    orders: `${API_GATEWAY_URL}/api/orders`,
    catalog: `${API_GATEWAY_URL}/api/catalog`,
    report: `${API_GATEWAY_URL}/api/report`,
    audit: `${API_GATEWAY_URL}/api/audit`,
  },
};
