package cl.pedidos360.report.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Proyeccion de solo lectura del estado actual de cada pedido, construida a
 * partir de orders.events. No es la fuente de verdad (esa es ms-orders) --
 * es una vista materializada pensada para agregaciones rapidas sin pegarle a
 * la base transaccional de pedidos.
 */
@Entity
@Table(name = "order_snapshots")
public class OrderSnapshot {

    @Id
    private Long orderId;

    @Column(nullable = false, length = 120)
    private String customerId;

    @Column(nullable = false, length = 20)
    private String currentStatus;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant deliveredAt;

    protected OrderSnapshot() {
    }

    public OrderSnapshot(Long orderId, String customerId, String currentStatus, BigDecimal total, Instant createdAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.currentStatus = currentStatus;
        this.total = total;
        this.createdAt = createdAt;
    }

    public void aplicarEvento(String newStatus, BigDecimal total, Instant occurredAt) {
        this.currentStatus = newStatus;
        this.total = total;
        if ("ENTREGADO".equals(newStatus)) {
            this.deliveredAt = occurredAt;
        }
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }
}
