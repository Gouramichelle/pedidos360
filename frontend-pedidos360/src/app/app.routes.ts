import { Routes } from '@angular/router';
import { ShellComponent } from './core/layout/shell/shell.component';
import { LoginComponent } from './features/login/login.component';
import { AuthCallbackComponent } from './features/auth-callback/auth-callback.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { OrderListComponent } from './features/orders/order-list/order-list.component';
import { OrderDetailComponent } from './features/orders/order-detail/order-detail.component';
import { OrderCreateComponent } from './features/orders/order-create/order-create.component';
import { CatalogComponent } from './features/catalog/catalog.component';
import { ReportsComponent } from './features/reports/reports.component';
import { AuditComponent } from './features/audit/audit.component';
import { roleGuard } from './core/guards/role.guard';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'auth/callback', component: AuthCallbackComponent },

  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: DashboardComponent },

      // Roles: Admin, Operador, Cliente (con distintos niveles de acceso a los datos)
      { path: 'orders', component: OrderListComponent },
      {
        path: 'orders/nuevo',
        component: OrderCreateComponent,
        canActivate: [roleGuard(['Cliente', 'Operador'])],
      },
      { path: 'orders/:id', component: OrderDetailComponent },

      // Roles: Admin, Operador
      {
        path: 'catalog',
        component: CatalogComponent,
        canActivate: [roleGuard(['Admin', 'Operador'])],
      },

      // Roles: Admin
      {
        path: 'reports',
        component: ReportsComponent,
        canActivate: [roleGuard(['Admin'])],
      },

      // Roles: Admin, Auditor
      {
        path: 'audit',
        component: AuditComponent,
        canActivate: [roleGuard(['Admin', 'Auditor'])],
      },
    ],
  },

  { path: '**', redirectTo: 'dashboard' },
];
