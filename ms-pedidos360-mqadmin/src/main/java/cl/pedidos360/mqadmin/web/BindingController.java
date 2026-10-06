package cl.pedidos360.mqadmin.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cl.pedidos360.mqadmin.admin.RabbitAdminService;
import cl.pedidos360.mqadmin.web.MqAdminDtos.CrearBindingRequest;
import jakarta.validation.Valid;

/**
 * Administracion de bindings entre un exchange y una cola.
 *
 * El borrado va por query params y no por cuerpo: un binding no tiene
 * identificador propio, se identifica por la tripleta exchange + cola +
 * routing key, y un DELETE con cuerpo es ambiguo para proxies y clientes HTTP.
 */
@RestController
@RequestMapping("/api/mqadmin/bindings")
@PreAuthorize("hasRole('Admin')")
public class BindingController {

    private final RabbitAdminService service;

    public BindingController(RabbitAdminService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> crear(@Valid @RequestBody CrearBindingRequest body) {
        String routingKey = body.routingKey() == null ? "" : body.routingKey();
        service.crearBinding(body.cola(), body.exchange(), routingKey);
        return ResponseEntity.status(201).body(Map.of(
                "exchange", body.exchange(),
                "cola", body.cola(),
                "routingKey", routingKey));
    }

    @DeleteMapping
    public ResponseEntity<Void> eliminar(
            @RequestParam String cola,
            @RequestParam String exchange,
            @RequestParam(required = false, defaultValue = "") String routingKey) {
        service.eliminarBinding(cola, exchange, routingKey);
        return ResponseEntity.noContent().build();
    }
}
