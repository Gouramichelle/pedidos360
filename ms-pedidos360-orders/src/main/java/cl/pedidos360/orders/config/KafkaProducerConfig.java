package cl.pedidos360.orders.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.config.TopicBuilder;

/**
 * ms-orders es el unico productor de orders.events, asi que es quien declara
 * el topico (3 particiones, retencion 3-7 dias) para no depender del
 * auto-create del broker.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public NewTopic ordersEventsTopic() {
        return TopicBuilder.name("orders.events")
                // Tres particiones: permiten que tres consumidores del mismo
                // grupo trabajen en paralelo. La clave de particion es el id
                // del pedido, asi que los eventos de un mismo pedido caen
                // siempre en la misma particion y conservan su orden.
                .partitions(3)
                // Tres replicas, una por broker. Con min.insync.replicas=2 se
                // sigue aceptando escritura si se cae un broker.
                .replicas(3)
                .config("retention.ms", String.valueOf(5L * 24 * 60 * 60 * 1000)) // 5 dias, dentro del rango 3-7
                .config("min.insync.replicas", "2")
                .build();
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // acks=all: espera confirmacion de todas las replicas in-sync antes de dar
        // por publicado el evento de negocio (orders.events es la fuente de verdad).
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
