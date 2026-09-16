import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { OrdersApi } from '../../core/api/orders.api';
import { Order } from '../../core/models/order.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  orders: Order[] = [];
  loading = true;
  error = '';

  constructor(
    public currentUser: CurrentUserService,
    private ordersApi: OrdersApi,
  ) {}

  ngOnInit(): void {
    this.ordersApi.list().subscribe({
      next: (orders) => {
        this.orders = orders;
        this.loading = false;
      },
      error: () => {
        this.error = 'No se pudieron cargar los pedidos.';
        this.loading = false;
      },
    });
  }

  get pendientes(): Order[] {
    return this.orders.filter((o) => !['ENTREGADO', 'CANCELADO'].includes(o.status));
  }

  get totalVentas(): number {
    return this.orders
      .filter((o) => o.status === 'ENTREGADO')
      .reduce((sum, o) => sum + o.total, 0);
  }
}
