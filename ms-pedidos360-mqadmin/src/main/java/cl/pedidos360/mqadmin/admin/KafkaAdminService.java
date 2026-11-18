package cl.pedidos360.mqadmin.admin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Administracion de la plataforma Kafka.
 *
 * Toda la interaccion con el cluster vive aca: los controladores no importan
 * nada de org.apache.kafka, igual que no importan nada de org.springframework
 * .amqp para el lado de RabbitMQ. Si manana se cambiara de plataforma, la capa
 * web no se entera.
 *
 * AdminClient es asincrono: cada operacion devuelve futures. Se esperan con un
 * tiempo limite y se traducen los fallos a una excepcion propia, para que un
 * cluster caido no deje la peticion HTTP colgada.
 */
@Service
public class KafkaAdminService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);

    /** Si el cluster no responde en este tiempo, se corta y se informa. */
    private static final int ESPERA_SEGUNDOS = 10;

    /** Los topicos internos de Kafka no son asunto de esta API. */
    private static final String PREFIJO_INTERNO = "__";

    private final Admin admin;

    public KafkaAdminService(Admin admin) {
        this.admin = admin;
    }

    // ------------------------------------------------------------- topicos

    /**
     * Crea un topico. Las particiones y las replicas no se pueden cambiar
     * despues sin reasignar, asi que conviene pensarlas al crear.
     */
    public void crearTopico(String nombre, int particiones, short replicas, Map<String, String> configuraciones) {
        NewTopic topico = new NewTopic(nombre, particiones, replicas);
        if (configuraciones != null && !configuraciones.isEmpty()) {
            topico.configs(configuraciones);
        }
        esperar(admin.createTopics(List.of(topico)).all(), "crear el topico " + nombre);
        log.info("Topico creado: {} ({} particiones, {} replicas)", nombre, particiones, replicas);
    }

    /** Los topicos del sistema quedan fuera: empiezan con doble guion bajo. */
    public List<String> listarTopicos() {
        Collection<String> nombres = esperar(admin.listTopics().names(), "listar los topicos");
        return nombres.stream().filter(n -> !n.startsWith(PREFIJO_INTERNO)).sorted().toList();
    }

    /**
     * Describe un topico: sus particiones, quien es lider de cada una, donde
     * estan las replicas y cuales estan sincronizadas.
     *
     * El numero de replicas sincronizadas es el dato que importa: si cae por
     * debajo de min.insync.replicas, el topico deja de aceptar escrituras.
     */
    public Optional<TopicoInfo> describirTopico(String nombre) {
        TopicDescription descripcion;
        try {
            descripcion = admin.describeTopics(List.of(nombre)).allTopicNames()
                    .get(ESPERA_SEGUNDOS, TimeUnit.SECONDS).get(nombre);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                return Optional.empty();
            }
            throw fallo("describir el topico " + nombre, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fallo("describir el topico " + nombre, e);
        } catch (TimeoutException e) {
            throw fallo("describir el topico " + nombre, e);
        }
        if (descripcion == null) {
            return Optional.empty();
        }

        List<TopicoInfo.ParticionInfo> particiones = descripcion.partitions().stream()
                .map(p -> new TopicoInfo.ParticionInfo(
                        p.partition(),
                        p.leader() == null ? -1 : p.leader().id(),
                        p.replicas().stream().map(org.apache.kafka.common.Node::id).sorted().toList(),
                        p.isr().stream().map(org.apache.kafka.common.Node::id).sorted().toList()))
                .sorted((a, b) -> Integer.compare(a.particion(), b.particion()))
                .toList();

        return Optional.of(new TopicoInfo(nombre, particiones, leerConfiguraciones(nombre)));
    }

    /** Solo las configuraciones puestas a mano: las heredadas del broker se omiten. */
    public Map<String, String> leerConfiguraciones(String nombre) {
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        var configs = esperar(admin.describeConfigs(List.of(recurso)).all(),
                "leer la configuracion de " + nombre).get(recurso);
        Map<String, String> salida = new HashMap<>();
        if (configs != null) {
            for (ConfigEntry entrada : configs.entries()) {
                if (!entrada.isDefault()) {
                    salida.put(entrada.name(), entrada.value());
                }
            }
        }
        return salida;
    }

    /**
     * Cambia configuraciones en caliente, sin reiniciar el broker ni recrear
     * el topico: retention.ms, cleanup.policy, min.insync.replicas y demas.
     */
    public void actualizarConfiguraciones(String nombre, Map<String, String> cambios) {
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        List<AlterConfigOp> operaciones = cambios.entrySet().stream()
                .map(e -> new AlterConfigOp(new ConfigEntry(e.getKey(), e.getValue()), AlterConfigOp.OpType.SET))
                .toList();
        esperar(admin.incrementalAlterConfigs(Map.of(recurso, operaciones)).all(),
                "actualizar la configuracion de " + nombre);
        log.info("Configuracion de {} actualizada: {}", nombre, cambios.keySet());
    }

    /**
     * Aumenta el numero de particiones. Solo hacia arriba: Kafka no permite
     * reducirlas, porque los mensajes ya repartidos no se pueden reubicar sin
     * romper el orden por clave.
     */
    public void ampliarParticiones(String nombre, int total) {
        esperar(admin.createPartitions(Map.of(nombre, NewPartitions.increaseTo(total))).all(),
                "ampliar las particiones de " + nombre);
        log.info("Topico {} ampliado a {} particiones", nombre, total);
    }

    /** Devuelve false si el topico no existia. */
    public boolean eliminarTopico(String nombre) {
        try {
            admin.deleteTopics(List.of(nombre)).all().get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
            log.info("Topico eliminado: {}", nombre);
            return true;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                return false;
            }
            throw fallo("eliminar el topico " + nombre, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fallo("eliminar el topico " + nombre, e);
        } catch (TimeoutException e) {
            throw fallo("eliminar el topico " + nombre, e);
        }
    }

    // --------------------------------------------- grupos de consumidores

    public List<String> listarGrupos() {
        Collection<ConsumerGroupListing> grupos = esperar(admin.listConsumerGroups().all(),
                "listar los grupos de consumidores");
        return grupos.stream().map(ConsumerGroupListing::groupId).sorted().toList();
    }

    /**
     * El estado de un grupo, con el retraso de cada particion.
     *
     * El retraso es la diferencia entre el ultimo offset escrito y el ultimo
     * confirmado por el grupo: cuantos mensajes hay publicados que ese grupo
     * todavia no proceso. Es la metrica que dice si un consumidor va al dia o
     * se esta quedando atras.
     */
    public Optional<GrupoConsumidorInfo> describirGrupo(String grupo) {
        var descripciones = esperar(admin.describeConsumerGroups(List.of(grupo)).all(),
                "describir el grupo " + grupo);
        var descripcion = descripciones.get(grupo);
        if (descripcion == null) {
            return Optional.empty();
        }

        Map<TopicPartition, OffsetAndMetadata> confirmados =
                esperar(admin.listConsumerGroupOffsets(grupo).partitionsToOffsetAndMetadata(),
                        "leer los offsets del grupo " + grupo);

        Map<TopicPartition, OffsetSpec> consulta = new HashMap<>();
        confirmados.keySet().forEach(tp -> consulta.put(tp, OffsetSpec.latest()));
        var finales = consulta.isEmpty() ? Map.<TopicPartition, ListOffsetsResultInfo>of()
                : esperar(admin.listOffsets(consulta).all(), "leer los offsets finales de " + grupo);

        List<GrupoConsumidorInfo.RetrasoParticion> retrasos = new ArrayList<>();
        confirmados.forEach((tp, offset) -> {
            long fin = finales.containsKey(tp) ? finales.get(tp).offset() : offset.offset();
            retrasos.add(new GrupoConsumidorInfo.RetrasoParticion(
                    tp.topic(), tp.partition(), offset.offset(), fin, Math.max(0, fin - offset.offset())));
        });
        retrasos.sort((a, b) -> {
            int porTopico = a.topico().compareTo(b.topico());
            return porTopico != 0 ? porTopico : Integer.compare(a.particion(), b.particion());
        });

        return Optional.of(new GrupoConsumidorInfo(
                grupo, descripcion.state().toString(), descripcion.members().size(), retrasos));
    }

    /** Un grupo solo se puede borrar si no tiene miembros conectados. */
    public boolean eliminarGrupo(String grupo) {
        try {
            admin.deleteConsumerGroups(List.of(grupo)).all().get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
            log.info("Grupo de consumidores eliminado: {}", grupo);
            return true;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof org.apache.kafka.common.errors.GroupIdNotFoundException) {
                return false;
            }
            throw fallo("eliminar el grupo " + grupo, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fallo("eliminar el grupo " + grupo, e);
        } catch (TimeoutException e) {
            throw fallo("eliminar el grupo " + grupo, e);
        }
    }

    // ------------------------------------------------------------ interno

    private <T> T esperar(org.apache.kafka.common.KafkaFuture<T> futuro, String operacion) {
        try {
            return futuro.get(ESPERA_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fallo(operacion, e);
        } catch (ExecutionException | TimeoutException e) {
            throw fallo(operacion, e);
        }
    }

    private BrokerNoDisponibleException fallo(String operacion, Exception causa) {
        Throwable raiz = causa.getCause() != null ? causa.getCause() : causa;
        log.warn("Fallo al {}: {}", operacion, raiz.getMessage());
        return new BrokerNoDisponibleException("No se pudo " + operacion + ": " + raiz.getMessage(), raiz);
    }
}
