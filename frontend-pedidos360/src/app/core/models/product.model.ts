export interface Product {
  id: number;
  sku: string;
  name: string;
  price: number;
  stock: number;
}

export interface ProductRequest {
  sku: string;
  name: string;
  price: number;
  stock: number;
}
