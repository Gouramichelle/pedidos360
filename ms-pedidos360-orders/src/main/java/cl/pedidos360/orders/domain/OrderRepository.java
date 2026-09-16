package cl.pedidos360.orders.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * findById heredado de JpaRepository no trae los items, y como
     * open-in-view esta deshabilitado la sesion ya esta cerrada cuando el
     * controller arma la respuesta: recorrer order.getItems() lanzaba
     * LazyInitializationException. Este finder los trae en la misma consulta.
     */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    List<Order> findAllByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "items")
    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    @EntityGraph(attributePaths = "items")
    List<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status);
}
