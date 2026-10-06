package cl.pedidos360.notify.messaging.comun;

/**
 * Fallo permanente: el problema esta en el mensaje mismo. Un payload sin los
 * campos obligatorios, un identificador que no corresponde a nada, un estado
 * que no existe. Reintentarlo va a fallar exactamente igual las veces que haga
 * falta, asi que reintentar solo gasta recursos y retrasa el resto de la cola.
 *
 * Se traduce en un NACK sin requeue, que manda el mensaje directo a la DLQ
 * para revisarlo a mano.
 */
public class ErrorNoRecuperable extends RuntimeException {

    public ErrorNoRecuperable(String message) {
        super(message);
    }

    public ErrorNoRecuperable(String message, Throwable cause) {
        super(message, cause);
    }
}
