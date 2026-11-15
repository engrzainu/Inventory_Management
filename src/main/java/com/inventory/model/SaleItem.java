package com.inventory.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "sale_items")
public class SaleItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id")
    private Sale sale;

    @Column(nullable = false)
    private Long productId;

    // Name and SKU are copied so old sales and receipts stay readable if the product is renamed.
    @Column(nullable = false, length = 120)
    private String productName;

    @Column(nullable = false, length = 50)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;

    public SaleItem() {}
    public SaleItem(Product p, int quantity, BigDecimal unitPrice) {
        this.productId = p.getId();
        this.productName = p.getName();
        this.sku = p.getSku();
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.unitCost = p.getCostPrice();
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    void setSale(Sale sale) { this.sale = sale; }
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
