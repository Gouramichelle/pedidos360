package cl.pedidos360.mqadmin.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Prueba contra el cluster local de verdad.
 *
 * Se salta sola si no hay cluster levantado, para que un clon nuevo del repo
 * pueda correr `mvn test` sin necesitar Docker. Con el cluster arriba
 * (infra/local/docker-compose.yml) si se ejecuta.
 *
 * Trabaja sobre topicos con nombre aleatorio y los borra al terminar: nunca
 * toca orders.events ni audit.timeline.
 */
class KafkaAdminServiceBrokerTest {

    private static final String BOOTSTRAP = "localhost:9092,localhost:9093,localhost:9094";

    private static Admin admin;
    private static KafkaAdminService service;

    @BeforeAll
    static void abrir() {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP);
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 4000);
        admin = Admin.create(config);
        service = new KafkaAdminService(admin);
    }

    @AfterAll
    static void cerrar() {
        if (admin != null) {
            admin.close();
        }
    }

    private static boolean clusterDisponible() {
        try {
            admin.describeCluster().nodes().get(4, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String nombreDePrueba() {
        return "prueba." + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Crear un topico vuelve en cuanto el controlador lo acepta, pero los
     * metadatos tardan un instante en llegar al broker al que se le pregunta.
     * Se espera a que el topico tenga las particiones esperadas antes de
     * comprobar nada.
     */
    private TopicoInfo esperarTopico(String nombre, int particiones) {
        for (int i = 0; i < 40; i++) {
            var info = service.describirTopico(nombre);
            if (info.isPresent() && info.get().particiones().size() == particiones) {
                return info.get();
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("El topico " + nombre + " no llego a tener " + particiones + " particiones");
    }

    @Test
    void creaDescribeYEliminaUnTopico() {
        assumeTrue(clusterDisponible(), "El cluster local de Kafka no esta levantado");
        String topico = nombreDePrueba();

        service.crearTopico(topico, 3, (short) 3, Map.of("retention.ms", "60000"));
        try {
            var info = esperarTopico(topico, 3);

            assertThat(info.particiones()).hasSize(3);
            assertThat(info.particiones().get(0).replicas())
                    .as("tres replicas, una por broker")
                    .hasSize(3);
            assertThat(info.particiones().get(0).sincronizadas())
                    .as("las tres deberian estar al dia en un cluster sano")
                    .hasSize(3);
            assertThat(info.configuracion()).containsEntry("retention.ms", "60000");
            assertThat(service.listarTopicos()).contains(topico);
        } finally {
            service.eliminarTopico(topico);
        }
    }

    @Test
    void cambiaLaConfiguracionEnCaliente() {
        assumeTrue(clusterDisponible(), "El cluster local de Kafka no esta levantado");
        String topico = nombreDePrueba();

        service.crearTopico(topico, 1, (short) 1, Map.of("retention.ms", "60000"));
        try {
            service.actualizarConfiguraciones(topico, Map.of("retention.ms", "120000"));

            assertThat(service.leerConfiguraciones(topico))
                    .containsEntry("retention.ms", "120000");
        } finally {
            service.eliminarTopico(topico);
        }
    }

    @Test
    void ampliaLasParticionesHaciaArriba() {
        assumeTrue(clusterDisponible(), "El cluster local de Kafka no esta levantado");
        String topico = nombreDePrueba();

        service.crearTopico(topico, 1, (short) 1, Map.of());
        try {
            service.ampliarParticiones(topico, 3);

            assertThat(esperarTopico(topico, 3).particiones()).hasSize(3);
        } finally {
            service.eliminarTopico(topico);
        }
    }

    @Test
    void describirUnTopicoQueNoExisteNoFalla() {
        assumeTrue(clusterDisponible(), "El cluster local de Kafka no esta levantado");

        assertThat(service.describirTopico("no.existe." + UUID.randomUUID())).isEmpty();
    }

    @Test
    void eliminarUnTopicoQueNoExisteDevuelveFalse() {
        assumeTrue(clusterDisponible(), "El cluster local de Kafka no esta levantado");

        assertThat(service.eliminarTopico("no.existe." + UUID.randomUUID())).isFalse();
    }
}
