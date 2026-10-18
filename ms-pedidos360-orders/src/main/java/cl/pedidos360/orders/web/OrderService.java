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
import cl.pedidos360.orders.messaging.InvoiceCommandPublisher;
import cl.pedidos360.orders.messaging.KitchenCommandPublisher;
import cl.pedidos360.orders.messaging.OrderEventPublisher;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final CatalogClient catalogClient;
    private final OrderEventPublisher eventPublisher;
    private final EmailCommandPublisher emailPublisher;
    private final KitchenCommandPublisher kitchenPublisher;
    private final InvoiceCommandPublisher invoicePublisher;

    public OrderService(
            OrderRepository repository,
            CatalogClient catalogClient,
            OrderEventPublisher eventPublisher,
            EmailCommandPublisher emailPublisher,
            KitchenCommandPublisher kitchenPublisher,
            InvoiceCommandPublisher invoicePublisher) {
        this.repository = repository;
        this.catalogClient = catalogClient;
        this.eventPublisher = eventPublisher;
        this.emailPublisher = emailPublisher;
        this.kitchenPublisher = kitchenPublisher;
        this.invoicePublisher = invoicePublisher;
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
    /**
     * customerId llega ya resuelto por el controller a partir del token; el
     * precio y el SKU de cada item se leen de ms-catalog. Nada del cuerpo de
     * la peticion influye en a quien pertenece el pedido ni en cuanto cuesta.
     */
    public Order crear(OrderDtos.CreateOrderRequest request, String customerId, String bearerToken, String actor) {
        Order order = new Order(customerId);
        request.items().forEach(i -> {
            CatalogClient.Product producto = catalogClient.obtenerProducto(i.productId(), bearerToken);
            if (producto == null) {
                throw new NotFoundException("No existe el producto " + i.productId());
            }
            order.addItem(new OrderItem(producto.id(), producto.sku(), i.qty(), producto.price()));
        });
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

    /**
     * Un cambio de estado produce un evento y uno o mas comandos, todos con el
     * mismo correlationId para poder seguir la operacion de punta a punta.
     *
     * El evento de Kafka se publica siempre: es la fuente de verdad de
     * auditoria y reportes, y a esos servicios les interesa toda transicion.
     *
     * Los comandos se reparten asi: al CREAR el pedido se dispara el flujo
     * completo -- notificacion, ticket de cocina y documento de cobro -- de
     * modo que una sola accion del usuario ejercita los tres dominios y sus
     * tres colas. Los cambios de estado posteriores solo notifican al cliente,
     * porque ni la cocina ni la facturacion necesitan enterarse dos veces del
     * mismo pedido.
     *
     * CREADO identifica la creacion sin ambiguedad: la maquina de estados solo
     * avanza, asi que ningun pedido vuelve a ese estado.
     */
    private void publicarEventos(Order order, OrderStatus estadoAnterior, String actor) {
        String trace = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        eventPublisher.publicarCambioEstado(order, estadoAnterior, actor, trace, correlationId);

        emailPublisher.notificarCambioEstado(order, trace, correlationId);

        if (order.getStatus() == OrderStatus.CREADO) {
            kitchenPublisher.emitirTicket(order, trace, correlationId);
            invoicePublisher.generarDocumento(order, trace, correlationId);
        }
    }
}
