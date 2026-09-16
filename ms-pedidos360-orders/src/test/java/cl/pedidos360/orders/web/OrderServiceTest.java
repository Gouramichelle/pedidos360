package cl.pedidos360.orders.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import cl.pedidos360.orders.client.CatalogClient;
import cl.pedidos360.orders.domain.Order;
import cl.pedidos360.orders.domain.OrderItem;
import cl.pedidos360.orders.domain.OrderRepository;
import cl.pedidos360.orders.domain.OrderStatus;
import cl.pedidos360.orders.messaging.EmailCommandPublisher;
import cl.pedidos360.orders.messaging.OrderEventPublisher;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    OrderRepository repository;
    @Mock
    CatalogClient catalogClient;
    @Mock
    OrderEventPublisher eventPublisher;
    @Mock
    EmailCommandPublisher emailPublisher;

    OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(repository, catalogClient, eventPublisher, emailPublisher);
        // lenient: no todos los tests llegan a guardar (algunos fallan antes por una
        // transicion de estado invalida), y eso no deberia hacerlos fallar por un
        // stub sin usar.
        org.mockito.Mockito.lenient().when(repository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void aceptarUnPedidoDescuentaStockDeCadaItemEnCatalogo() {
        Order order = new Order("cliente-1");
        order.addItem(new OrderItem(10L, "SKU-A", 3, new BigDecimal("100.00")));
        order.addItem(new OrderItem(20L, "SKU-B", 1, new BigDecimal("50.00")));
        when(repository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        service.cambiarEstado(1L, OrderStatus.ACEPTADO, "Bearer token-de-prueba", "operador@pedidos360.cl");

        verify(catalogClient).decreaseStock(10L, 3, "Bearer token-de-prueba");
        verify(catalogClient).decreaseStock(20L, 1, "Bearer token-de-prueba");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACEPTADO);
    }

    @Test
    void siCatalogoRechazaElDescuentoElPedidoNoCambiaDeEstado() {
        Order order = new Order("cliente-1");
        order.addItem(new OrderItem(10L, "SKU-A", 999, new BigDecimal("100.00")));
        when(repository.findWithItemsById(1L)).thenReturn(Optional.of(order));
        org.mockito.Mockito.doThrow(new IllegalStateException("Stock insuficiente"))
                .when(catalogClient).decreaseStock(any(), anyInt(), anyString());

        assertThatThrownBy(() -> service.cambiarEstado(1L, OrderStatus.ACEPTADO, "Bearer x", "operador@pedidos360.cl"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREADO);
        verify(eventPublisher, never()).publicarCambioEstado(any(), any(), any(), anyString(), anyString());
    }

    @Test
    void unNoDespachoSinAceptarNuncaLlegaAlPublicador() {
        Order order = new Order("cliente-1");
        when(repository.findWithItemsById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cambiarEstado(1L, OrderStatus.DESPACHADO, "Bearer x", "operador@pedidos360.cl"))
                .isInstanceOf(IllegalStateException.class);

        verify(catalogClient, never()).decreaseStock(any(), anyInt(), anyString());
        verify(eventPublisher, never()).publicarCambioEstado(any(), any(), any(), anyString(), anyString());
    }

    @Test
    void crearUnPedidoPublicaElEventoDeCreacionYElComandoDeEmail() {
        service.crear(new OrderDtos.CreateOrderRequest("cliente-1",
                List.of(new OrderDtos.OrderItemRequest(1L, "SKU-A", 2, new BigDecimal("10.00")))),
                "cliente@pedidos360.cl");

        verify(eventPublisher, times(1))
                .publicarCambioEstado(any(Order.class), eq(OrderStatus.CREADO), eq("cliente@pedidos360.cl"), anyString(), anyString());
        verify(emailPublisher, times(1)).notificarCambioEstado(any(Order.class), anyString(), anyString());
    }

    @Test
    void buscarUnPedidoInexistenteLanzaNotFound() {
        when(repository.findWithItemsById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.buscar(99L)).isInstanceOf(NotFoundException.class);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
