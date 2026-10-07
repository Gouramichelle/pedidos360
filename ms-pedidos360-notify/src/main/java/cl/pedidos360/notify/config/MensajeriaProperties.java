package cl.pedidos360.notify.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de la topologia de RabbitMQ, leidos del bloque "mensajeria" del
 * application.yml.
 *
 * Existe para que no quede ni un nombre de cola, exchange o routing key
 * escrito en el codigo. RabbitTopologyConfig recorre estos valores para
 * declarar la topologia y los publishers los consultan para saber donde
 * escribir, asi que agregar un flujo nuevo es agregar una entrada en el yml.
 */
@ConfigurationProperties(prefix = "mensajeria")
public record MensajeriaProperties(Exchanges exchanges, Map<String, Flujo> flujos) {

    public record Exchanges(String direct, String topic, String dlx) {
    }

    /**
     * @param cola        nombre de la cola principal del flujo
     * @param routingKey  clave exacta con la que se publica y con la que la DLQ
     *                    cuelga del dead-letter exchange
     * @param patronTopic binding en el exchange topic, admite * y #
     */
    public record Flujo(String cola, String routingKey, String patronTopic) {

        /** La DLQ siempre se llama igual que su cola con el sufijo .dlq. */
        public String dlq() {
            return cola + ".dlq";
        }
    }

    public Flujo flujo(String nombre) {
        Flujo encontrado = flujos.get(nombre);
        if (encontrado == null) {
            throw new IllegalStateException(
                    "No hay un flujo '" + nombre + "' configurado en mensajeria.flujos del application.yml");
        }
        return encontrado;
    }
}
