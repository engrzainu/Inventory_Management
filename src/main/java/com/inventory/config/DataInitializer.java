package com.inventory.config;

import com.inventory.model.AppUser;
import com.inventory.model.CashAccount;
import com.inventory.model.Role;
import com.inventory.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.security.SecureRandom;
import java.util.HexFormat;
import com.inventory.model.Product;
import com.inventory.repository.CashAccountRepository;
import com.inventory.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.math.BigDecimal;

@Configuration
public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    /**
     * Creates the first Inventory Admin when there are no users yet. The password comes from
     * INVENTORY_ADMIN_PASSWORD (a random one is printed in the log if it is not set).
     * Set INVENTORY_RESET_ADMIN_PASSWORD=true for one start-up to reset a forgotten admin password.
     */
    @Bean
    CommandLineRunner ensureAdminUser(AppUserRepository users, PasswordEncoder encoder,
            @Value("${app.security.admin-username:admin}") String username,
            @Value("${app.security.admin-password:}") String password,
            @Value("${app.security.reset-admin-password:false}") boolean reset) {
        return args -> {
            if (users.count() == 0) {
                String pw = password;
                if (pw == null || pw.isBlank()) {
                    byte[] bytes = new byte[9];
                    new SecureRandom().nextBytes(bytes);
                    pw = HexFormat.of().formatHex(bytes);
                    log.warn("INVENTORY_ADMIN_PASSWORD is not set. Generated password for first admin '{}': {}", username, pw);
                }
                users.save(new AppUser(username, encoder.encode(pw), Role.ADMIN));
            } else if (reset && password != null && !password.isBlank()) {
                users.findByUsernameIgnoreCase(username).ifPresent(u -> {
                    u.setPasswordHash(encoder.encode(password));
                    u.setRole(Role.ADMIN);
                    u.setEnabled(true);
                    users.save(u);
                    log.warn("Password for admin '{}' was reset from INVENTORY_ADMIN_PASSWORD.", username);
                });
            }
        };
    }

    @Bean
    CommandLineRunner initializeData(CashAccountRepository cash, ProductRepository products,
            @Value("${app.seed-demo-data:false}") boolean seed) {
        return args -> {
            if (!cash.existsById(1L)) cash.save(new CashAccount(1L));
            if (seed && products.count() == 0) {
                add(products, "Rice (5kg)", "RICE-005", "Grains", 50, 5, "4200", "5500");
                add(products, "Cooking Oil (2L)", "OIL-002", "Groceries", 28, 5, "6000", "7200");
                add(products, "Indomie Noodles", "IND-001", "Food", 2, 5, "450", "600");
                add(products, "Milo Drink", "MIL-001", "Drinks", 3, 5, "650", "800");
                add(products, "Peak Milk", "PEAK-01", "Dairy", 15, 5, "900", "1200");
            }
        };
    }

    private void add(ProductRepository repo, String name, String sku, String category,
                     int quantity, int reorder, String cost, String selling) {
        Product p = new Product();
        p.setName(name); p.setSku(sku); p.setCategory(category);
        p.setQuantity(quantity); p.setReorderLevel(reorder);
        p.setCostPrice(new BigDecimal(cost)); p.setSellingPrice(new BigDecimal(selling));
        repo.save(p);
    }
}
