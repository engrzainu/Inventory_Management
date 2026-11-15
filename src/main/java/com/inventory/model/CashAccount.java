package com.inventory.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "cash_account")
public class CashAccount {
    @Id
    private Long id;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    public CashAccount() {}
    public CashAccount(Long id) { this.id = id; }
    public Long getId() { return id; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
}
