package cl.pedidos360.notify.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declaracion idempotente de la cola que este servicio consume (y su DLQ),
 * por si ms-notify arranca antes que ms-orders. Declarar dos veces la misma
 * cola con los mismos argumentos no falla; declararla con argumentos
 * distintos si lo haria, por eso los argumentos de x-dead-letter-* deben
 * coincidir exactamente con los de ms-pedidos360-orders.
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String EXCHANGE_DIRECT = "cmd.direct";
    public static final String EXCHANGE_TOPIC = "cmd.topic";
    public static final String EXCHANGE_DLX = "cmd.dead.dlx";
    private static final String Q_EMAIL = "q.cmd.email";

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

    @Bean
    Queue qCmdEmail() {
        return QueueBuilder.durable(Q_EMAIL)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", "email.send")
                .build();
    }

    @Bean
    Queue qCmdEmailDlq() {
        return QueueBuilder.durable(Q_EMAIL + ".dlq").build();
    }

    @Bean
    Binding bindEmailDirect() {
        return BindingBuilder.bind(qCmdEmail()).to(cmdDirectExchange()).with("email.send");
    }

    @Bean
    Binding bindEmailTopic() {
        return BindingBuilder.bind(qCmdEmail()).to(cmdTopicExchange()).with("email.*");
    }

    @Bean
    Binding bindEmailDlq() {
        return BindingBuilder.bind(qCmdEmailDlq()).to(cmdDeadLetterExchange()).with("email.send");
    }

    /**
     * TypePrecedence.INFERRED: deserializa usando el tipo del parametro del
     * @RabbitListener en vez del header __TypeId__ que puso el productor.
     * Necesario porque el productor (ms-orders) y este consumidor tienen la
     * clase EventEnvelope en paquetes Java distintos -- si se usara el header,
     * Jackson intentaria cargar cl.pedidos360.orders.messaging.EventEnvelope
     * aqui y fallaria con ClassNotFoundException.
     */
    @Bean
    MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setTypePrecedence(TypePrecedence.INFERRED);
        return converter;
    }
}
