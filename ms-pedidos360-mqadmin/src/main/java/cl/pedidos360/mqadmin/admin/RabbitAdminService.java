package cl.pedidos360.mqadmin.admin;

import java.util.Optional;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

/**
 * Unico punto del servicio que conoce la API de RabbitMQ.
 *
 * Los controladores no importan nada de org.springframework.amqp: reciben y
 * devuelven tipos propios, y toda la traduccion a Queue, Exchange y Binding
 * ocurre aca. Eso es lo que permite cambiar como se declara la topologia sin
 * tocar la capa HTTP, y es la razon de que esta clase exista en vez de llamar
 * a AmqpAdmin directamente desde el controlador.
 *
 * Reparto de responsabilidades con la capa web: aqui se valida lo que es
 * regla del broker (nombres reservados, tipos de exchange validos) y se
 * devuelve el resultado en crudo (un boolean, un Optional). Traducir eso a
 * codigos HTTP es trabajo del controlador.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    /**
     * RabbitMQ reserva el prefijo "amq." para sus propios objetos: intentar
     * declarar algo con ese nombre cierra el canal con un error 403 del
     * protocolo. Se rechaza antes de llegar al broker para devolver un 400
     * entendible en vez de un fallo de conexion.
     */
    private static final String PREFIJO_RESERVADO = "amq.";

    private final AmqpAdmin amqpAdmin;

    public RabbitAdminService(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    // ---------------------------------------------------------------- colas

    public void crearCola(String nombre, boolean durable, boolean autoDelete,
            String deadLetterExchange, String deadLetterRoutingKey) {
        validarNombre(nombre, "cola");

        QueueBuilder builder = durable ? QueueBuilder.durable(nombre) : QueueBuilder.nonDurable(nombre);
        if (autoDelete) {
            builder.autoDelete();
        }
        // Si se indica DLX, la cola enruta ahi los mensajes rechazados o vencidos.
        if (tieneValor(deadLetterExchange)) {
            builder.withArgument("x-dead-letter-exchange", deadLetterExchange.trim());
        }
        if (tieneValor(deadLetterRoutingKey)) {
            builder.withArgument("x-dead-letter-routing-key", deadLetterRoutingKey.trim());
        }

        Queue cola = builder.build();
        ejecutar(() -> amqpAdmin.declareQueue(cola),
                "No se pudo declarar la cola '" + nombre + "'");
        log.info("Cola declarada: {} (durable={}, autoDelete={}, dlx={})",
                nombre, durable, autoDelete, deadLetterExchange);
    }

    /**
     * @return false si la cola no existia.
     *
     * Se consulta la existencia antes de borrar en vez de confiar en lo que
     * devuelve deleteQueue: el borrado de colas en AMQP es idempotente y el
     * broker responde con exito aunque la cola no exista, asi que ese boolean
     * es true siempre. Sin esta comprobacion previa, un DELETE con el nombre
     * mal escrito devolveria 204 y daria a entender que borro algo.
     */
    public boolean eliminarCola(String nombre) {
        validarNombre(nombre, "cola");
        boolean existia = ejecutar(() -> amqpAdmin.getQueueProperties(nombre),
                "No se pudo consultar la cola '" + nombre + "'") != null;
        if (!existia) {
            log.info("Eliminacion de la cola {}: no existia", nombre);
            return false;
        }
        ejecutar(() -> amqpAdmin.deleteQueue(nombre),
                "No se pudo eliminar la cola '" + nombre + "'");
        log.info("Cola eliminada: {}", nombre);
        return true;
    }

    /** @return vacio si la cola no existe. */
    public Optional<ColaInfo> obtenerCola(String nombre) {
        validarNombre(nombre, "cola");
        Properties props = ejecutar(() -> amqpAdmin.getQueueProperties(nombre),
                "No se pudo consultar la cola '" + nombre + "'");
        if (props == null) {
            return Optional.empty();
        }
        return Optional.of(new ColaInfo(
                nombre,
                entero(props, RabbitAdmin.QUEUE_MESSAGE_COUNT),
                entero(props, RabbitAdmin.QUEUE_CONSUMER_COUNT)));
    }

    /** @return cuantos mensajes se descartaron. */
    public int vaciarCola(String nombre) {
        validarNombre(nombre, "cola");
        int descartados = ejecutar(() -> amqpAdmin.purgeQueue(nombre),
                "No se pudo vaciar la cola '" + nombre + "'");
        log.info("Cola {} vaciada: {} mensajes descartados", nombre, descartados);
        return descartados;
    }

    // ----------------------------------------------------------- exchanges

    public void crearExchange(String nombre, String tipo, boolean durable, boolean autoDelete) {
        validarNombre(nombre, "exchange");
        TipoExchange tipoExchange = TipoExchange.desde(tipo);

        Exchange exchange = switch (tipoExchange) {
            case DIRECT -> new DirectExchange(nombre, durable, autoDelete);
            case TOPIC -> new TopicExchange(nombre, durable, autoDelete);
            case FANOUT -> new FanoutExchange(nombre, durable, autoDelete);
            case HEADERS -> new HeadersExchange(nombre, durable, autoDelete);
        };

        ejecutar(() -> {
            amqpAdmin.declareExchange(exchange);
            return null;
        }, "No se pudo declarar el exchange '" + nombre + "'");
        log.info("Exchange declarado: {} (tipo={}, durable={}, autoDelete={})",
                nombre, tipoExchange.name().toLowerCase(), durable, autoDelete);
    }

    /**
     * Borrar un exchange es idempotente: si no existia, el broker responde con
     * exito igual. A diferencia de las colas, AmqpAdmin no ofrece forma de
     * consultar si un exchange existe, asi que no se puede distinguir un caso
     * del otro y la operacion no devuelve nada. El controlador responde 204
     * siempre, que es el comportamiento correcto para un DELETE idempotente.
     */
    public void eliminarExchange(String nombre) {
        validarNombre(nombre, "exchange");
        ejecutar(() -> amqpAdmin.deleteExchange(nombre),
                "No se pudo eliminar el exchange '" + nombre + "'");
        log.info("Exchange eliminado (o no existia): {}", nombre);
    }

    // ------------------------------------------------------------ bindings

    public void crearBinding(String cola, String exchange, String routingKey) {
        validarNombre(cola, "cola");
        validarNombre(exchange, "exchange");
        Binding binding = binding(cola, exchange, routingKey);
        ejecutar(() -> {
            amqpAdmin.declareBinding(binding);
            return null;
        }, "No se pudo declarar el binding " + exchange + " -> " + cola);
        log.info("Binding declarado: {} -> {} con routing key '{}'", exchange, cola, routingKey);
    }

    public void eliminarBinding(String cola, String exchange, String routingKey) {
        validarNombre(cola, "cola");
        validarNombre(exchange, "exchange");
        Binding binding = binding(cola, exchange, routingKey);
        ejecutar(() -> {
            amqpAdmin.removeBinding(binding);
            return null;
        }, "No se pudo eliminar el binding " + exchange + " -> " + cola);
        log.info("Binding eliminado: {} -> {} con routing key '{}'", exchange, cola, routingKey);
    }

    private Binding binding(String cola, String exchange, String routingKey) {
        return new Binding(cola, Binding.DestinationType.QUEUE, exchange,
                routingKey == null ? "" : routingKey, null);
    }

    // ------------------------------------------------------------ internos

    private void validarNombre(String nombre, String que) {
        if (!tieneValor(nombre)) {
            throw new IllegalArgumentException("El nombre de la " + que + " no puede estar vacio");
        }
        if (nombre.trim().toLowerCase().startsWith(PREFIJO_RESERVADO)) {
            throw new IllegalArgumentException(
                    "El prefijo '" + PREFIJO_RESERVADO + "' esta reservado por RabbitMQ, no se puede usar en "
                            + que + " '" + nombre + "'");
        }
    }

    private boolean tieneValor(String valor) {
        return valor != null && !valor.isBlank();
    }

    /** AmqpException no extiende NestedRuntimeException, asi que no trae getMostSpecificCause(). */
    private Throwable causaRaiz(Throwable ex) {
        Throwable actual = ex;
        while (actual.getCause() != null && actual.getCause() != actual) {
            actual = actual.getCause();
        }
        return actual;
    }

    private int entero(Properties props, Object clave) {
        Object valor = props.get(clave);
        return valor instanceof Number n ? n.intValue() : 0;
    }

    /**
     * Toda llamada al broker pasa por aca. AmqpException cubre tanto el broker
     * caido como el rechazo del propio broker (por ejemplo, redeclarar una cola
     * existente con argumentos distintos, que cierra el canal). Se traduce a
     * IllegalStateException para que el manejador de errores de la capa web lo
     * devuelva como 409 sin tener que conocer las excepciones de AMQP.
     */
    private <T> T ejecutar(java.util.function.Supplier<T> operacion, String mensajeError) {
        try {
            return operacion.get();
        } catch (AmqpConnectException ex) {
            log.error("{}: el broker no responde", mensajeError);
            throw new BrokerNoDisponibleException("No hay conexion con el broker RabbitMQ", ex);
        } catch (AmqpException ex) {
            String detalle = causaRaiz(ex).getMessage();
            log.error("{}: {}", mensajeError, detalle);
            throw new IllegalStateException(mensajeError + ": " + detalle, ex);
        }
    }
}
