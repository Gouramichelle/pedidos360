package cl.pedidos360.mqadmin.admin;

import java.util.List;

/**
 * Estado de un grupo de consumidores, con su retraso por particion.
 *
 * @param grupo     el identificador del grupo
 * @param estado    Stable, Empty, PreparingRebalance y demas
 * @param miembros  cuantos consumidores hay conectados ahora mismo
 * @param retrasos  una entrada por particion asignada
 */
public record GrupoConsumidorInfo(String grupo, String estado, int miembros, List<RetrasoParticion> retrasos) {

    /**
     * @param confirmado  el ultimo offset que el grupo dio por procesado
     * @param ultimo      el ultimo offset escrito en la particion
     * @param retraso     cuantos mensajes faltan por procesar. Creciendo sostenido
     *                    significa que el consumidor no da abasto
     */
    public record RetrasoParticion(String topico, int particion, long confirmado, long ultimo, long retraso) {
    }
}
