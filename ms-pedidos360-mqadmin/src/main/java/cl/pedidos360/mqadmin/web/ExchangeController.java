package cl.pedidos360.mqadmin.web;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.pedidos360.mqadmin.admin.RabbitAdminService;
import cl.pedidos360.mqadmin.web.MqAdminDtos.CrearExchangeRequest;
import jakarta.validation.Valid;

/** Administracion de exchanges. Solo Admin, por el mismo motivo que las colas. */
@RestController
@RequestMapping("/api/mqadmin/exchanges")
@PreAuthorize("hasRole('Admin')")
public class ExchangeController {

    private final RabbitAdminService service;

    public ExchangeController(RabbitAdminService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody CrearExchangeRequest body) {
        service.crearExchange(body.nombre(), body.tipo(), body.durable(), body.autoDelete());
        return ResponseEntity.status(201).body(Map.of(
                "nombre", body.nombre(),
                "tipo", body.tipo().toLowerCase(),
                "durable", body.durable(),
                "autoDelete", body.autoDelete()));
    }

    /**
     * Siempre 204, incluso si el exchange no existia: el broker no permite
     * distinguir los dos casos y DELETE es idempotente por definicion.
     */
    @DeleteMapping("/{nombre}")
    public ResponseEntity<Void> eliminar(@PathVariable String nombre) {
        service.eliminarExchange(nombre);
        return ResponseEntity.noContent().build();
    }
}
