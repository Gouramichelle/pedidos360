package cl.pedidos360.mqadmin.web;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.pedidos360.mqadmin.admin.KafkaAdminService;
import cl.pedidos360.mqadmin.web.KafkaDtos.ActualizarConfiguracionRequest;
import cl.pedidos360.mqadmin.web.KafkaDtos.AmpliarParticionesRequest;
import cl.pedidos360.mqadmin.web.KafkaDtos.CrearTopicoRequest;
import cl.pedidos360.mqadmin.web.KafkaDtos.TopicoResponse;
import jakarta.validation.Valid;

/**
 * Administracion de topicos de Kafka.
 *
 * Solo Admin, por la misma razon que las colas: estos endpoints pueden borrar
 * el topico del que depende toda la auditoria del sistema.
 *
 * Esta clase no importa nada de org.apache.kafka: traduce HTTP a llamadas de
 * KafkaAdminService y de vuelta.
 */
@RestController
@RequestMapping("/api/mqadmin/kafka/topics")
@PreAuthorize("hasRole('Admin')")
public class KafkaTopicController {

    private final KafkaAdminService service;

    public KafkaTopicController(KafkaAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<String> listar() {
        return service.listarTopicos();
    }

    @PostMapping
    public ResponseEntity<TopicoResponse> crear(@Valid @RequestBody CrearTopicoRequest body) {
        service.crearTopico(body.nombre(), body.particiones(),
                body.replicas().shortValue(), body.configuracion());
        return service.describirTopico(body.nombre())
                .map(info -> ResponseEntity.status(201).body(TopicoResponse.from(info)))
                .orElseGet(() -> ResponseEntity.status(201).body(new TopicoResponse(
                        body.nombre(), body.particiones(), body.replicas(), Map.of(), List.of())));
    }

    @GetMapping("/{nombre}")
    public TopicoResponse obtener(@PathVariable String nombre) {
        return service.describirTopico(nombre)
                .map(TopicoResponse::from)
                .orElseThrow(() -> new NotFoundException("No existe el topico " + nombre));
    }

    /** Cambia configuraciones en caliente: retention.ms, cleanup.policy y demas. */
    @PatchMapping("/{nombre}/config")
    public TopicoResponse actualizarConfiguracion(
            @PathVariable String nombre,
            @Valid @RequestBody ActualizarConfiguracionRequest body) {
        if (service.describirTopico(nombre).isEmpty()) {
            throw new NotFoundException("No existe el topico " + nombre);
        }
        service.actualizarConfiguraciones(nombre, body.configuracion());
        return service.describirTopico(nombre)
                .map(TopicoResponse::from)
                .orElseThrow(() -> new NotFoundException("No existe el topico " + nombre));
    }

    /**
     * Kafka solo permite aumentar particiones, nunca reducirlas: los mensajes
     * ya repartidos no se pueden reubicar sin romper el orden por clave.
     */
    @PostMapping("/{nombre}/partitions")
    public TopicoResponse ampliarParticiones(
            @PathVariable String nombre,
            @Valid @RequestBody AmpliarParticionesRequest body) {
        if (service.describirTopico(nombre).isEmpty()) {
            throw new NotFoundException("No existe el topico " + nombre);
        }
        service.ampliarParticiones(nombre, body.total());
        return service.describirTopico(nombre)
                .map(TopicoResponse::from)
                .orElseThrow(() -> new NotFoundException("No existe el topico " + nombre));
    }

    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> eliminar(@PathVariable String nombre) {
        if (!service.eliminarTopico(nombre)) {
            throw new NotFoundException("No existe el topico " + nombre);
        }
        return ResponseEntity.noContent().build();
    }
}
