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

    private final RestClient restClient;

    public CatalogClient(RestClient catalogRestClient) {
        this.restClient = catalogRestClient;
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
