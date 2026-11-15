package com.inventory.service;

import com.inventory.dto.SaleRequest;
import com.inventory.dto.SaleResponse;
import com.inventory.model.*;
import com.inventory.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SalesServiceTest {
    ProductRepository products = mock(ProductRepository.class);
    CashAccountRepository accounts = mock(CashAccountRepository.class);
    CashTransactionRepository cashTx = mock(CashTransactionRepository.class);
    SaleRepository sales = mock(SaleRepository.class);
    SaleItemRepository saleItems = mock(SaleItemRepository.class);
    SalesService service = new SalesService(products, accounts, cashTx, sales, saleItems);
    CashAccount cash = new CashAccount(1L);
    Product rice = product(1L, "Rice", "R1", 10, "100", "150");
    Product oil = product(2L, "Oil", "O1", 5, "200", "300");

    static Product product(Long id, String name, String sku, int qty, String cost, String price) {
        Product p = new Product();
        ReflectionTestUtils.setField(p, "id", id);
        p.setName(name); p.setSku(sku); p.setCategory("Food"); p.setQuantity(qty);
        p.setCostPrice(new BigDecimal(cost)); p.setSellingPrice(new BigDecimal(price));
        return p;
    }

    @BeforeEach
    void setUp() {
        cash.setBalance(new BigDecimal("1000.00"));
        when(accounts.findMainAccountForUpdate()).thenReturn(Optional.of(cash));
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(rice));
        when(products.findByIdForUpdate(2L)).thenReturn(Optional.of(oil));
        when(sales.save(any(Sale.class))).thenAnswer(i -> {
            Sale s = i.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 7L);
            return s;
        });
    }

    @Test
    void cartSaleReducesStockAddsCashAndComputesProfit() {
        SaleResponse r = service.sell(new SaleRequest("Ada", null, List.of(
            new SaleRequest.Item(1L, 4, new BigDecimal("160")),      // price edited from 150 to 160
            new SaleRequest.Item(2L, 2, new BigDecimal("300"))), null, null));
        assertEquals(6, rice.getQuantity());
        assertEquals(3, oil.getQuantity());
        assertEquals(new BigDecimal("1000.00").add(new BigDecimal("1240")), cash.getBalance()); // 640 + 600
        assertEquals(new BigDecimal("1240"), r.total());
        assertEquals(new BigDecimal("800"), r.totalCost());   // 4*100 + 2*200
        assertEquals(new BigDecimal("440"), r.profit());
        assertEquals("RCP-000007", r.receiptNo());
        assertEquals(2, r.items().size());
    }

    @Test
    void sameProductOnTwoLinesIsCheckedAgainstTotalStock() {
        assertThrows(IllegalArgumentException.class, () -> service.sell(new SaleRequest(null, null, List.of(
            new SaleRequest.Item(1L, 6, new BigDecimal("150")),
            new SaleRequest.Item(1L, 5, new BigDecimal("140"))), null, null)));
        assertEquals(10, rice.getQuantity());
        assertEquals(new BigDecimal("1000.00"), cash.getBalance());
    }

    @Test
    void deletingSaleRestoresStockAndCash() {
        Sale sale = new Sale();
        ReflectionTestUtils.setField(sale, "id", 7L);
        sale.setReceiptNo("RCP-000007");
        sale.setTotal(new BigDecimal("640"));
        when(sales.findById(7L)).thenReturn(Optional.of(sale));
        when(saleItems.sumQuantityByProduct(7L)).thenReturn(Collections.singletonList(new Object[]{1L, 4L}));

        service.deleteSale(7L);

        assertEquals(14, rice.getQuantity());
        assertEquals(new BigDecimal("360.00"), cash.getBalance());
        assertTrue(sale.isDeleted());
    }

    @Test
    void deletingSaleIsRefusedWhenCashIsTooLow() {
        Sale sale = new Sale();
        sale.setTotal(new BigDecimal("5000"));
        when(sales.findById(7L)).thenReturn(Optional.of(sale));
        when(saleItems.sumQuantityByProduct(7L)).thenReturn(Collections.singletonList(new Object[]{1L, 4L}));
        assertThrows(IllegalArgumentException.class, () -> service.deleteSale(7L));
        assertFalse(sale.isDeleted());
    }

    private SaleRequest oneRice(String clientId, String soldAt) {
        return new SaleRequest(null, null, List.of(new SaleRequest.Item(1L, 2, new BigDecimal("150"))), clientId, soldAt);
    }

    @Test
    void uploadingTheSameOfflineSaleTwiceRecordsItOnce() {
        Sale first = new Sale();
        ReflectionTestUtils.setField(first, "id", 7L);
        first.setReceiptNo("RCP-000007");
        first.setTotal(new BigDecimal("300"));
        // 1st upload: unknown clientId, so it is recorded
        when(sales.findByClientId("abc-123")).thenReturn(Optional.empty());
        service.sell(oneRice("abc-123", null));
        assertEquals(8, rice.getQuantity());
        assertEquals(new BigDecimal("1300.00"), cash.getBalance());
        // retry: the server now knows the clientId, so nothing changes
        when(sales.findByClientId("abc-123")).thenReturn(Optional.of(first));
        SaleResponse again = service.sell(oneRice("abc-123", null));
        assertEquals("RCP-000007", again.receiptNo());
        assertEquals(8, rice.getQuantity());
        assertEquals(new BigDecimal("1300.00"), cash.getBalance());
    }

    @Test
    void offlineSaleKeepsItsRealSaleTime() {
        java.time.Instant when = java.time.Instant.now().minusSeconds(3 * 3600);
        SaleResponse r = service.sell(oneRice("off-1", when.toString()));
        java.time.LocalDateTime expected = when.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        assertEquals(expected, r.createdAt());
    }

    @Test
    void impossibleSaleTimesAreRejected() {
        String future = java.time.Instant.now().plusSeconds(3600).toString();
        String ancient = java.time.Instant.now().minusSeconds(40L * 24 * 3600).toString();
        assertThrows(IllegalArgumentException.class, () -> service.sell(oneRice("f", future)));
        assertThrows(IllegalArgumentException.class, () -> service.sell(oneRice("a", ancient)));
        assertThrows(IllegalArgumentException.class, () -> service.sell(oneRice("g", "yesterday")));
        assertEquals(10, rice.getQuantity());
    }

    @Test
    void dateRangesCoverTheRequestedPeriod() {
        DateRange today = DateRange.resolve("today", null, null);
        assertEquals(today.from().plusDays(1), today.to());
        DateRange custom = DateRange.resolve("custom", java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 31));
        assertEquals(java.time.LocalDate.of(2026, 2, 1).atStartOfDay(), custom.to());
        assertThrows(IllegalArgumentException.class, () -> DateRange.resolve("custom", null, null));
        assertThrows(IllegalArgumentException.class, () -> DateRange.resolve("custom",
            java.time.LocalDate.of(2026, 2, 1), java.time.LocalDate.of(2026, 1, 1)));
    }
}
