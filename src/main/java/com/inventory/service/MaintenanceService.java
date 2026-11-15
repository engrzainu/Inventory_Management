package com.inventory.service;

import com.inventory.model.AppUser;
import com.inventory.model.CashAccount;
import com.inventory.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class MaintenanceService {
    public static final String CONFIRMATION_PHRASE = "DELETE ALL DATA";

    private final ProductRepository products;
    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final BusinessTransactionRepository businessTransactions;
    private final CashTransactionRepository cashTransactions;
    private final CashAccountRepository accounts;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;

    public MaintenanceService(ProductRepository products, SaleRepository sales, SaleItemRepository saleItems,
            BusinessTransactionRepository businessTransactions, CashTransactionRepository cashTransactions,
            CashAccountRepository accounts, AppUserRepository users, PasswordEncoder encoder) {
        this.products = products;
        this.sales = sales;
        this.saleItems = saleItems;
        this.businessTransactions = businessTransactions;
        this.cashTransactions = cashTransactions;
        this.accounts = accounts;
        this.users = users;
        this.encoder = encoder;
    }

    /**
     * Erases all business data: products, sales, purchases and the cash ledger, and sets Cash Balance to zero.
     * User accounts, the business profile and settings are kept so the admin can still sign in.
     * Requires the exact confirmation phrase and the signed-in admin's password.
     */
    @Transactional
    public void clearAllData(String username, String password, String confirmation) {
        if (!CONFIRMATION_PHRASE.equals(confirmation == null ? null : confirmation.trim())) {
            throw new IllegalArgumentException("Type " + CONFIRMATION_PHRASE + " exactly to confirm.");
        }
        AppUser admin = users.findByUsernameIgnoreCase(username).orElseThrow(() -> new IllegalArgumentException("User not found."));
        if (password == null || !encoder.matches(password, admin.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect password.");
        }
        saleItems.deleteAllInBatch();           // children first
        sales.deleteAllInBatch();
        businessTransactions.deleteAllInBatch();
        cashTransactions.deleteAllInBatch();
        products.deleteAllInBatch();
        CashAccount cash = accounts.findMainAccountForUpdate().orElseGet(() -> new CashAccount(1L));
        cash.setBalance(BigDecimal.ZERO);
        accounts.save(cash);
    }
}
