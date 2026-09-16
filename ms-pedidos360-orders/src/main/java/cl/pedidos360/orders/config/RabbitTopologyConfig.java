package cl.pedidos360.orders.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia RabbitMQ del Caso 0: 3 flujos de comando (email, kitchen, invoice),
 * cada uno con su cola principal y su DLQ, mas los exchanges direct/topic y el
 * exchange de dead-lettering.
 *
 * Para EP1 solo q.cmd.email tiene consumidor activo (ms-notify); kitchen e
 * invoice se declaran para dejar la topologia completa desde ahora, tal como
 * indica el roadmap, aunque todavia no publiquen ni consuman.
 *
 * Esta clase se declara solo en ms-pedidos360-orders porque es quien primero
 * necesita que la topologia exista al arrancar (es el unico productor). Los
 * consumidores (ms-notify) declaran las mismas colas de forma idempotente por
 * si arrancan antes.
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String EXCHANGE_DIRECT = "cmd.direct";
    public static final String EXCHANGE_TOPIC = "cmd.topic";
    public static final String EXCHANGE_DLX = "cmd.dead.dlx";

    private static final String Q_EMAIL = "q.cmd.email";
    private static final String Q_KITCHEN = "q.cmd.kitchen";
    private static final String Q_INVOICE = "q.cmd.invoice";

    @Bean
    DirectExchange cmdDirectExchange() {
        return new DirectExchange(EXCHANGE_DIRECT, true, false);
    }

    @Bean
    TopicExchange cmdTopicExchange() {
        return new TopicExchange(EXCHANGE_TOPIC, true, false);
    }

    @Bean
    DirectExchange cmdDeadLetterExchange() {
        return new DirectExchange(EXCHANGE_DLX, true, false);
    }

    // ---- Colas principales, cada una enruta sus mensajes fallidos a su DLQ ----

    @Bean
    Queue qCmdEmail() {
        return colaConDlq(Q_EMAIL);
    }

    @Bean
    Queue qCmdKitchen() {
        return colaConDlq(Q_KITCHEN);
    }

    @Bean
    Queue qCmdInvoice() {
        return colaConDlq(Q_INVOICE);
    }

    private Queue colaConDlq(String nombre) {
        return QueueBuilder.durable(nombre)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", nombre.substring("q.cmd.".length()).equals("email") ? "email.send"
                        : nombre.substring("q.cmd.".length()).equals("kitchen") ? "kitchen.ticket" : "invoice.gen")
                .build();
    }

    // ---- DLQs: colas simples colgadas del exchange de dead-lettering ----

    @Bean
    Queue qCmdEmailDlq() {
        return QueueBuilder.durable(Q_EMAIL + ".dlq").build();
    }

    @Bean
    Queue qCmdKitchenDlq() {
        return QueueBuilder.durable(Q_KITCHEN + ".dlq").build();
    }

    @Bean
    Queue qCmdInvoiceDlq() {
        return QueueBuilder.durable(Q_INVOICE + ".dlq").build();
    }

    // ---- Bindings direct: enrutamiento exacto ----

    @Bean
    Binding bindEmailDirect() {
        return BindingBuilder.bind(qCmdEmail()).to(cmdDirectExchange()).with("email.send");
    }

    @Bean
    Binding bindKitchenDirect() {
        return BindingBuilder.bind(qCmdKitchen()).to(cmdDirectExchange()).with("kitchen.ticket");
    }

    @Bean
    Binding bindInvoiceDirect() {
        return BindingBuilder.bind(qCmdInvoice()).to(cmdDirectExchange()).with("invoice.gen");
    }

    // ---- Bindings topic: variantes (ej. email.send.high) sin tocar el binding ----

    @Bean
    Binding bindEmailTopic() {
        return BindingBuilder.bind(qCmdEmail()).to(cmdTopicExchange()).with("email.*");
    }

    @Bean
    Binding bindKitchenTopic() {
        return BindingBuilder.bind(qCmdKitchen()).to(cmdTopicExchange()).with("kitchen.#");
    }

    @Bean
    Binding bindInvoiceTopic() {
        return BindingBuilder.bind(qCmdInvoice()).to(cmdTopicExchange()).with("invoice.*");
    }

    // ---- Bindings de dead-lettering ----

    @Bean
    Binding bindEmailDlq() {
        return BindingBuilder.bind(qCmdEmailDlq()).to(cmdDeadLetterExchange()).with("email.send");
    }

    @Bean
    Binding bindKitchenDlq() {
        return BindingBuilder.bind(qCmdKitchenDlq()).to(cmdDeadLetterExchange()).with("kitchen.ticket");
    }

    @Bean
    Binding bindInvoiceDlq() {
        return BindingBuilder.bind(qCmdInvoiceDlq()).to(cmdDeadLetterExchange()).with("invoice.gen");
    }

    /** JSON en vez de Java serializado: interoperable entre servicios y legible en la management UI. */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
