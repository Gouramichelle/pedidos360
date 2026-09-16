import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { OrdersApi } from '../../../core/api/orders.api';
import { CatalogApi } from '../../../core/api/catalog.api';
import { Product } from '../../../core/models/product.model';
import { CreateOrderItemRequest } from '../../../core/models/order.model';
import { CurrentUserService } from '../../../core/auth/current-user.service';

interface LineaCarrito extends CreateOrderItemRequest {
  nombreProducto: string;
}

@Component({
  selector: 'app-order-create',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './order-create.component.html',
  styleUrl: './order-create.component.scss',
})
export class OrderCreateComponent implements OnInit {
  productos: Product[] = [];
  carrito: LineaCarrito[] = [];
  productoSeleccionadoId: number | null = null;
  cantidad = 1;
  guardando = false;
  error = '';

  constructor(
    private catalogApi: CatalogApi,
    private ordersApi: OrdersApi,
    private router: Router,
    public currentUser: CurrentUserService,
  ) {}

  ngOnInit(): void {
    this.catalogApi.list().subscribe({
      next: (productos) => (this.productos = productos),
      error: () => (this.error = 'No se pudo cargar el catalogo.'),
    });
  }

  agregarAlCarrito(): void {
    const producto = this.productos.find((p) => p.id === this.productoSeleccionadoId);
    if (!producto || this.cantidad < 1) return;

    this.carrito.push({
      productId: producto.id,
      productSku: producto.sku,
      qty: this.cantidad,
      price: producto.price,
      nombreProducto: producto.name,
    });
    this.productoSeleccionadoId = null;
    this.cantidad = 1;
  }

  quitarDelCarrito(index: number): void {
    this.carrito.splice(index, 1);
  }

  get total(): number {
    return this.carrito.reduce((sum, l) => sum + l.price * l.qty, 0);
  }

  confirmar(): void {
    if (this.carrito.length === 0) return;
    this.guardando = true;
    this.error = '';

    this.ordersApi
      .create({
        customerId: this.currentUser.username,
        items: this.carrito.map(({ productId, productSku, qty, price }) => ({
          productId,
          productSku,
          qty,
          price,
        })),
      })
      .subscribe({
        next: (order) => this.router.navigate(['/orders', order.id]),
        error: () => {
          this.error = 'No se pudo crear el pedido.';
          this.guardando = false;
        },
      });
  }
}
