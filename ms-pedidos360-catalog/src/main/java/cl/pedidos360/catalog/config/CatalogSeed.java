package cl.pedidos360.catalog.config;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import cl.pedidos360.catalog.domain.Product;
import cl.pedidos360.catalog.domain.ProductRepository;

/** Datos semilla para la demo. Solo inserta si la tabla esta vacia. */
@Configuration
@Profile("!test")
public class CatalogSeed {

    @Bean
    CommandLineRunner seedProducts(ProductRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            repository.saveAll(List.of(
                    new Product("PIZZA-MARG", "Pizza Margarita", new BigDecimal("8990.00"), 50),
                    new Product("PIZZA-PEPP", "Pizza Pepperoni", new BigDecimal("10990.00"), 40),
                    new Product("EMP-PINO", "Empanada de Pino", new BigDecimal("2490.00"), 200),
                    new Product("BEB-COLA-1L", "Bebida Cola 1L", new BigDecimal("1890.00"), 120),
                    new Product("POSTRE-TIRA", "Tiramisu individual", new BigDecimal("4590.00"), 30)));
        };
    }
}
