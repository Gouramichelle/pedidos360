import { Injectable } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { AccountInfo } from '@azure/msal-browser';

export type AppRole = 'Admin' | 'Operador' | 'Cliente' | 'Auditor';

export const TODOS_LOS_ROLES: AppRole[] = ['Admin', 'Operador', 'Cliente', 'Auditor'];

/** Clave de sessionStorage donde se recuerda la vista simulada entre navegaciones. */
const VISTA_ROL_KEY = 'pedidos360.vistaRol';

/**
 * Lee roles y username desde los claims del id_token de la cuenta activa.
 * MSAL ya trae los claims decodificados en account.idTokenClaims -- no hace
 * falta parsear el JWT a mano.
 *
 * Ademas maneja la "vista por rol": una simulacion SOLO de interfaz para la
 * demo, que permite ver como se veria la app con un rol distinto sin cerrar
 * sesion. Es importante entender su alcance: el access token que se manda al
 * backend NO cambia, sigue siendo el real con todos los roles del usuario.
 * O sea que esto sirve para mostrar la UI de cada perfil, pero NO prueba la
 * seguridad -- para eso hay que iniciar sesion con un usuario que realmente
 * tenga un solo rol asignado en Azure AD.
 */
@Injectable({ providedIn: 'root' })
export class CurrentUserService {
  private vistaRolActual: AppRole | null = null;

  constructor(private msalService: MsalService) {
    const guardado = sessionStorage.getItem(VISTA_ROL_KEY) as AppRole | null;
    if (guardado && TODOS_LOS_ROLES.includes(guardado)) {
      this.vistaRolActual = guardado;
    }
  }

  get account(): AccountInfo | null {
    return this.msalService.instance.getActiveAccount();
  }

  get username(): string {
    const claims = this.account?.idTokenClaims as Record<string, unknown> | undefined;
    return (claims?.['preferred_username'] as string) ?? this.account?.username ?? '';
  }

  /** Los roles que realmente vienen en el token. No los altera la simulacion. */
  get rolesReales(): AppRole[] {
    const claims = this.account?.idTokenClaims as Record<string, unknown> | undefined;
    return (claims?.['roles'] as AppRole[]) ?? [];
  }

  /**
   * Los roles que la interfaz debe considerar: si hay una vista simulada
   * activa, solo ese rol; si no, los reales.
   */
  get roles(): AppRole[] {
    return this.vistaRolActual ? [this.vistaRolActual] : this.rolesReales;
  }

  get vistaRol(): AppRole | null {
    return this.vistaRolActual;
  }

  get simulando(): boolean {
    return this.vistaRolActual !== null;
  }

  /** Pasar null vuelve a la vista real (todos los roles del usuario). */
  setVistaRol(rol: AppRole | null): void {
    this.vistaRolActual = rol;
    if (rol) {
      sessionStorage.setItem(VISTA_ROL_KEY, rol);
    } else {
      sessionStorage.removeItem(VISTA_ROL_KEY);
    }
  }

  hasRole(...roles: AppRole[]): boolean {
    const mine = this.roles;
    return roles.some((r) => mine.includes(r));
  }

  isLoggedIn(): boolean {
    return this.account !== null;
  }

  logout(): void {
    this.setVistaRol(null);
    this.msalService.logoutRedirect();
  }
}
