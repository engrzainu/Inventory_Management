package com.inventory.service;

import com.inventory.dto.CashRequest;
import com.inventory.dto.ProductRequest;
import com.inventory.dto.TradeRequest;
import com.inventory.model.*;
import com.inventory.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryServiceTest {
    ProductRepository products = mock(ProductRepository.class);
    CashAccountRepository accounts = mock(CashAccountRepository.class);
    CashTransactionRepository cashTx = mock(CashTransactionRepository.class);
    BusinessTransactionRepository bizTx = mock(BusinessTransactionRepository.class);
    SaleRepository saleRepo = mock(SaleRepository.class);
    SaleItemRepository saleItemRepo = mock(SaleItemRepository.class);
    InventoryService service = new InventoryService(products, accounts, cashTx, bizTx, saleRepo, saleItemRepo);
    CashAccount cash = new CashAccount(1L);

    @BeforeEach
    void setUp() {
        cash.setBalance(new BigDecimal("10000.00"));
        when(accounts.findMainAccountForUpdate()).thenReturn(Optional.of(cash));
        when(products.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));
    }

    @SuppressWarnings("unused")
    private Product product(int qty) {
        Product p = new Product();
        p.setName("Rice"); p.setSku("R1"); p.setCategory("Grains"); p.setQuantity(qty);
        p.setCostPrice(new BigDecimal("100")); p.setSellingPrice(new BigDecimal("150"));
        return p;
    }

    @Test
    void addingProductDebitsCashByQuantityTimesCost() {
        service.createProduct(new ProductRequest("Rice", "R1", "Grains", 20, 5,
            new BigDecimal("100"), new BigDecimal("150")), true);
        assertEquals(new BigDecimal("8000.00"), cash.getBalance());
        ArgumentCaptor<CashTransaction> c = ArgumentCaptor.forClass(CashTransaction.class);
        verify(cashTx).save(c.capture());
        assertEquals(new BigDecimal("-2000"), c.getValue().getAmount());
    }

    @Test
    void addingProductWithoutDebitLeavesCashAlone() {
        service.createProduct(new ProductRequest("Rice", "R1", "Grains", 20, 5,
            new BigDecimal("100"), new BigDecimal("150")), false);
        assertEquals(new BigDecimal("10000.00"), cash.getBalance());
        verify(cashTx, never()).save(any());
    }

    @Test
    void addingProductRejectedWhenCashIsInsufficient() {
        assertThrows(IllegalArgumentException.class, () -> service.createProduct(
            new ProductRequest("Rice", "R1", "Grains", 200, 5, new BigDecimal("100"), new BigDecimal("150")), true));
        verify(products, never()).save(any());
        assertEquals(new BigDecimal("10000.00"), cash.getBalance());
    }

    @Test
    void manualCashInAndOut() {
        service.addCash(new CashRequest(new BigDecimal("500"), "capital"));
        assertEquals(new BigDecimal("10500.00"), cash.getBalance());
        service.withdrawCash(new CashRequest(new BigDecimal("200"), "rent"));
        assertEquals(new BigDecimal("10300.00"), cash.getBalance());
        assertThrows(IllegalArgumentException.class,
            () -> service.withdrawCash(new CashRequest(new BigDecimal("99999"), "too much")));
    }
}
