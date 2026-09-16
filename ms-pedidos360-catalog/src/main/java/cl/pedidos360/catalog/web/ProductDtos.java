package cl.pedidos360.catalog.web;

import java.math.BigDecimal;

import cl.pedidos360.catalog.domain.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ProductDtos {

    public record ProductRequest(
            @NotBlank String sku,
            @NotBlank String name,
            @NotNull @DecimalMin("0.0") BigDecimal price,
            @NotNull @Min(0) Integer stock) {
    }

    public record StockChangeRequest(@NotNull @Min(1) Integer qty) {
    }

    public record ProductResponse(Long id, String sku, String name, BigDecimal price, Integer stock) {
        public static ProductResponse from(Product p) {
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getPrice(), p.getStock());
        }
    }
}
