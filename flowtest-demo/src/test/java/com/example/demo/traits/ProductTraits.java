package com.example.demo.traits;

import com.example.demo.entity.Product;
import com.flowtest.core.fixture.Trait;

import java.math.BigDecimal;

/**
 * Trait definitions for Product entity.
 */
public final class ProductTraits {

    private ProductTraits() {
    }

    /**
     * Sets the product price.
     */
    public static Trait<Product> price(double price) {
        return product -> product.setPrice(BigDecimal.valueOf(price));
    }

    /**
     * Product with stock.
     */
    public static Trait<Product> inStock(int quantity) {
        return product -> {
            product.setStock(quantity);
            product.setStatus(Product.ProductStatus.IN_STOCK);
        };
    }

    /**
     * Product out of stock.
     */
    public static Trait<Product> outOfStock() {
        return product -> {
            product.setStock(0);
            product.setStatus(Product.ProductStatus.OUT_OF_STOCK);
        };
    }

    /**
     * Discontinued product.
     */
    public static Trait<Product> discontinued() {
        return product -> product.setStatus(Product.ProductStatus.DISCONTINUED);
    }

    /**
     * Sets product name.
     */
    public static Trait<Product> name(String name) {
        return product -> product.setName(name);
    }

    /**
     * Sets product description.
     */
    public static Trait<Product> description(String description) {
        return product -> product.setDescription(description);
    }

    /**
     * Cheap product (common combination).
     */
    public static Trait<Product> cheap() {
        return price(9.99).and(inStock(100));
    }

    /**
     * Expensive product (common combination).
     */
    public static Trait<Product> expensive() {
        return price(999.99).and(inStock(10));
    }
}
