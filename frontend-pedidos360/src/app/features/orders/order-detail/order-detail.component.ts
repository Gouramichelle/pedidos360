import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OrdersApi } from '../../../core/api/orders.api';
import { NEXT_STATUSES, Order, OrderStatus } from '../../../core/models/order.model';
import { CurrentUserService } from '../../../core/auth/current-user.service';
import { OrderStatusBadgeComponent } from '../order-status-badge/order-status-badge.component';

@Component({
  selector: 'app-order-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, OrderStatusBadgeComponent],
  templateUrl: './order-detail.component.html',
  styleUrl: './order-detail.component.scss',
})
export class OrderDetailComponent implements OnInit {
  order: Order | null = null;
  loading = true;
  error = '';
  cambiandoEstado = false;

  constructor(
    private route: ActivatedRoute,
    private ordersApi: OrdersApi,
    public currentUser: CurrentUserService,
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.ordersApi.get(id).subscribe({
      next: (order) => {
        this.order = order;
        this.loading = false;
      },
      error: () => {
        this.error = 'No se pudo cargar el pedido.';
        this.loading = false;
      },
    });
  }

  get siguientesEstados(): OrderStatus[] {
    return this.order ? NEXT_STATUSES[this.order.status] : [];
  }

  puedeCambiarEstado(): boolean {
    return this.currentUser.hasRole('Admin', 'Operador');
  }

  cambiarEstado(nuevoEstado: OrderStatus): void {
    if (!this.order) return;
    this.cambiandoEstado = true;
    this.error = '';
    this.ordersApi.changeStatus(this.order.id, nuevoEstado).subscribe({
      next: (order) => {
        this.order = order;
        this.cambiandoEstado = false;
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'No se pudo cambiar el estado del pedido.';
        this.cambiandoEstado = false;
      },
    });
  }
}
