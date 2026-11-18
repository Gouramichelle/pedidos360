package cl.pedidos360.mqadmin.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.pedidos360.mqadmin.admin.KafkaAdminService;
import cl.pedidos360.mqadmin.web.KafkaDtos.GrupoResponse;

/**
 * Inspeccion de los grupos de consumidores.
 *
 * El dato que importa aqui es el retraso: cuantos mensajes hay publicados que
 * un grupo todavia no proceso. Un retraso que crece de forma sostenida avisa
 * de que un consumidor no da abasto antes de que se note en la aplicacion.
 *
 * Es la misma informacion que muestra Kafka UI, expuesta como API para poder
 * consultarla desde un script o un monitor.
 */
@RestController
@RequestMapping("/api/mqadmin/kafka/consumer-groups")
@PreAuthorize("hasRole('Admin')")
public class KafkaConsumerGroupController {

    private final KafkaAdminService service;

    public KafkaConsumerGroupController(KafkaAdminService service) {
        this.service = service;
    }

    @GetMapping
    public List<String> listar() {
        return service.listarGrupos();
    }

    @GetMapping("/{grupo}")
    public GrupoResponse obtener(@PathVariable String grupo) {
        return service.describirGrupo(grupo)
                .map(GrupoResponse::from)
                .orElseThrow(() -> new NotFoundException("No existe el grupo " + grupo));
    }

    /**
     * Solo funciona con el grupo parado: Kafka rechaza borrar un grupo que
     * tenga consumidores conectados.
     */
    @DeleteMapping("/{grupo}")
    public ResponseEntity<Void> eliminar(@PathVariable String grupo) {
        if (!service.eliminarGrupo(grupo)) {
            throw new NotFoundException("No existe el grupo " + grupo);
        }
        return ResponseEntity.noContent().build();
    }
}
