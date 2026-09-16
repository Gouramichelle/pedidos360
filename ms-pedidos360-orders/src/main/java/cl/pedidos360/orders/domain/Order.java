package cl.pedidos360.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 120)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "preparing_at")
    private Instant preparingAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Order() {
    }

    public Order(String customerId) {
        this.customerId = customerId;
        this.status = OrderStatus.CREADO;
        this.createdAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }

    public BigDecimal total() {
        return items.stream().map(OrderItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Aplica una transicion de estado o falla si no es valida.
     * Devuelve el estado anterior para poder publicarlo en el evento.
     */
    public OrderStatus cambiarEstado(OrderStatus destino) {
        if (this.status == destino) {
            throw new IllegalStateException("El pedido " + id + " ya esta en estado " + destino);
        }
        if (!this.status.puedePasarA(destino)) {
            throw new IllegalStateException(
                    "Transicion invalida: no se puede pasar de " + this.status + " a " + destino
                            + ". Desde " + this.status + " solo se puede ir a " + this.status.siguientesPosibles());
        }
        OrderStatus anterior = this.status;
        this.status = destino;
        Instant ahora = Instant.now();
        switch (destino) {
            case ACEPTADO -> this.acceptedAt = ahora;
            case EN_PREPARACION -> this.preparingAt = ahora;
            case DESPACHADO -> this.dispatchedAt = ahora;
            case ENTREGADO -> this.deliveredAt = ahora;
            case CANCELADO -> this.cancelledAt = ahora;
            default -> {
            }
        }
        return anterior;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getPreparingAt() {
        return preparingAt;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
