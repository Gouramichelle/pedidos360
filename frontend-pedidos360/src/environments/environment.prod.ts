/**
 * Entorno de produccion.
 *
 * Las URLs de API son relativas a proposito: Nginx sirve el frontend y ademas
 * hace de proxy de /api/* hacia el Invoke URL del API Gateway (ver
 * nginx.conf). Al quedar todo en el mismo origen el navegador no dispara
 * preflight ni CORS, y el API Gateway con su JWT Authorizer sigue validando
 * el token en el borde antes de enrutar al microservicio.
 */
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
    orders: '/api/orders',
    catalog: '/api/catalog',
    report: '/api/report',
    audit: '/api/audit',
  },
};
