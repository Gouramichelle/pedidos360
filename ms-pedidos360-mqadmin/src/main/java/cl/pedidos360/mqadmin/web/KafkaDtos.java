package cl.pedidos360.mqadmin.web;

import java.util.List;
import java.util.Map;

import cl.pedidos360.mqadmin.admin.GrupoConsumidorInfo;
import cl.pedidos360.mqadmin.admin.TopicoInfo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Contratos de la API de administracion de Kafka.
 *
 * La validacion de formato vive aqui, igual que en MqAdminDtos: una peticion
 * malformada se rechaza con 400 antes de llegar al cluster. Las reglas que
 * dependen de Kafka —que no se pueden reducir particiones, que el factor de
 * replicacion no puede superar el numero de brokers— las impone el propio
 * broker y se traducen en KafkaAdminService.
 */
public class KafkaDtos {

    /**
     * Kafka admite letras, numeros, punto, guion y guion bajo. Se aplica el
     * mismo criterio que a las colas: nada de espacios ni acentos, para que la
     * topologia se pueda operar desde la consola y los scripts.
     */
    private static final String NOMBRE_TOPICO = "^[A-Za-z0-9._-]+$";
    private static final String MSG_NOMBRE =
            "solo admite letras, numeros, punto, guion y guion bajo";

    // -------------------------------------------------------------- topicos

    public record CrearTopicoRequest(
            @NotBlank(message = "el nombre del topico es obligatorio")
            @Size(max = 249, message = "Kafka no admite nombres de mas de 249 caracteres")
            @Pattern(regexp = NOMBRE_TOPICO, message = MSG_NOMBRE)
            String nombre,

            // Las particiones fijan el paralelismo maximo: un grupo nunca tendra
            // mas consumidores utiles que particiones tiene el topico.
            @NotNull(message = "hay que indicar cuantas particiones")
            @Min(value = 1, message = "como minimo una particion")
            @Max(value = 100, message = "mas de 100 particiones no tiene sentido en este sistema")
            Integer particiones,

            // El factor de replicacion no puede superar el numero de brokers.
            @NotNull(message = "hay que indicar el factor de replicacion")
            @Min(value = 1, message = "como minimo una replica")
            @Max(value = 3, message = "el cluster tiene tres brokers")
            Integer replicas,

            /** retention.ms, cleanup.policy, min.insync.replicas y demas. Opcional. */
            Map<String, String> configuracion) {
    }

    public record ActualizarConfiguracionRequest(
            @NotEmpty(message = "hay que indicar al menos una configuracion")
            Map<String, String> configuracion) {
    }

    public record AmpliarParticionesRequest(
            @NotNull(message = "hay que indicar el total de particiones")
            @Min(value = 1, message = "como minimo una particion")
            @Max(value = 100, message = "mas de 100 particiones no tiene sentido en este sistema")
            Integer total) {
    }

    public record TopicoResponse(
            String nombre,
            int particiones,
            int replicas,
            Map<String, String> configuracion,
            List<ParticionResponse> detalle) {

        public static TopicoResponse from(TopicoInfo info) {
            int replicas = info.particiones().isEmpty() ? 0 : info.particiones().get(0).replicas().size();
            return new TopicoResponse(
                    info.nombre(),
                    info.particiones().size(),
                    replicas,
                    info.configuracion(),
                    info.particiones().stream().map(ParticionResponse::from).toList());
        }
    }

    public record ParticionResponse(int particion, int lider, List<Integer> replicas, List<Integer> sincronizadas) {

        static ParticionResponse from(TopicoInfo.ParticionInfo p) {
            return new ParticionResponse(p.particion(), p.lider(), p.replicas(), p.sincronizadas());
        }
    }

    // ------------------------------------------- grupos de consumidores

    public record GrupoResponse(
            String grupo,
            String estado,
            int miembros,
            long retrasoTotal,
            List<RetrasoResponse> retrasos) {

        public static GrupoResponse from(GrupoConsumidorInfo info) {
            return new GrupoResponse(
                    info.grupo(),
                    info.estado(),
                    info.miembros(),
                    info.retrasos().stream().mapToLong(GrupoConsumidorInfo.RetrasoParticion::retraso).sum(),
                    info.retrasos().stream().map(RetrasoResponse::from).toList());
        }
    }

    public record RetrasoResponse(String topico, int particion, long confirmado, long ultimo, long retraso) {

        static RetrasoResponse from(GrupoConsumidorInfo.RetrasoParticion r) {
            return new RetrasoResponse(r.topico(), r.particion(), r.confirmado(), r.ultimo(), r.retraso());
        }
    }
}
