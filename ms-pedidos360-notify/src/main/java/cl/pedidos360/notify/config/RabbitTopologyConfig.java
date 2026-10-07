package cl.pedidos360.notify.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia de comandos de RabbitMQ: un bean explicito por cada objeto, con
 * los nombres leidos del bloque "mensajeria" del application.yml.
 *
 * Los tres flujos del caso tienen la misma forma -- cola con dead-lettering,
 * DLQ, y tres bindings (direct por clave exacta, topic por patron, y la DLQ
 * colgada del dead-letter exchange) -- pero cada uno se declara por separado y
 * con nombre propio, para que la ruta de cada caso de uso se pueda leer de
 * corrido en el codigo.
 *
 * Lo que se repite es la construccion, no la configuracion: los helpers de
 * abajo arman la cola y la DLQ a partir del flujo, y ningun nombre aparece
 * escrito aqui. Cambiar como se llama una cola es cambiar el yml.
 *
 * ms-orders y ms-notify declaran esta misma topologia de forma idempotente,
 * por si uno arranca antes que el otro. Declarar dos veces la misma cola con
 * los mismos argumentos no falla; hacerlo con argumentos distintos si, por eso
 * el bloque del yml tiene que ser identico en los dos servicios.
 */
@Configuration
@EnableConfigurationProperties(MensajeriaProperties.class)
public class RabbitTopologyConfig {

    private static final String EMAIL = "email";
    private static final String KITCHEN = "kitchen";
    private static final String INVOICE = "invoice";

    private final MensajeriaProperties mensajeria;

    public RabbitTopologyConfig(MensajeriaProperties mensajeria) {
        this.mensajeria = mensajeria;
    }

    // ------------------------------------------------------------ exchanges

    @Bean
    DirectExchange cmdDirectExchange() {
        return new DirectExchange(mensajeria.exchanges().direct(), true, false);
    }

    @Bean
    TopicExchange cmdTopicExchange() {
        return new TopicExchange(mensajeria.exchanges().topic(), true, false);
    }

    @Bean
    DirectExchange cmdDeadLetterExchange() {
        return new DirectExchange(mensajeria.exchanges().dlx(), true, false);
    }

    // ------------------------------------------- flujo email: notificaciones

    @Bean
    Queue colaEmail() {
        return colaDeFlujo(EMAIL);
    }

    @Bean
    Queue colaEmailDlq() {
        return dlqDeFlujo(EMAIL);
    }

    @Bean
    Binding bindEmailDirect() {
        return BindingBuilder.bind(colaEmail()).to(cmdDirectExchange()).with(routingKey(EMAIL));
    }

    @Bean
    Binding bindEmailTopic() {
        return BindingBuilder.bind(colaEmail()).to(cmdTopicExchange()).with(patronTopic(EMAIL));
    }

    @Bean
    Binding bindEmailDlq() {
        return BindingBuilder.bind(colaEmailDlq()).to(cmdDeadLetterExchange()).with(routingKey(EMAIL));
    }

    // ------------------------------------------------- flujo kitchen: cocina

    @Bean
    Queue colaKitchen() {
        return colaDeFlujo(KITCHEN);
    }

    @Bean
    Queue colaKitchenDlq() {
        return dlqDeFlujo(KITCHEN);
    }

    @Bean
    Binding bindKitchenDirect() {
        return BindingBuilder.bind(colaKitchen()).to(cmdDirectExchange()).with(routingKey(KITCHEN));
    }

    @Bean
    Binding bindKitchenTopic() {
        return BindingBuilder.bind(colaKitchen()).to(cmdTopicExchange()).with(patronTopic(KITCHEN));
    }

    @Bean
    Binding bindKitchenDlq() {
        return BindingBuilder.bind(colaKitchenDlq()).to(cmdDeadLetterExchange()).with(routingKey(KITCHEN));
    }

    // -------------------------------------------- flujo invoice: facturacion

    @Bean
    Queue colaInvoice() {
        return colaDeFlujo(INVOICE);
    }

    @Bean
    Queue colaInvoiceDlq() {
        return dlqDeFlujo(INVOICE);
    }

    @Bean
    Binding bindInvoiceDirect() {
        return BindingBuilder.bind(colaInvoice()).to(cmdDirectExchange()).with(routingKey(INVOICE));
    }

    @Bean
    Binding bindInvoiceTopic() {
        return BindingBuilder.bind(colaInvoice()).to(cmdTopicExchange()).with(patronTopic(INVOICE));
    }

    @Bean
    Binding bindInvoiceDlq() {
        return BindingBuilder.bind(colaInvoiceDlq()).to(cmdDeadLetterExchange()).with(routingKey(INVOICE));
    }

    // ------------------------------------------------------------- internos

    /** Cola principal del flujo, con los mensajes rechazados enrutados al DLX. */
    private Queue colaDeFlujo(String flujo) {
        return QueueBuilder.durable(mensajeria.flujo(flujo).cola())
                .withArgument("x-dead-letter-exchange", mensajeria.exchanges().dlx())
                .withArgument("x-dead-letter-routing-key", routingKey(flujo))
                .build();
    }

    private Queue dlqDeFlujo(String flujo) {
        return QueueBuilder.durable(mensajeria.flujo(flujo).dlq()).build();
    }

    private String routingKey(String flujo) {
        return mensajeria.flujo(flujo).routingKey();
    }

    private String patronTopic(String flujo) {
        return mensajeria.flujo(flujo).patronTopic();
    }
}
