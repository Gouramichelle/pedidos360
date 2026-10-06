package cl.pedidos360.notify.messaging.comun;

/**
 * Fallo transitorio: el mensaje esta bien, lo que fallo es el entorno. Un
 * proveedor de correo caido, un timeout de red, una base de datos que no
 * responde. Reintentar el mismo mensaje mas tarde tiene sentido, porque la
 * causa puede desaparecer sola.
 *
 * Se traduce en un NACK con requeue, hasta agotar los intentos.
 */
public class ErrorRecuperable extends RuntimeException {

    public ErrorRecuperable(String message) {
        super(message);
    }

    public ErrorRecuperable(String message, Throwable cause) {
        super(message, cause);
    }
}
