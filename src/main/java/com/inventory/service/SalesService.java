package com.inventory.service;

import com.inventory.dto.*;
import com.inventory.model.*;
import com.inventory.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class SalesService {
    private final ProductRepository products;
    private final CashAccountRepository accounts;
    private final CashTransactionRepository cashTransactions;
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;

    public SalesService(ProductRepository products, CashAccountRepository accounts,
            CashTransactionRepository cashTransactions, SaleRepository sales, SaleItemRepository saleItems) {
        this.products = products;
        this.accounts = accounts;
        this.cashTransactions = cashTransactions;
        this.sales = sales;
        this.saleItems = saleItems;
    }

    /**
     * Records a whole cart in one database transaction: stock goes down, cash balance goes up by the
     * cart total, and the sale is saved. If any line fails (for example not enough stock) nothing is saved.
     * Locks are always taken product by product in id order, then cash, so concurrent sales cannot deadlock.
     */
    @Transactional
    public SaleResponse sell(SaleRequest r) {
        String clientId = r.clientId() == null || r.clientId().isBlank() ? null : r.clientId().trim();
        if (clientId != null) {   // a retry of a sale that was already recorded: return it, do not record it again
            Optional<Sale> existing = sales.findByClientId(clientId);
            if (existing.isPresent()) return SaleResponse.from(existing.get());
        }
        LocalDateTime soldAt = parseSoldAt(r.soldAt());
        Map<Long, Integer> wanted = new TreeMap<>();
        for (SaleRequest.Item item : r.items()) wanted.merge(item.productId(), item.quantity(), Integer::sum);

        Map<Long, Product> locked = new HashMap<>();
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            Product p = products.findByIdForUpdate(e.getKey())
                .orElseThrow(() -> new IllegalArgumentException("A product in the cart no longer exists."));
            if (p.getQuantity() < e.getValue()) {
                throw new IllegalArgumentException("Not enough stock for " + p.getName() + " (available " + p.getQuantity()
                    + ", requested " + e.getValue() + ").");
            }
            locked.put(e.getKey(), p);
        }
        CashAccount cash = lockedCash();

        Sale sale = new Sale();
        sale.setClientId(clientId);
        if (soldAt != null) sale.setCreatedAt(soldAt);
        sale.setCustomerName(blankToNull(r.customerName()));
        sale.setNote(blankToNull(r.note()));
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal cost = BigDecimal.ZERO;
        for (SaleRequest.Item item : r.items()) {
            Product p = locked.get(item.productId());
            SaleItem line = new SaleItem(p, item.quantity(), item.unitPrice());
            sale.addItem(line);
            total = total.add(line.getLineTotal());
            cost = cost.add(line.getUnitCost().multiply(BigDecimal.valueOf(line.getQuantity())));
        }
        for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
            Product p = locked.get(e.getKey());
            p.setQuantity(p.getQuantity() - e.getValue());
        }
        sale.setTotal(total);
        sale.setTotalCost(cost);
        sale = sales.save(sale);
        sale.setReceiptNo(String.format("RCP-%06d", sale.getId()));

        cash.setBalance(cash.getBalance().add(total));
        cashTransactions.save(new CashTransaction("SALE", total, cash.getBalance(),
            "Sale " + sale.getReceiptNo() + " (" + sale.getItems().size() + " item(s))" + (soldAt != null ? ", sold offline" : "")));
        return SaleResponse.from(sale);
    }

    /**
     * Deletes a sale and puts everything back as it was before the sale: the sold units return to stock
     * and the sale amount leaves cash balance. A reversing entry is written to the cash ledger.
     * Refused if cash balance is now lower than the sale amount (the money was already spent).
     */
    @Transactional
    public void deleteSale(Long id) {
        Sale sale = sales.findById(id).orElseThrow(() -> new IllegalArgumentException("Sale not found."));
        if (sale.isDeleted()) throw new IllegalArgumentException("This sale was already deleted.");

        // Read quantities without loading Product entities, then lock each product freshly (ordered by id).
        List<Object[]> rows = saleItems.sumQuantityByProduct(id);
        for (Object[] row : rows) {
            Long productId = ((Number) row[0]).longValue();
            int qty = ((Number) row[1]).intValue();
            Product p = products.findByIdForUpdate(productId)
                .orElseThrow(() -> new IllegalStateException("A product from this sale no longer exists."));
            p.setQuantity(p.getQuantity() + qty);
        }
        CashAccount cash = lockedCash();
        if (cash.getBalance().compareTo(sale.getTotal()) < 0) {
            throw new IllegalArgumentException("Cash balance is lower than this sale amount ("
                + sale.getTotal().toPlainString() + "), so the cash cannot be restored. Add cash first, then delete the sale.");
        }
        cash.setBalance(cash.getBalance().subtract(sale.getTotal()));
        cashTransactions.save(new CashTransaction("SALE_DELETED", sale.getTotal().negate(), cash.getBalance(),
            "Sale deleted: " + sale.getReceiptNo()));
        sale.setDeleted(true);
        sale.setDeletedAt(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public SalesListResponse history(DateRange range, int page, int size) {
        Page<Long> ids = sales.findActiveIdsInRange(range.from(), range.to(),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        Map<Long, Sale> byId = new HashMap<>();
        if (!ids.isEmpty()) for (Sale s : sales.findAllWithItemsByIdIn(ids.getContent())) byId.put(s.getId(), s);
        List<SaleResponse> list = ids.getContent().stream().map(byId::get).filter(Objects::nonNull).map(SaleResponse::from).toList();

        Object[] t = sales.totalsInRange(range.from(), range.to()).get(0);
        long count = ((Number) t[0]).longValue();
        BigDecimal revenue = new BigDecimal(t[1].toString());
        BigDecimal cost = new BigDecimal(t[2].toString());
        return new SalesListResponse(range.label(), range.firstDay(), range.lastDay(), count, revenue, cost,
            revenue.subtract(cost), new PageResponse<>(list, ids.getNumber(), ids.getSize(), ids.getTotalElements(), ids.getTotalPages()));
    }

    @Transactional(readOnly = true)
    public Optional<SaleResponse> findByClientId(String clientId) {
        return sales.findByClientId(clientId).map(SaleResponse::from);
    }

    private static LocalDateTime parseSoldAt(String value) {
        if (value == null || value.isBlank()) return null;
        LocalDateTime t;
        try {
            t = Instant.parse(value.trim()).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid sale time.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (t.isAfter(now.plusMinutes(5))) throw new IllegalArgumentException("The sale time is in the future. Check the clock on the device that made the sale.");
        if (t.isBefore(now.minusDays(30))) throw new IllegalArgumentException("This offline sale is more than 30 days old and cannot be uploaded automatically.");
        return t;
    }

    private CashAccount lockedCash() {
        return accounts.findMainAccountForUpdate()
            .orElseThrow(() -> new IllegalStateException("Cash balance account is not initialized."));
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
