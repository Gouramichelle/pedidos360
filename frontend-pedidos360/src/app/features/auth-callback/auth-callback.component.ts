import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';

/**
 * MSAL redirige aqui tras un login exitoso (ver Caso 0: "/auth/callback").
 * El redirect ya fue procesado de forma centralizada por el APP_INITIALIZER
 * en app.config.ts antes de que esta pantalla llegue a renderizar, asi que
 * aca solo hace falta navegar al dashboard.
 */
@Component({
  selector: 'app-auth-callback',
  standalone: true,
  template: `<p class="callback">Completando el inicio de sesion...</p>`,
  styles: [`.callback { padding: 2rem; text-align: center; color: #6b7280; }`],
})
export class AuthCallbackComponent implements OnInit {
  constructor(private router: Router) {}

  ngOnInit(): void {
    this.router.navigateByUrl('/dashboard');
  }
}
