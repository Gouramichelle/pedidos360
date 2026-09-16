import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { OrdersApi } from '../../../core/api/orders.api';
import { Order } from '../../../core/models/order.model';
import { CurrentUserService } from '../../../core/auth/current-user.service';
import { OrderStatusBadgeComponent } from '../order-status-badge/order-status-badge.component';

@Component({
  selector: 'app-order-list',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, OrderStatusBadgeComponent],
  templateUrl: './order-list.component.html',
  styleUrl: './order-list.component.scss',
})
export class OrderListComponent implements OnInit {
  orders: Order[] = [];
  loading = true;
  error = '';
  filtroEstado = '';

  constructor(
    private ordersApi: OrdersApi,
    public currentUser: CurrentUserService,
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  cargar(): void {
    this.loading = true;
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

  get filtrados(): Order[] {
    if (!this.filtroEstado) return this.orders;
    return this.orders.filter((o) => o.status === this.filtroEstado);
  }

  puedeCrear(): boolean {
    return this.currentUser.hasRole('Cliente', 'Operador');
  }
}
