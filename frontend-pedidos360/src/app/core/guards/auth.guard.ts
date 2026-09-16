import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { CurrentUserService } from '../auth/current-user.service';

/**
 * A diferencia de MsalGuard (que redirige directo a Microsoft), este guard
 * manda a un usuario no autenticado a nuestra propia pantalla /login, donde
 * recien ahi el click en "Iniciar sesion con Microsoft" dispara el
 * loginRedirect. Es el flujo que describe el Caso 0 para la pantalla /login.
 */
export const authGuard: CanActivateFn = () => {
  const currentUser = inject(CurrentUserService);
  const router = inject(Router);

  if (currentUser.isLoggedIn()) {
    return true;
  }
  return router.createUrlTree(['/login']);
};
