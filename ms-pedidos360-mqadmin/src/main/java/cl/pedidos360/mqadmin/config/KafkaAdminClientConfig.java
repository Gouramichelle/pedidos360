package cl.pedidos360.mqadmin.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El cliente de administracion del cluster de Kafka.
 *
 * Se apunta a los tres brokers a proposito: si el primero no responde, el
 * cliente intenta con el siguiente. Basta con que uno conteste para descubrir
 * el resto del cluster.
 *
 * No hay productor ni consumidor en este microservicio: solo administra.
 */
@Configuration
public class KafkaAdminClientConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean(destroyMethod = "close")
    public Admin kafkaAdminClient() {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        // Sin esto, una peticion contra un cluster caido se queda reintentando
        // hasta el timeout por defecto, que son dos minutos.
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10_000);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 15_000);
        config.put(AdminClientConfig.CLIENT_ID_CONFIG, "mqadmin");
        return Admin.create(config);
    }
}
