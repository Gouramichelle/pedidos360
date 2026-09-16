package cl.pedidos360.orders.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Llamada sincrona, servicio a servicio, para descontar stock al aceptar un
 * pedido. Reenvia el Bearer token del usuario actual: ms-catalog valida ese
 * mismo token, asi que el permiso efectivo sigue siendo el del usuario que
 * acepto el pedido, no un permiso "de sistema" mas amplio.
 */
@Component
public class CatalogClient {

    public record StockDecreaseRequest(Integer qty) {
    }

    /** Solo los campos que ms-orders necesita de la respuesta de catalogo. */
    public record Product(Long id, String sku, java.math.BigDecimal price, Integer stock) {
    }

    private final RestClient restClient;

    public CatalogClient(RestClient catalogRestClient) {
        this.restClient = catalogRestClient;
    }

    /**
     * Precio y SKU se leen del catalogo y no del cuerpo de la peticion: si se
     * confiara en lo que envia el cliente, cualquiera podria crear un pedido
     * al precio que quisiera, y ademas los KPIs de ventas quedarian calculados
     * sobre cifras inventadas.
     */
    public Product obtenerProducto(Long productId, String bearerToken) {
        return restClient.get()
                .uri("/api/catalog/products/{id}", productId)
                .header("Authorization", bearerToken)
                .retrieve()
                .body(Product.class);
    }

    public void decreaseStock(Long productId, int qty, String bearerToken) {
        restClient.patch()
                .uri("/api/catalog/products/{id}/stock/decrease", productId)
                .header("Authorization", bearerToken)
                .body(new StockDecreaseRequest(qty))
                .retrieve()
                .toBodilessEntity();
    }
}
