package com.inventory.dto;

import com.inventory.model.Sale;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record SaleResponse(Long id, String receiptNo, String customerName, String note,
        BigDecimal total, BigDecimal totalCost, BigDecimal profit, LocalDateTime createdAt, List<Item> items) {

    public record Item(Long productId, String name, String sku, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}

    /** Call inside a transaction: reads the sale's items. */
    public static SaleResponse from(Sale s) {
        List<Item> items = s.getItems().stream()
            .map(i -> new Item(i.getProductId(), i.getProductName(), i.getSku(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal()))
            .toList();
        return new SaleResponse(s.getId(), s.getReceiptNo(), s.getCustomerName(), s.getNote(),
            s.getTotal(), s.getTotalCost(), s.getProfit(), s.getCreatedAt(), items);
    }
}
