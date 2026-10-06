package cl.pedidos360.mqadmin.admin;

/**
 * Estado de una cola, con los datos que el broker expone sin pasar por el
 * plugin de management: cuantos mensajes tiene encolados y cuantos
 * consumidores estan enganchados.
 */
public record ColaInfo(String nombre, int mensajes, int consumidores) {
}
