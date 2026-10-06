package cl.pedidos360.mqadmin.admin;

/**
 * El broker no esta alcanzable. Se distingue de un rechazo del broker (que es
 * un conflicto, 409) porque aqui no hay nada que corregir en la peticion: es
 * la infraestructura la que no responde, y corresponde un 503.
 *
 * Existe para que la capa web no tenga que importar las excepciones de AMQP
 * solo para distinguir estos dos casos.
 */
public class BrokerNoDisponibleException extends RuntimeException {
    public BrokerNoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}
