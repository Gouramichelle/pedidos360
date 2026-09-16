package cl.pedidos360.orders.web;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import cl.pedidos360.orders.client.CatalogClient;
import cl.pedidos360.orders.domain.Order;
import cl.pedidos360.orders.domain.OrderItem;
import cl.pedidos360.orders.domain.OrderRepository;
import cl.pedidos360.orders.domain.OrderStatus;
import cl.pedidos360.orders.messaging.EmailCommandPublisher;
import cl.pedidos360.orders.messaging.OrderEventPublisher;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final CatalogClient catalogClient;
    private final OrderEventPublisher eventPublisher;
    private final EmailCommandPublisher emailPublisher;

    public OrderService(
            OrderRepository repository,
            CatalogClient catalogClient,
            OrderEventPublisher eventPublisher,
            EmailCommandPublisher emailPublisher) {
        this.repository = repository;
        this.catalogClient = catalogClient;
        this.eventPublisher = eventPublisher;
        this.emailPublisher = emailPublisher;
    }

    public List<Order> listar() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public List<Order> listarPorCliente(String customerId) {
        return repository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    public Order buscar(Long id) {
        return repository.findWithItemsById(id)
                .orElseThrow(() -> new cl.pedidos360.orders.web.NotFoundException("No existe el pedido " + id));
    }

    @Transactional
    public Order crear(OrderDtos.CreateOrderRequest request, String actor) {
        Order order = new Order(request.customerId());
        request.items().forEach(i -> order.addItem(new OrderItem(i.productId(), i.productSku(), i.qty(), i.price())));
        Order guardado = repository.save(order);
        publicarEventos(guardado, OrderStatus.CREADO, actor);
        return guardado;
    }

    /**
     * Cambia el estado del pedido. Al aceptar, descuenta stock en ms-catalog
     * ANTES de confirmar la transicion: si el catalogo rechaza por falta de
     * stock, el pedido se queda en CREADO y no queda en un estado inconsistente.
     */
    @Transactional
    public Order cambiarEstado(Long id, OrderStatus nuevoEstado, String bearerToken, String actor) {
        Order order = buscar(id);

        if (nuevoEstado == OrderStatus.ACEPTADO) {
            order.getItems().forEach(item ->
                    catalogClient.decreaseStock(item.getProductId(), item.getQty(), bearerToken));
        }

        OrderStatus anterior = order.cambiarEstado(nuevoEstado);
        Order guardado = repository.save(order);
        publicarEventos(guardado, anterior, actor);
        return guardado;
    }

    private void publicarEventos(Order order, OrderStatus estadoAnterior, String actor) {
        String trace = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();
        eventPublisher.publicarCambioEstado(order, estadoAnterior, actor, trace, correlationId);
        emailPublisher.notificarCambioEstado(order, trace, correlationId);
    }
}
