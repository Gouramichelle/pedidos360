import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { CatalogApi } from '../../core/api/catalog.api';
import { Product, ProductRequest } from '../../core/models/product.model';
import { CurrentUserService } from '../../core/auth/current-user.service';
import { ProductCardComponent } from './product-card/product-card.component';
import { ProductFormComponent } from './product-form/product-form.component';

@Component({
  selector: 'app-catalog',
  standalone: true,
  imports: [CommonModule, ProductCardComponent, ProductFormComponent],
  templateUrl: './catalog.component.html',
  styleUrl: './catalog.component.scss',
})
export class CatalogComponent implements OnInit {
  productos: Product[] = [];
  loading = true;
  error = '';
  mostrandoFormulario = false;
  productoEnEdicion: Product | null = null;

  constructor(
    private catalogApi: CatalogApi,
    public currentUser: CurrentUserService,
  ) {}

  ngOnInit(): void {
    this.cargar();
  }

  private cargar(): void {
    this.loading = true;
    this.catalogApi.list().subscribe({
      next: (productos) => {
        this.productos = productos;
        this.loading = false;
      },
      error: () => {
        this.error = 'No se pudo cargar el catalogo.';
        this.loading = false;
      },
    });
  }

  puedeEditar(): boolean {
    return this.currentUser.hasRole('Admin');
  }

  nuevoProducto(): void {
    this.productoEnEdicion = null;
    this.mostrandoFormulario = true;
  }

  editar(producto: Product): void {
    this.productoEnEdicion = producto;
    this.mostrandoFormulario = true;
  }

  eliminar(producto: Product): void {
    if (!confirm(`¿Eliminar ${producto.name}?`)) return;
    this.catalogApi.delete(producto.id).subscribe({
      next: () => this.cargar(),
      error: () => (this.error = 'No se pudo eliminar el producto.'),
    });
  }

  guardar(request: ProductRequest): void {
    const accion = this.productoEnEdicion
      ? this.catalogApi.update(this.productoEnEdicion.id, request)
      : this.catalogApi.create(request);

    accion.subscribe({
      next: () => {
        this.mostrandoFormulario = false;
        this.cargar();
      },
      error: () => (this.error = 'No se pudo guardar el producto.'),
    });
  }

  cancelarFormulario(): void {
    this.mostrandoFormulario = false;
  }
}
