import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AppRole, CurrentUserService } from '../auth/current-user.service';

/**
 * Guard de autorizacion por rol. Se usa DESPUES de MsalGuard en la cadena de
 * `canActivate` de cada ruta: MsalGuard garantiza que hay sesion, este guard
 * garantiza que el rol de esa sesion puede ver la pantalla.
 *
 * Uso: canActivate: [MsalGuard, roleGuard(['Admin'])]
 */
export function roleGuard(allowedRoles: AppRole[]): CanActivateFn {
  return () => {
    const currentUser = inject(CurrentUserService);
    const router = inject(Router);

    if (currentUser.hasRole(...allowedRoles)) {
      return true;
    }
    return router.createUrlTree(['/dashboard']);
  };
}
