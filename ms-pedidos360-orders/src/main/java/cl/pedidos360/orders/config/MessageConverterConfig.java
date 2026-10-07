package cl.pedidos360.orders.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Serializacion de los mensajes que salen hacia RabbitMQ.
 *
 * JSON en vez de serializacion Java: es interoperable entre servicios y se
 * puede leer tal cual en la consola del broker, que es justamente lo que se
 * necesita para inspeccionar una DLQ.
 */
@Configuration
public class MessageConverterConfig {

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
