package cl.pedidos360.report.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Ultima intervencion registrada sobre cada pedido: quien la hizo, desde donde
 * y cuando.
 *
 * Se construye desde audit.timeline y no desde orders.events, por dos razones.
 *
 * La primera es que orders.events no lleva el origen de la accion: ese campo
 * lo agrega ms-audit al derivar el registro de trazabilidad.
 *
 * La segunda es de diseno. audit.timeline esta compactado por el id del
 * pedido, asi que el broker conserva el ultimo mensaje de cada uno y descarta
 * los anteriores. Para reconstruir esta proyeccion desde cero basta con leer
 * el topico entero una vez: no hay que replicar todo el historial de eventos,
 * solo el ultimo estado de cada pedido. Esa es exactamente la diferencia entre
 * un topico de eventos y uno compactado.
 */
@Entity
@Table(name = "order_activity")
public class OrderActivity {

    @Id
    private Long orderId;

    @Column(nullable = false, length = 60)
    private String lastEventType;

    @Column(nullable = false, length = 120)
    private String lastActor;

    @Column(length = 60)
    private String lastSource;

    @Column(nullable = false, length = 20)
    private String lastStatus;

    @Column(nullable = false)
    private Instant lastAt;

    /** Cuantas veces se toco el pedido. Da una idea de si costo sacarlo adelante. */
    @Column(nullable = false)
    private int transitions;

    protected OrderActivity() {
    }

    public OrderActivity(Long orderId) {
        this.orderId = orderId;
        this.transitions = 0;
    }

    /**
     * Aplica un registro de la linea de tiempo.
     *
     * Los mensajes mas viejos se ignoran: un topico compactado puede entregar
     * un mensaje anterior despues de una recuperacion, y no tendria sentido
     * que la ultima actividad retrocediera en el tiempo.
     */
    public void registrar(String eventType, String actor, String source, String status, Instant occurredAt) {
        if (lastAt != null && occurredAt.isBefore(lastAt)) {
            return;
        }
        this.lastEventType = eventType;
        this.lastActor = actor;
        this.lastSource = source;
        this.lastStatus = status;
        this.lastAt = occurredAt;
        this.transitions++;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getLastEventType() {
        return lastEventType;
    }

    public String getLastActor() {
        return lastActor;
    }

    public String getLastSource() {
        return lastSource;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public Instant getLastAt() {
        return lastAt;
    }

    public int getTransitions() {
        return transitions;
    }
}
