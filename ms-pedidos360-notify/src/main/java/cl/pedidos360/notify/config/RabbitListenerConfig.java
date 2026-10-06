package cl.pedidos360.notify.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Pone los consumidores en modo de confirmacion MANUAL.
 *
 * Con el modo AUTO que trae Spring por defecto, el framework confirma el
 * mensaje solo si el metodo del listener retorna sin excepcion, y lo rechaza
 * si lanza. Funciona, pero la decision no esta escrita en ninguna parte: no se
 * puede distinguir un fallo transitorio de uno permanente, ni decidir entre
 * reintentar y descartar. En MANUAL esa decision es explicita y vive completa
 * en ConfirmacionDeMensajes.
 *
 * Por el mismo motivo se quito el bloque spring.rabbitmq.listener.simple.retry
 * del application.yml: ese reintento lo hacia el framework en memoria, dentro
 * del mismo consumo y sin que el broker se enterara. Ahora el reintento es un
 * NACK con requeue de verdad, que devuelve el mensaje a la cola.
 */
@Configuration
@EnableRabbit
public class RabbitListenerConfig {

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        // Explicito aunque el configurer ya lo tome del bean: este converter es
        // el que resuelve el tipo por la firma del listener en vez de por el
        // header __TypeId__ del productor (ver RabbitTopologyConfig).
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
