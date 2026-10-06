package cl.pedidos360.mqadmin.admin;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Tipos de exchange que acepta la API. Existe para que el tipo no viaje como
 * String suelto hasta el fondo del servicio: cualquier valor invalido se
 * rechaza en un solo lugar y con un mensaje que dice cuales son los validos.
 */
public enum TipoExchange {

    DIRECT,
    TOPIC,
    FANOUT,
    HEADERS;

    /** Acepta el valor sin distinguir mayusculas ("topic", "TOPIC", "Topic"). */
    public static TipoExchange desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El tipo de exchange es obligatorio. Validos: " + validos());
        }
        try {
            return TipoExchange.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Tipo de exchange invalido: '" + valor + "'. Validos: " + validos());
        }
    }

    public static String validos() {
        return Arrays.stream(values())
                .map(t -> t.name().toLowerCase())
                .collect(Collectors.joining(", "));
    }
}
