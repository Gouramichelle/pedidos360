import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AppRole, CurrentUserService, TODOS_LOS_ROLES } from '../../auth/current-user.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  readonly todosLosRoles = TODOS_LOS_ROLES;

  constructor(
    public currentUser: CurrentUserService,
    private router: Router,
  ) {}

  /**
   * El selector solo tiene sentido si el usuario tiene mas de un rol: con uno
   * solo no hay nada que simular, ya esta viendo lo que le corresponde.
   */
  get puedeSimular(): boolean {
    return this.currentUser.rolesReales.length > 1;
  }

  cambiarVista(valor: string): void {
    const rol = valor === '' ? null : (valor as AppRole);
    this.currentUser.setVistaRol(rol);

    // Si la pantalla actual deja de estar permitida para el rol simulado, el
    // guard la rechazaria recien en la proxima navegacion y quedaria a la
    // vista contenido que ese rol no deberia ver. Se vuelve al dashboard, que
    // es accesible para todos.
    this.router.navigateByUrl('/dashboard');
  }

  logout(): void {
    this.currentUser.logout();
  }
}
