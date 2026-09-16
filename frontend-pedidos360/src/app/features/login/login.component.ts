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

  login(): void {
    // Hay que pedir explicitamente el scope de nuestra API. Sin esto, MSAL
    // solo pide los scopes OIDC por defecto (openid/profile/email) y el
    // access token que devuelve Azure AD es para Microsoft Graph, no para
    // Pedidos360: llega sin el claim "roles" y con una audiencia que ni el
    // API Gateway ni los microservicios reconocen, asi que toda llamada a la
    // API responde 401.
    this.msalService.loginRedirect({ scopes: environment.apiScopes });
  }
}
