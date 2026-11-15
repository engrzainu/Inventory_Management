package com.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ReportResponse(
    String label, LocalDate from, LocalDate to,
    int productCount, long unitsInStock,
    BigDecimal totalProductsValue,     // stock at cost price
    BigDecimal expectedProductValue,   // stock at selling price
    BigDecimal expectedProfit,         // expected value minus cost value
    int salesCount, long unitsSold,
    BigDecimal salesRevenue, BigDecimal salesCost, BigDecimal salesProfit,
    List<ProductRow> products
) {
    public record ProductRow(Long id, String name, String sku, String category, int quantity, int reorderLevel,
            BigDecimal costPrice, BigDecimal sellingPrice, BigDecimal stockValue, BigDecimal expectedValue,
            BigDecimal expectedProfit, long unitsSold, BigDecimal revenue, BigDecimal profit) {}
}
