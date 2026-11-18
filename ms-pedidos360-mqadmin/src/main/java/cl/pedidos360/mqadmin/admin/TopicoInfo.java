package cl.pedidos360.mqadmin.admin;

import java.util.List;
import java.util.Map;

/**
 * Estado de un topico tal como lo reporta el cluster.
 *
 * @param nombre         el topico
 * @param particiones    una entrada por particion, con donde vive y quien la lidera
 * @param configuracion  solo lo configurado a mano; lo heredado del broker se omite
 */
public record TopicoInfo(String nombre, List<ParticionInfo> particiones, Map<String, String> configuracion) {

    /**
     * @param particion     su numero, empezando en cero
     * @param lider         el broker que atiende lecturas y escrituras de esta particion
     * @param replicas      todos los brokers que guardan una copia
     * @param sincronizadas las copias al dia. Si quedan menos que min.insync.replicas,
     *                      el topico deja de aceptar escrituras
     */
    public record ParticionInfo(int particion, int lider, List<Integer> replicas, List<Integer> sincronizadas) {
    }
}
