import { APP_INITIALIZER, ApplicationConfig, importProvidersFrom } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HTTP_INTERCEPTORS } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import {
  MSAL_GUARD_CONFIG,
  MSAL_INSTANCE,
  MSAL_INTERCEPTOR_CONFIG,
  MsalBroadcastService,
  MsalGuard,
  MsalInterceptor,
  MsalModule,
  MsalService,
} from '@azure/msal-angular';

import { routes } from './app.routes';
import {
  msalGuardConfigFactory,
  msalInstanceFactory,
  msalInterceptorConfigFactory,
} from './core/auth/msal.config';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptorsFromDi()),
    importProvidersFrom(MsalModule),
    {
      provide: MSAL_INSTANCE,
      useFactory: msalInstanceFactory,
    },
    {
      provide: MSAL_GUARD_CONFIG,
      useFactory: msalGuardConfigFactory,
    },
    {
      provide: MSAL_INTERCEPTOR_CONFIG,
      useFactory: msalInterceptorConfigFactory,
    },
    {
      provide: HTTP_INTERCEPTORS,
      useClass: MsalInterceptor,
      multi: true,
    },
    /**
     * Dos cosas tienen que pasar UNA sola vez, en orden, antes de que la app
     * renderice cualquier ruta:
     *
     * 1. instance.initialize() -- desde msal-browser 3.x la instancia ya no
     *    se auto-inicializa; sin esto cualquier llamada falla con
     *    "uninitialized_public_client_application".
     * 2. handleRedirectObservable() -- procesa la respuesta de Azure AD si la
     *    URL actual es un regreso de loginRedirect(), y limpia el flag
     *    interno "interaccion en curso". Se hace ACA (centralizado, antes del
     *    bootstrap) y no dentro de un componente de ruta: si se llama mas
     *    tarde, el MsalInterceptor puede intentar conseguir un token para
     *    otra llamada HTTP mientras el redirect original todavia se esta
     *    procesando, y esa segunda interaccion choca con la primera
     *    (BrowserAuthError: interaction_in_progress).
     *
     * navigateToLoginRequestUrl: false es imprescindible. Por defecto, al
     * volver de Microsoft al redirectUri (/auth/callback), MSAL NO procesa la
     * respuesta ahi: cachea el hash y hace una navegacion completa del
     * navegador de vuelta a la pagina donde se llamo a loginRedirect()
     * (/login), esperando procesarla alla. Resultado visible: el login
     * "funciona" pero la app termina de vuelta en /login. Con esta opcion en
     * false, la respuesta se procesa en /auth/callback y la unica navegacion
     * posterior es la nuestra, hacia /dashboard.
     */
    {
      provide: APP_INITIALIZER,
      useFactory: (msalService: MsalService) => () =>
        msalService.instance.initialize().then(() =>
          firstValueFrom(
            msalService.handleRedirectObservable({ navigateToLoginRequestUrl: false }),
          ).then((result) => {
            if (result?.account) {
              msalService.instance.setActiveAccount(result.account);
            } else if (!msalService.instance.getActiveAccount()) {
              const accounts = msalService.instance.getAllAccounts();
              if (accounts.length > 0) {
                msalService.instance.setActiveAccount(accounts[0]);
              }
            }
          }),
        ),
      deps: [MsalService],
      multi: true,
    },
    MsalService,
    MsalGuard,
    MsalBroadcastService,
  ],
};
