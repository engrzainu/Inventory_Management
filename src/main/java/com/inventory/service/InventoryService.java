package com.inventory.service;

import com.inventory.dto.*;
import com.inventory.model.*;
import com.inventory.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.List;
import java.util.Map;

@Service
public class InventoryService {
    private final ProductRepository products;
    private final CashAccountRepository accounts;
    private final CashTransactionRepository cashTransactions;
    private final BusinessTransactionRepository businessTransactions;
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;

    public InventoryService(ProductRepository products, CashAccountRepository accounts,
            CashTransactionRepository cashTransactions, BusinessTransactionRepository businessTransactions,
            SaleRepository sales, SaleItemRepository saleItems) {
        this.products = products;
        this.accounts = accounts;
        this.cashTransactions = cashTransactions;
        this.businessTransactions = businessTransactions;
        this.sales = sales;
        this.saleItems = saleItems;
    }

    @Transactional(readOnly = true)
    public PageResponse<Product> productsPage(String q, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").and(Sort.by("id")));
        if (q == null || q.isBlank()) return PageResponse.from(products.findAll(pageable));
        String term = q.trim();
        return PageResponse.from(products.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(term, term, pageable));
    }

    /** Small result list for the sales search box. */
    @Transactional(readOnly = true)
    public List<CatalogItem> searchProducts(String q, int limit) {
        return productsPage(q, 0, Math.max(1, Math.min(limit, 20))).items().stream().map(CatalogItem::from).toList();
    }

    /** The whole sellable catalogue (no cost prices). The browser keeps a copy so sales still work offline. */
    @Transactional(readOnly = true)
    public List<CatalogItem> catalog() {
        return products.findAll(Sort.by("name")).stream().map(CatalogItem::from).toList();
    }

    @Transactional(readOnly = true)
    public List<Product> allProducts() { return products.findAll(Sort.by("name")); }

    /**
     * Adds a product. When debitCash is true, (quantity x cost price) is taken out of Cash Balance
     * in the same database transaction and recorded in the cash ledger and purchase history.
     * If cash is not enough, nothing is saved.
     */
    @Transactional
    public Product createProduct(ProductRequest r, boolean debitCash) {
        String sku = r.sku().trim();
        if (products.existsBySku(sku)) throw new IllegalArgumentException("A product with this SKU already exists.");
        Product p = new Product();
        copy(p, r);

        BigDecimal stockValue = r.costPrice().multiply(BigDecimal.valueOf(r.quantity()));
        CashAccount cash = null;
        if (debitCash && stockValue.signum() > 0) {
            cash = lockedCash();
            if (cash.getBalance().compareTo(stockValue) < 0) {
                throw new IllegalArgumentException("Insufficient cash balance to add this stock (needs "
                    + stockValue.toPlainString() + ", available " + cash.getBalance().toPlainString()
                    + "). Add cash first, or add the product without debiting cash.");
            }
        }
        p = products.save(p);
        if (cash != null) {
            cash.setBalance(cash.getBalance().subtract(stockValue));
            cashTransactions.save(new CashTransaction("STOCK_ADDED", stockValue.negate(), cash.getBalance(),
                "New product stock: " + p.getName() + " x " + p.getQuantity()));
            businessTransactions.save(new BusinessTransaction("PURCHASE", p, p.getQuantity(),
                p.getCostPrice(), stockValue, "Opening stock", "Added with new product"));
        }
        return p;
    }

    /** Editing a product is a correction: it never moves cash. Use Purchases to buy more stock. */
    @Transactional
    public Product updateProduct(Long id, ProductRequest r) {
        Product p = products.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Product not found."));
        products.findBySku(r.sku().trim()).filter(other -> !other.getId().equals(id))
            .ifPresent(other -> { throw new IllegalArgumentException("A product with this SKU already exists."); });
        copy(p, r);
        return products.save(p);
    }

    private void copy(Product p, ProductRequest r) {
        p.setName(r.name().trim()); p.setSku(r.sku().trim()); p.setCategory(r.category().trim());
        p.setQuantity(r.quantity()); p.setReorderLevel(r.reorderLevel());
        p.setCostPrice(r.costPrice()); p.setSellingPrice(r.sellingPrice());
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product p = products.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Product not found."));
        if (businessTransactions.existsByProductId(id) || saleItems.existsByProductId(id)) {
            throw new IllegalArgumentException("This product has transaction history and cannot be deleted. Set its stock to zero instead.");
        }
        products.delete(p);
    }

    // Lock order is always product first, then cash, so concurrent requests cannot deadlock.
    @Transactional
    public BusinessTransaction purchase(TradeRequest r) {
        Product p = products.findByIdForUpdate(r.productId()).orElseThrow(() -> new IllegalArgumentException("Product not found."));
        BigDecimal total = r.unitPrice().multiply(BigDecimal.valueOf(r.quantity()));
        CashAccount cash = lockedCash();
        if (cash.getBalance().compareTo(total) < 0) throw new IllegalArgumentException("Insufficient cash balance for this purchase.");
        p.setQuantity(p.getQuantity() + r.quantity());
        cash.setBalance(cash.getBalance().subtract(total));
        cashTransactions.save(new CashTransaction("PURCHASE", total.negate(), cash.getBalance(),
                "Purchase: " + p.getName() + " x " + r.quantity()));
        return businessTransactions.save(new BusinessTransaction("PURCHASE", p, r.quantity(),
                r.unitPrice(), total, r.partyName(), r.note()));
    }

    @Transactional
    public BigDecimal addCash(CashRequest r) {
        CashAccount cash = lockedCash();
        cash.setBalance(cash.getBalance().add(r.amount()));
        cashTransactions.save(new CashTransaction("CASH_IN", r.amount(), cash.getBalance(), r.description()));
        return cash.getBalance();
    }

    @Transactional
    public BigDecimal withdrawCash(CashRequest r) {
        CashAccount cash = lockedCash();
        if (cash.getBalance().compareTo(r.amount()) < 0) throw new IllegalArgumentException("Insufficient cash balance.");
        cash.setBalance(cash.getBalance().subtract(r.amount()));
        cashTransactions.save(new CashTransaction("CASH_OUT", r.amount().negate(), cash.getBalance(), r.description()));
        return cash.getBalance();
    }

    private CashAccount lockedCash() {
        return accounts.findMainAccountForUpdate()
            .orElseThrow(() -> new IllegalStateException("Cash balance account is not initialized."));
    }

    @Transactional(readOnly = true)
    public BigDecimal cashBalance() {
        return accounts.findById(1L).map(CashAccount::getBalance).orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public PageResponse<CashTransaction> cashHistory(int page, int size) {
        return PageResponse.from(cashTransactions.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(page, size)));
    }
    @Transactional(readOnly = true)
    public List<BusinessTransaction> allTransactions() { return businessTransactions.findTop100ByOrderByCreatedAtDesc(); }
    @Transactional(readOnly = true)
    public PageResponse<BusinessTransaction> purchases(int page, int size) {
        return PageResponse.from(businessTransactions.findByTypeOrderByCreatedAtDescIdDesc("PURCHASE", PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> dashboard() {
        List<Product> low = products.findLowStock();
        return Map.of(
            "productCount", products.count(),
            "totalStock", products.totalStock(),
            "lowStockCount", low.size(),
            "cashBalance", cashBalance(),
            "salesTotal", sales.sumActiveTotal(),
            "purchaseTotal", businessTransactions.sumTotalByType("PURCHASE"),
            "lowStockProducts", low);
    }
}
