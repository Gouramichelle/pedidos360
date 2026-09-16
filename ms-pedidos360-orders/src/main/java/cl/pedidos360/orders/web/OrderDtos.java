package cl.pedidos360.orders.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import cl.pedidos360.orders.domain.Order;
import cl.pedidos360.orders.domain.OrderItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class OrderDtos {

    /**
     * Sin precio ni SKU a proposito: los toma el servidor desde ms-catalog.
     * Aceptarlos aqui permitiria fijar el precio desde el cliente.
     */
    public record OrderItemRequest(
            @NotNull Long productId,
            @NotNull @Min(1) Integer qty) {
    }

    /**
     * customerId es opcional y solo se respeta para Admin y Operador, que
     * pueden tomar un pedido en nombre de un cliente. Para un Cliente se
     * ignora y se usa siempre su propia identidad del token: si no, cualquiera
     * podria crear pedidos a nombre de otra persona.
     */
    public record CreateOrderRequest(
            String customerId,
            @NotEmpty @Valid List<OrderItemRequest> items) {
    }

    public record ChangeStatusRequest(@NotNull String status) {
    }

    public record OrderItemResponse(Long productId, String productSku, Integer qty, BigDecimal price, BigDecimal subtotal) {
        static OrderItemResponse from(OrderItem item) {
            return new OrderItemResponse(item.getProductId(), item.getProductSku(), item.getQty(), item.getPrice(), item.subtotal());
        }
    }

    public record OrderResponse(
            Long id,
            String customerId,
            String status,
            BigDecimal total,
            List<OrderItemResponse> items,
            Instant createdAt,
            Instant acceptedAt,
            Instant preparingAt,
            Instant dispatchedAt,
            Instant deliveredAt,
            Instant cancelledAt) {

        public static OrderResponse from(Order order) {
            return new OrderResponse(
                    order.getId(),
                    order.getCustomerId(),
                    order.getStatus().name(),
                    order.total(),
                    order.getItems().stream().map(OrderItemResponse::from).toList(),
                    order.getCreatedAt(),
                    order.getAcceptedAt(),
                    order.getPreparingAt(),
                    order.getDispatchedAt(),
                    order.getDeliveredAt(),
                    order.getCancelledAt());
        }
    }
}
