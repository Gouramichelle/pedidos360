package cl.pedidos360.mqadmin.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

class RabbitAdminServiceTest {

    private AmqpAdmin amqpAdmin;
    private RabbitAdminService service;

    @BeforeEach
    void setUp() {
        amqpAdmin = mock(AmqpAdmin.class);
        service = new RabbitAdminService(amqpAdmin);
    }

    // ------------------------------------------------------------- colas

    @Test
    void creaUnaColaDurableYSinArgumentosCuandoNoSePideDlq() {
        service.crearCola("q.pruebas", true, false, null, null);

        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(captor.capture());
        Queue cola = captor.getValue();

        assertThat(cola.getName()).isEqualTo("q.pruebas");
        assertThat(cola.isDurable()).isTrue();
        assertThat(cola.isAutoDelete()).isFalse();
        assertThat(cola.getArguments()).isEmpty();
    }

    @Test
    void agregaLosArgumentosDeDeadLetteringCuandoSeIndicaUnDlx() {
        service.crearCola("q.pruebas", true, false, "cmd.dead.dlx", "pruebas.fallo");

        ArgumentCaptor<Queue> captor = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(captor.capture());

        assertThat(captor.getValue().getArguments())
                .containsEntry("x-dead-letter-exchange", "cmd.dead.dlx")
                .containsEntry("x-dead-letter-routing-key", "pruebas.fallo");
    }

    @Test
    void rechazaUnNombreDeColaVacio() {
        assertThatThrownBy(() -> service.crearCola("   ", true, false, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no puede estar vacio");
    }

    @Test
    void rechazaElPrefijoReservadoPorRabbitMQ() {
        // amq.* lo reserva el broker: declararlo cierra el canal con un 403 del
        // protocolo, asi que se corta antes de salir a la red.
        assertThatThrownBy(() -> service.crearCola("amq.propia", true, false, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reservado");
    }

    @Test
    void eliminarDevuelveFalseCuandoLaColaNoExistia() {
        // La existencia se decide por getQueueProperties, no por lo que
        // devuelva deleteQueue: el broker responde con exito igual.
        when(amqpAdmin.getQueueProperties("q.fantasma")).thenReturn(null);

        assertThat(service.eliminarCola("q.fantasma")).isFalse();
        verify(amqpAdmin, org.mockito.Mockito.never()).deleteQueue(anyString());
    }

    @Test
    void eliminarBorraLaColaCuandoSiExiste() {
        when(amqpAdmin.getQueueProperties("q.cmd.email")).thenReturn(new Properties());

        assertThat(service.eliminarCola("q.cmd.email")).isTrue();
        verify(amqpAdmin).deleteQueue("q.cmd.email");
    }

    @Test
    void obtenerDevuelveVacioCuandoElBrokerNoConoceLaCola() {
        when(amqpAdmin.getQueueProperties("q.fantasma")).thenReturn(null);
        assertThat(service.obtenerCola("q.fantasma")).isEmpty();
    }

    @Test
    void obtenerMapeaMensajesYConsumidores() {
        Properties props = new Properties();
        props.put(RabbitAdmin.QUEUE_NAME, "q.cmd.email");
        props.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, 7);
        props.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, 2);
        when(amqpAdmin.getQueueProperties("q.cmd.email")).thenReturn(props);

        Optional<ColaInfo> info = service.obtenerCola("q.cmd.email");

        assertThat(info).isPresent();
        assertThat(info.get().mensajes()).isEqualTo(7);
        assertThat(info.get().consumidores()).isEqualTo(2);
    }

    // --------------------------------------------------------- exchanges

    @Test
    void creaCadaTipoDeExchangeConLaClaseQueCorresponde() {
        service.crearExchange("x.directo", "direct", true, false);
        service.crearExchange("x.topico", "TOPIC", true, false);
        service.crearExchange("x.difusion", "FanOut", true, false);

        ArgumentCaptor<Exchange> captor = ArgumentCaptor.forClass(Exchange.class);
        verify(amqpAdmin, org.mockito.Mockito.times(3)).declareExchange(captor.capture());

        assertThat(captor.getAllValues().get(0)).isInstanceOf(DirectExchange.class);
        assertThat(captor.getAllValues().get(1)).isInstanceOf(TopicExchange.class);
        assertThat(captor.getAllValues().get(2)).isInstanceOf(FanoutExchange.class);
    }

    @Test
    void rechazaUnTipoDeExchangeInvalidoYDiceCualesSonValidos() {
        assertThatThrownBy(() -> service.crearExchange("x.raro", "carrusel", true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("direct")
                .hasMessageContaining("topic");
    }

    // ---------------------------------------------------------- bindings

    @Test
    void usaRoutingKeyVaciaCuandoNoSeIndicaNinguna() {
        service.crearBinding("q.pruebas", "x.difusion", null);

        ArgumentCaptor<Binding> captor = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin).declareBinding(captor.capture());

        assertThat(captor.getValue().getRoutingKey()).isEmpty();
        assertThat(captor.getValue().getDestination()).isEqualTo("q.pruebas");
        assertThat(captor.getValue().getExchange()).isEqualTo("x.difusion");
    }

    // ------------------------------------------------- errores del broker

    @Test
    void traduceElRechazoDelBrokerAUnConflicto() {
        // Caso tipico: redeclarar una cola existente con argumentos distintos.
        when(amqpAdmin.getQueueProperties(anyString()))
                .thenThrow(new AmqpIOException(new java.io.IOException("PRECONDITION_FAILED - args")));

        assertThatThrownBy(() -> service.obtenerCola("q.cmd.email"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PRECONDITION_FAILED");
    }

    @Test
    void distingueElBrokerCaidoDeUnRechazoDelBroker() {
        org.mockito.Mockito.doThrow(new AmqpConnectException(new java.net.ConnectException("rechazada")))
                .when(amqpAdmin).declareQueue(any(Queue.class));

        assertThatThrownBy(() -> service.crearCola("q.pruebas", true, false, null, null))
                .isInstanceOf(BrokerNoDisponibleException.class)
                .hasMessageContaining("No hay conexion");
    }
}
