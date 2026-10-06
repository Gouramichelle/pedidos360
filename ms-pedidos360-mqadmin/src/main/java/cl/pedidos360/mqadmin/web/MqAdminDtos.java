package cl.pedidos360.mqadmin.web;

import cl.pedidos360.mqadmin.admin.ColaInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Contratos de entrada y salida de la API de administracion.
 *
 * Toda la validacion de formato vive aqui, en anotaciones sobre los records,
 * de modo que una peticion malformada se rechaza con 400 antes de que el
 * controlador o el servicio lleguen a ejecutarse. Las reglas propias del
 * broker (prefijos reservados, tipos de exchange validos) se validan en
 * RabbitAdminService, porque dependen de RabbitMQ y no del formato HTTP.
 */
public class MqAdminDtos {

    /**
     * Caracteres admitidos en nombres de colas y exchanges. RabbitMQ acepta
     * casi cualquier UTF-8, pero permitir espacios o acentos vuelve la
     * topologia dificil de operar desde la consola y los scripts. Se restringe
     * a lo que ya usa el sistema (q.cmd.email, cmd.dead.dlx).
     */
    private static final String NOMBRE = "^[A-Za-z0-9._:-]+$";
    private static final String MSG_NOMBRE =
            "solo admite letras, numeros, punto, guion, guion bajo y dos puntos";

    /** Las routing keys ademas admiten los comodines de topic: * y #. */
    private static final String ROUTING_KEY = "^[A-Za-z0-9._:#*-]*$";

    // ---------------------------------------------------------------- colas

    public record CrearColaRequest(
            @NotBlank(message = "el nombre de la cola es obligatorio")
            @Size(max = 255, message = "el nombre no puede superar 255 caracteres")
            @Pattern(regexp = NOMBRE, message = MSG_NOMBRE)
            String nombre,

            Boolean durable,
            Boolean autoDelete,

            @Size(max = 255) @Pattern(regexp = NOMBRE, message = MSG_NOMBRE)
            String deadLetterExchange,

            @Size(max = 255) @Pattern(regexp = ROUTING_KEY, message = MSG_NOMBRE)
            String deadLetterRoutingKey) {

        /**
         * Una cola sin especificar nace durable: sobrevive al reinicio del
         * broker, que es lo que se quiere por defecto para colas de comandos.
         */
        public CrearColaRequest {
            durable = durable == null || durable;
            autoDelete = autoDelete != null && autoDelete;
        }
    }

    public record ColaResponse(String nombre, int mensajes, int consumidores) {
        public static ColaResponse from(ColaInfo info) {
            return new ColaResponse(info.nombre(), info.mensajes(), info.consumidores());
        }
    }

    public record ColaVaciadaResponse(String nombre, int mensajesDescartados) {
    }

    // ----------------------------------------------------------- exchanges

    public record CrearExchangeRequest(
            @NotBlank(message = "el nombre del exchange es obligatorio")
            @Size(max = 255, message = "el nombre no puede superar 255 caracteres")
            @Pattern(regexp = NOMBRE, message = MSG_NOMBRE)
            String nombre,

            @NotBlank(message = "el tipo es obligatorio: direct, topic, fanout o headers")
            String tipo,

            Boolean durable,
            Boolean autoDelete) {

        public CrearExchangeRequest {
            durable = durable == null || durable;
            autoDelete = autoDelete != null && autoDelete;
        }
    }

    // ------------------------------------------------------------ bindings

    public record CrearBindingRequest(
            @NotBlank(message = "la cola destino es obligatoria")
            @Size(max = 255) @Pattern(regexp = NOMBRE, message = MSG_NOMBRE)
            String cola,

            @NotBlank(message = "el exchange origen es obligatorio")
            @Size(max = 255) @Pattern(regexp = NOMBRE, message = MSG_NOMBRE)
            String exchange,

            @Size(max = 255) @Pattern(regexp = ROUTING_KEY, message = MSG_NOMBRE)
            String routingKey) {
    }
}
