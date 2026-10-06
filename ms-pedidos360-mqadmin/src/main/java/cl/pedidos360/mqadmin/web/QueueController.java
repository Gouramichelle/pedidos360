package cl.pedidos360.mqadmin.web;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.pedidos360.mqadmin.admin.RabbitAdminService;
import cl.pedidos360.mqadmin.web.MqAdminDtos.ColaResponse;
import cl.pedidos360.mqadmin.web.MqAdminDtos.ColaVaciadaResponse;
import cl.pedidos360.mqadmin.web.MqAdminDtos.CrearColaRequest;
import jakarta.validation.Valid;

/**
 * Administracion de colas.
 *
 * Solo Admin: estos endpoints pueden borrar las colas de las que depende el
 * resto del sistema, asi que no se abren a Operador ni a Cliente. El token se
 * valida igual que en el resto de los microservicios (firma, issuer, vigencia
 * y audiencia), y ademas se exige el rol.
 *
 * Esta clase no importa nada de org.springframework.amqp: traduce HTTP a
 * llamadas de RabbitAdminService y de vuelta.
 */
@RestController
@RequestMapping("/api/mqadmin/queues")
@PreAuthorize("hasRole('Admin')")
public class QueueController {

    private final RabbitAdminService service;

    public QueueController(RabbitAdminService service) {
        this.service = service;
    }

    /** Declarar es idempotente en RabbitMQ: repetirlo con los mismos argumentos no falla. */
    @PostMapping
    public ResponseEntity<ColaResponse> crear(@Valid @RequestBody CrearColaRequest body) {
        service.crearCola(body.nombre(), body.durable(), body.autoDelete(),
                body.deadLetterExchange(), body.deadLetterRoutingKey());
        return service.obtenerCola(body.nombre())
                .map(info -> ResponseEntity.status(201).body(ColaResponse.from(info)))
                .orElseGet(() -> ResponseEntity.status(201).body(new ColaResponse(body.nombre(), 0, 0)));
    }

    @GetMapping("/{nombre}")
    public ColaResponse obtener(@PathVariable String nombre) {
        return service.obtenerCola(nombre)
                .map(ColaResponse::from)
                .orElseThrow(() -> new NotFoundException("No existe la cola " + nombre));
    }

    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> eliminar(@PathVariable String nombre) {
        if (!service.eliminarCola(nombre)) {
            throw new NotFoundException("No existe la cola " + nombre);
        }
        return ResponseEntity.noContent().build();
    }

    /** Descarta los mensajes encolados sin borrar la cola ni sus bindings. */
    @DeleteMapping("/{nombre}/messages")
    public ColaVaciadaResponse vaciar(@PathVariable String nombre) {
        return new ColaVaciadaResponse(nombre, service.vaciarCola(nombre));
    }
}
