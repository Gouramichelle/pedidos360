import {
  BrowserCacheLocation,
  InteractionType,
  IPublicClientApplication,
  LogLevel,
  PublicClientApplication,
} from '@azure/msal-browser';
import {
  MsalGuardConfiguration,
  MsalInterceptorConfiguration,
} from '@azure/msal-angular';
import { environment } from '../../../environments/environment';

/**
 * Instancia de MSAL. sessionStorage en vez de localStorage: el token no
 * sobrevive a un cierre de pestaña, lo cual es preferible para una app que
 * maneja pedidos y datos de clientes.
 */
export function msalInstanceFactory(): IPublicClientApplication {
  return new PublicClientApplication({
    auth: {
      clientId: environment.msal.clientId,
      authority: environment.msal.authority,
      redirectUri: environment.msal.redirectUri,
      postLogoutRedirectUri: environment.msal.postLogoutRedirectUri,
    },
    cache: {
      cacheLocation: BrowserCacheLocation.SessionStorage,
    },
    system: {
      loggerOptions: {
        loggerCallback: (level: LogLevel, message: string) => {
          if (level === LogLevel.Error) {
            console.error(message);
          }
        },
        logLevel: LogLevel.Warning,
      },
    },
  });
}

/**
 * Config de MsalGuard. Las rutas NO lo usan (usan authGuard, que manda a
 * nuestra pantalla /login en vez de redirigir directo a Microsoft), pero
 * MSAL_GUARD_CONFIG es un provider obligatorio de msal-angular.
 *
 * Ojo: como el guard no se usa, estos scopes no se aplican en ningun lado --
 * el scope de la API se pide explicitamente en LoginComponent.
 */
export function msalGuardConfigFactory(): MsalGuardConfiguration {
  return {
    interactionType: InteractionType.Redirect,
    authRequest: {
      scopes: environment.apiScopes,
    },
  };
}

/**
 * En produccion environment.api.* son rutas relativas (el proxy de Nginx las
 * manda al API Gateway); en desarrollo son URLs absolutas a cada
 * microservicio en localhost. El interceptor necesita URLs absolutas para
 * decidir a que llamadas les adjunta el token, asi que se resuelven contra el
 * origen actual cuando hace falta.
 */
function aUrlAbsoluta(url: string): string {
  return url.startsWith('http') ? url : `${window.location.origin}${url}`;
}

/**
 * MsalInterceptor: adjunta el Bearer token en cada llamada cuya URL matchea
 * la lista de abajo. Solo se adjunta a las URLs del backend propio -- nunca a
 * llamadas a terceros.
 */
export function msalInterceptorConfigFactory(): MsalInterceptorConfiguration {
  const protectedResourceMap = new Map<string, Array<string> | null>();

  [
    environment.api.orders,
    environment.api.catalog,
    environment.api.report,
    environment.api.audit,
  ].forEach((base) => {
    const absoluta = aUrlAbsoluta(base);
    // La URL exacta (ej. la lista de pedidos) y todo lo que cuelgue de ella
    // (ej. el detalle /api/orders/5 o el cambio de estado).
    protectedResourceMap.set(absoluta, environment.apiScopes);
    protectedResourceMap.set(`${absoluta}/*`, environment.apiScopes);
  });

  return {
    interactionType: InteractionType.Redirect,
    protectedResourceMap,
  };
}
