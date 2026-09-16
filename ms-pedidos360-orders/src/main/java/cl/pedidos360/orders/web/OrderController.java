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

    @GetMapping
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public List<OrderResponse> list(Authentication auth) {
        boolean esStaff = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_Admin") || a.getAuthority().equals("ROLE_Operador"));
        List<Order> orders = esStaff ? service.listar() : service.listarPorCliente(auth.getName());
        return orders.stream().map(OrderResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public OrderResponse get(@PathVariable Long id) {
        return OrderResponse.from(service.buscar(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest body, Authentication auth) {
        Order created = service.crear(body, auth.getName());
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
