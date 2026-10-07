package cl.pedidos360.notify.config;

import org.springframework.amqp.support.converter.Jackson2JavaTypeMapper.TypePrecedence;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Deserializacion de los mensajes que llegan desde RabbitMQ.
 *
 * TypePrecedence.INFERRED: se deduce el tipo del parametro del @RabbitListener
 * en vez de leer el header __TypeId__ que puso el productor. Es necesario
 * porque ms-orders y este servicio tienen las clases de payload en paquetes
 * Java distintos: usando el header, Jackson intentaria cargar aqui
 * cl.pedidos360.orders.messaging.EventEnvelope y fallaria con
 * ClassNotFoundException.
 */
@Configuration
public class MessageConverterConfig {

    @Bean
    MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setTypePrecedence(TypePrecedence.INFERRED);
        return converter;
    }
}
