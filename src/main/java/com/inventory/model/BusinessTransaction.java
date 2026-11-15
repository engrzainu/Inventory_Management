package com.inventory.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "business_transactions")
public class BusinessTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String type; // SALE or PURCHASE

    @ManyToOne(optional = false)
    private Product product;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal total;

    @Column(length = 120)
    private String partyName;

    @Column(length = 255)
    private String note;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }

    public BusinessTransaction() {}
    public BusinessTransaction(String type, Product product, int quantity, BigDecimal unitPrice,
                               BigDecimal total, String partyName, String note) {
        this.type = type;
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.total = total;
        this.partyName = partyName;
        this.note = note;
    }
    public Long getId() { return id; }
    public String getType() { return type; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTotal() { return total; }
    public String getPartyName() { return partyName; }
    public String getNote() { return note; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
