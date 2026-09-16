package cl.pedidos360.orders.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.pedidos360.orders.domain.Order;
import cl.pedidos360.orders.domain.OrderStatus;
import cl.pedidos360.orders.web.OrderDtos.ChangeStatusRequest;
import cl.pedidos360.orders.web.OrderDtos.CreateOrderRequest;
import cl.pedidos360.orders.web.OrderDtos.OrderResponse;
import jakarta.validation.Valid;

/**
 * Cliente ve solo sus propios pedidos; Operador y Admin ven todos.
 * Crear un pedido: Cliente u Operador. Cambiar de estado: Operador o Admin.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /** Admin y Operador ven y operan sobre todos los pedidos; Cliente solo sobre los suyos. */
    private boolean esStaff(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_Admin") || a.getAuthority().equals("ROLE_Operador"));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public List<OrderResponse> list(Authentication auth) {
        List<Order> orders = esStaff(auth) ? service.listar() : service.listarPorCliente(auth.getName());
        return orders.stream().map(OrderResponse::from).toList();
    }

    /**
     * El rol por si solo no alcanza: sin esta comprobacion un Cliente podia
     * leer cualquier pedido cambiando el id en la URL, y el filtro del listado
     * quedaba en algo cosmetico.
     *
     * Se responde 404 y no 403 a proposito, para no revelar que el pedido
     * existe y pertenece a otra persona.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public OrderResponse get(@PathVariable Long id, Authentication auth) {
        Order order = service.buscar(id);
        if (!esStaff(auth) && !order.getCustomerId().equals(auth.getName())) {
            throw new NotFoundException("No existe el pedido " + id);
        }
        return OrderResponse.from(order);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest body,
            @RequestHeader("Authorization") String authorization,
            Authentication auth) {
        // Un Cliente solo puede pedir para si mismo. Admin y Operador pueden
        // tomar el pedido en nombre de otro, y si no lo indican queda a su
        // nombre.
        boolean puedeElegirCliente = esStaff(auth);
        String customerId = puedeElegirCliente && body.customerId() != null && !body.customerId().isBlank()
                ? body.customerId().trim()
                : auth.getName();

        Order created = service.crear(body, customerId, authorization, auth.getName());
        return ResponseEntity.status(201).body(OrderResponse.from(created));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('Admin', 'Operador')")
    public OrderResponse changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ChangeStatusRequest body,
            @RequestHeader("Authorization") String authorization,
            Authentication auth) {
        OrderStatus nuevoEstado = parseEstado(body.status());
        return OrderResponse.from(service.cambiarEstado(id, nuevoEstado, authorization, auth.getName()));
    }

    private OrderStatus parseEstado(String raw) {
        try {
            return OrderStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Estado desconocido: " + raw
                    + ". Valores validos: " + java.util.Arrays.toString(OrderStatus.values()));
        }
    }
}
