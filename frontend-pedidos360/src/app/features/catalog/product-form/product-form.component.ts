import { Component, EventEmitter, Input, Output, OnChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Product, ProductRequest } from '../../../core/models/product.model';

@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './product-form.component.html',
  styleUrl: './product-form.component.scss',
})
export class ProductFormComponent implements OnChanges {
  @Input() product: Product | null = null;
  @Output() save = new EventEmitter<ProductRequest>();
  @Output() cancel = new EventEmitter<void>();

  form: ProductRequest = { sku: '', name: '', price: 0, stock: 0 };

  ngOnChanges(): void {
    this.form = this.product
      ? { sku: this.product.sku, name: this.product.name, price: this.product.price, stock: this.product.stock }
      : { sku: '', name: '', price: 0, stock: 0 };
  }

  submit(): void {
    this.save.emit(this.form);
  }
}
