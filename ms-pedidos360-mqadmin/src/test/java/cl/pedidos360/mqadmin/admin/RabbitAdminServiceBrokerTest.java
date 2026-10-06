package cl.pedidos360.mqadmin.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Prueba contra el broker de verdad, no contra un mock.
 *
 * Verifica lo que los mocks no pueden: que RabbitMQ acepte las declaraciones
 * que arma el servicio y que un binding creado por la API efectivamente
 * enrute mensajes.
 *
 * Si el cluster local no esta levantado la prueba se salta en vez de fallar,
 * para que `mvn test` siga sirviendo sin infraestructura. Levantarlo con:
 *   docker compose -f infra/local/docker-compose.yml up -d
 */
class RabbitAdminServiceBrokerTest {

    private static final String COLA = "q.prueba.integracion";
    private static final String EXCHANGE = "x.prueba.integracion";
    private static final String ROUTING_KEY = "prueba.uno";

    private CachingConnectionFactory connectionFactory;
    private RabbitAdminService service;
    private RabbitTemplate template;

    @BeforeEach
    void setUp() {
        connectionFactory = new CachingConnectionFactory();
        connectionFactory.setAddresses("localhost:5672,localhost:5673");
        connectionFactory.setUsername("guest");
        connectionFactory.setPassword("guest");

        assumeTrue(brokerDisponible(), "El cluster local de RabbitMQ no esta levantado");

        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        service = new RabbitAdminService(rabbitAdmin);
        template = new RabbitTemplate(connectionFactory);
    }

    @AfterEach
    void limpiar() {
        if (service != null) {
            try {
                service.eliminarCola(COLA);
                service.eliminarExchange(EXCHANGE);
            } catch (RuntimeException ignorada) {
                // La prueba pudo haber fallado antes de crearlos.
            }
        }
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    private boolean brokerDisponible() {
        try (var conexion = connectionFactory.createConnection()) {
            return conexion.isOpen();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Test
    void creaLaTopologiaCompletaYElBindingEnrutaDeVerdad() throws Exception {
        service.crearExchange(EXCHANGE, "topic", true, false);
        service.crearCola(COLA, true, false, null, null);
        service.crearBinding(COLA, EXCHANGE, "prueba.*");

        Optional<ColaInfo> recienCreada = service.obtenerCola(COLA);
        assertThat(recienCreada).isPresent();
        assertThat(recienCreada.get().mensajes()).isZero();

        // La prueba real del binding: si enruta, el mensaje aparece en la cola.
        template.convertAndSend(EXCHANGE, ROUTING_KEY, "hola");

        assertThat(esperarMensajes(1)).isEqualTo(1);
    }

    @Test
    void vaciarDescartaLosMensajesPeroConservaLaCola() throws Exception {
        service.crearExchange(EXCHANGE, "direct", true, false);
        service.crearCola(COLA, true, false, null, null);
        service.crearBinding(COLA, EXCHANGE, ROUTING_KEY);

        template.convertAndSend(EXCHANGE, ROUTING_KEY, "uno");
        template.convertAndSend(EXCHANGE, ROUTING_KEY, "dos");
        assertThat(esperarMensajes(2)).isEqualTo(2);

        assertThat(service.vaciarCola(COLA)).isEqualTo(2);
        assertThat(service.obtenerCola(COLA)).isPresent();
        assertThat(service.obtenerCola(COLA).get().mensajes()).isZero();
    }

    @Test
    void eliminarDevuelveFalseCuandoLaColaNoExisteEnElBroker() {
        // Esta es la prueba que descubrio que deleteQueue devuelve true aunque
        // la cola no exista: sin la comprobacion previa de existencia, esto
        // pasaba a ser true y el endpoint nunca podia responder 404.
        assertThat(service.eliminarCola("q.que.no.existe.en.absoluto")).isFalse();
    }

    @Test
    void eliminarDevuelveTrueCuandoLaColaSiExistia() {
        service.crearCola(COLA, true, false, null, null);
        assertThat(service.eliminarCola(COLA)).isTrue();
        assertThat(service.obtenerCola(COLA)).isEmpty();
    }

    @Test
    void elBrokerRechazaRedeclararUnaColaConArgumentosDistintos() {
        service.crearCola(COLA, true, false, null, null);

        // Misma cola, ahora con dead-lettering: RabbitMQ responde
        // PRECONDITION_FAILED y el servicio lo traduce a un conflicto.
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> service.crearCola(COLA, true, false, "cmd.dead.dlx", "x"))
                .isInstanceOf(IllegalStateException.class);
    }

    /** El broker acusa recibo de forma asincrona; se espera hasta 5 segundos. */
    private int esperarMensajes(int esperados) throws InterruptedException {
        for (int intento = 0; intento < 50; intento++) {
            int actuales = service.obtenerCola(COLA).map(ColaInfo::mensajes).orElse(0);
            if (actuales >= esperados) {
                return actuales;
            }
            Thread.sleep(100);
        }
        return service.obtenerCola(COLA).map(ColaInfo::mensajes).orElse(0);
    }
}
