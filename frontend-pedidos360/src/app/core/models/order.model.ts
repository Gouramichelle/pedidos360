export type OrderStatus =
  | 'CREADO'
  | 'ACEPTADO'
  | 'EN_PREPARACION'
  | 'DESPACHADO'
  | 'ENTREGADO'
  | 'CANCELADO';

export interface OrderItem {
  productId: number;
  productSku: string;
  qty: number;
  price: number;
  subtotal: number;
}

export interface Order {
  id: number;
  customerId: string;
  status: OrderStatus;
  total: number;
  items: OrderItem[];
  createdAt: string;
  acceptedAt?: string;
  preparingAt?: string;
  dispatchedAt?: string;
  deliveredAt?: string;
  cancelledAt?: string;
}

/**
 * Sin precio ni SKU: los resuelve el backend consultando al catalogo. Y sin
 * customerId, porque el pedido queda a nombre del usuario del token (solo
 * Admin y Operador pueden indicar otro, cosa que esta pantalla no hace).
 */
export interface CreateOrderItemRequest {
  productId: number;
  qty: number;
}

export interface CreateOrderRequest {
  items: CreateOrderItemRequest[];
}

/** Transiciones validas desde cada estado -- debe reflejar OrderStatus.java en ms-orders. */
export const NEXT_STATUSES: Record<OrderStatus, OrderStatus[]> = {
  CREADO: ['ACEPTADO', 'CANCELADO'],
  ACEPTADO: ['EN_PREPARACION', 'CANCELADO'],
  EN_PREPARACION: ['DESPACHADO', 'CANCELADO'],
  DESPACHADO: ['ENTREGADO'],
  ENTREGADO: [],
  CANCELADO: [],
};
