package cl.pedidos360.notify.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia de comandos de RabbitMQ, armada a partir del bloque "mensajeria"
 * del application.yml. No hay ni un nombre escrito en esta clase.
 *
 * Por cada flujo configurado se declara la cola principal, su DLQ y tres
 * bindings:
 *   - en el exchange direct, con la routing key exacta
 *   - en el exchange topic, con el patron que admite comodines
 *   - la DLQ en el dead-letter exchange, con la misma routing key
 *
 * Se usa un unico bean Declarables en vez de un bean por objeto porque asi la
 * cantidad de flujos la decide el yml: agregar uno nuevo no agrega metodos.
 * RabbitAdmin declara todo lo que encuentre dentro de un Declarables igual que
 * si fueran beans sueltos.
 *
 * ms-orders y ms-notify declaran esta misma topologia de forma idempotente,
 * por si uno arranca antes que el otro. Declarar dos veces la misma cola con
 * los mismos argumentos no falla; hacerlo con argumentos distintos si, por eso
 * el bloque del yml tiene que ser identico en los dos servicios.
 */
@Configuration
@EnableConfigurationProperties(MensajeriaProperties.class)
public class RabbitTopologyConfig {

    @Bean
    Declarables topologiaDeComandos(MensajeriaProperties props) {
        DirectExchange direct = new DirectExchange(props.exchanges().direct(), true, false);
        TopicExchange topic = new TopicExchange(props.exchanges().topic(), true, false);
        DirectExchange dlx = new DirectExchange(props.exchanges().dlx(), true, false);

        List<Declarable> declarables = new ArrayList<>(List.of(direct, topic, dlx));

        props.flujos().forEach((dominio, flujo) -> {
            Queue cola = QueueBuilder.durable(flujo.cola())
                    .withArgument("x-dead-letter-exchange", props.exchanges().dlx())
                    .withArgument("x-dead-letter-routing-key", flujo.routingKey())
                    .build();
            Queue dlq = QueueBuilder.durable(flujo.dlq()).build();

            declarables.add(cola);
            declarables.add(dlq);
            declarables.add(BindingBuilder.bind(cola).to(direct).with(flujo.routingKey()));
            declarables.add(BindingBuilder.bind(cola).to(topic).with(flujo.patronTopic()));
            declarables.add(BindingBuilder.bind(dlq).to(dlx).with(flujo.routingKey()));
        });

        return new Declarables(declarables);
    }
}
