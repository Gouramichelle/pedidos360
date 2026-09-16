import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OrderStatus } from '../../../core/models/order.model';

@Component({
  selector: 'app-order-status-badge',
  standalone: true,
  imports: [CommonModule],
  template: `<span class="badge" [ngClass]="status.toLowerCase()">{{ status }}</span>`,
  styles: [`
    .badge {
      display: inline-block;
      padding: 0.2rem 0.65rem;
      border-radius: 999px;
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      color: #fff;
    }
    .creado { background: #6b7280; }
    .aceptado { background: #2563eb; }
    .en_preparacion { background: #d97706; }
    .despachado { background: #7c3aed; }
    .entregado { background: #16a34a; }
    .cancelado { background: #dc2626; }
  `],
})
export class OrderStatusBadgeComponent {
  @Input({ required: true }) status!: OrderStatus;
}
