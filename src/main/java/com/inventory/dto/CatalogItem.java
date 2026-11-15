package com.inventory.dto;

import com.inventory.model.Product;
import java.math.BigDecimal;

/** What the sales screen needs to know about a product. Deliberately has no cost price. */
public record CatalogItem(Long id, String name, String sku, String category, BigDecimal sellingPrice, int quantity) {
    public static CatalogItem from(Product p) {
        return new CatalogItem(p.getId(), p.getName(), p.getSku(), p.getCategory(), p.getSellingPrice(), p.getQuantity());
    }
}
