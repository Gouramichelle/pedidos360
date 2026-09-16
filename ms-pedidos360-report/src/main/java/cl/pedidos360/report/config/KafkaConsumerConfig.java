package cl.pedidos360.report.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * ErrorHandlingDeserializer envuelve al JsonDeserializer real: si un mensaje
 * llega corrupto o con un tipo que no matchea, el error se captura por
 * registro individual en vez de tumbar el listener container completo.
 *
 * trusted.packages "*" es intencional: el productor (ms-orders) y este
 * consumidor tienen EventEnvelope en paquetes Java distintos, y de todas
 * formas se ignora el header de tipo (value.default.type fuerza siempre el
 * mismo tipo destino), asi que no hay riesgo de deserializar una clase
 * arbitraria controlada por el productor.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    /**
     * Topico de descarte de este consumidor. Hay que declararlo: el broker
     * tiene deshabilitada la creacion automatica de topicos, asi que sin esto
     * el DeadLetterPublishingRecoverer no puede publicar, el error handler
     * falla al intentarlo y el mismo mensaje se reintenta en loop para siempre.
     */
    @Bean
    public NewTopic deadLetterTopic() {
        return TopicBuilder.name("orders.events." + groupId + ".DLT")
                .partitions(3)
                .config("retention.ms", String.valueOf(10L * 24 * 60 * 60 * 1000)) // 10 dias, dentro del rango 7-14
                .build();
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                "cl.pedidos360.report.messaging.EventEnvelope");
        return new DefaultKafkaConsumerFactory<>(config);
    }

    /** Productor exclusivo del recoverer, para publicar en el *.DLT del consumidor que fallo. */
    @Bean
    public ProducerFactory<String, Object> dltProducerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(config);
    }

    /**
     * Expuesto como bean (no construido "en linea" dentro de otro metodo) a
     * proposito: KafkaAutoConfiguration de Spring Boot tambien busca crear su
     * propio KafkaTemplate<Object, Object> a partir de cualquier ProducerFactory
     * disponible, y sin este bean explicito colisiona con dltProducerFactory
     * por un choque de tipos genericos (String vs Object) que tumba el arranque
     * con "required a bean of type ProducerFactory that could not be found".
     * Declarar este KafkaTemplate satisface el @ConditionalOnMissingBean de
     * Boot y evita que lo intente crear el solo.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> dltProducerFactory) {
        return new KafkaTemplate<>(dltProducerFactory);
    }

    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaOperations<String, Object> kafkaTemplate) {
        // Nombre por convencion del caso: <topico-original>.<group-id>.DLT
        return new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(record.topic() + "." + groupId + ".DLT", -1));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            DeadLetterPublishingRecoverer deadLetterPublishingRecoverer) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(AckMode.RECORD);
        // 3 reintentos con 1s de espera; si sigue fallando, se publica el mensaje
        // original + metadatos de error en el *.DLT de este consumidor, tal como
        // pide el caso para orders.events.report.DLT.
        factory.setCommonErrorHandler(
                new DefaultErrorHandler(deadLetterPublishingRecoverer, new FixedBackOff(1000L, 3)));
        return factory;
    }
}
