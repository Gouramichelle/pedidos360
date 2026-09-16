package cl.pedidos360.catalog.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.pedidos360.catalog.domain.Product;
import cl.pedidos360.catalog.domain.ProductRepository;
import cl.pedidos360.catalog.web.ProductDtos.ProductRequest;
import cl.pedidos360.catalog.web.ProductDtos.ProductResponse;
import cl.pedidos360.catalog.web.ProductDtos.StockChangeRequest;
import jakarta.validation.Valid;

/**
 * Lectura abierta a cualquier usuario autenticado, escritura solo para Admin.
 * El descuento de stock lo puede hacer tambien Operador porque forma parte del
 * flujo de aceptacion de un pedido.
 */
@RestController
@RequestMapping("/api/catalog/products")
public class ProductController {

    private final ProductRepository repository;

    public ProductController(ProductRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public List<ProductResponse> list() {
        return repository.findAll().stream().map(ProductResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public ProductResponse get(@PathVariable Long id) {
        return ProductResponse.from(find(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest body) {
        if (repository.existsBySku(body.sku())) {
            throw new IllegalStateException("Ya existe un producto con el SKU " + body.sku());
        }
        Product saved = repository.save(new Product(body.sku(), body.name(), body.price(), body.stock()));
        return ResponseEntity.status(201).body(ProductResponse.from(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest body) {
        Product product = find(id);
        product.setSku(body.sku());
        product.setName(body.name());
        product.setPrice(body.price());
        product.setStock(body.stock());
        return ProductResponse.from(repository.save(product));
    }

    @PatchMapping("/{id}/stock/decrease")
    @PreAuthorize("hasAnyRole('Admin', 'Operador')")
    @Transactional
    public ProductResponse decreaseStock(@PathVariable Long id, @Valid @RequestBody StockChangeRequest body) {
        Product product = find(id);
        product.decreaseStock(body.qty());
        return ProductResponse.from(repository.save(product));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.delete(find(id));
        return ResponseEntity.noContent().build();
    }

    private Product find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("No existe el producto " + id));
    }
}
