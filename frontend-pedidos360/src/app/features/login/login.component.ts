import { Component } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-login',
  standalone: true,
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  constructor(private msalService: MsalService) {}

  /**
   * Hay que pedir explicitamente el scope de nuestra API. Sin esto, MSAL solo
   * pide los scopes OIDC por defecto y el access token que devuelve Azure AD
   * es para Microsoft Graph, no para Pedidos360: llega sin el claim "roles" y
   * con una audiencia que ni el API Gateway ni los microservicios reconocen.
   */
  login(): void {
    this.msalService.loginRedirect({ scopes: environment.apiScopes });
  }

  /**
   * Mismo flujo de autorizacion, pero con prompt=create, que es el valor
   * estandar de OpenID Connect para "quiero crear una cuenta". Azure AD lleva
   * al usuario directo al formulario de registro del flujo de autoservicio en
   * vez de a la pantalla de inicio de sesion.
   *
   * Requiere que el flujo de registro de autoservicio este habilitado en el
   * tenant y asociado a esta aplicacion; si no lo esta, Azure AD ignora el
   * parametro y muestra el inicio de sesion normal.
   */
  registrarse(): void {
    this.msalService.loginRedirect({
      scopes: environment.apiScopes,
      prompt: 'create',
    });
  }
}
