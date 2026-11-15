package com.inventory.controller;

import com.inventory.dto.*;
import com.inventory.model.*;
import com.inventory.service.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final InventoryService service;
    private final ProductExcelService excelService;
    private final SalesService salesService;
    private final SettingsService settingsService;
    private final ReportService reportService;
    private final ReportExcelService reportExcelService;
    private final UserService userService;
    private final MaintenanceService maintenanceService;

    public ApiController(InventoryService service, ProductExcelService excelService, SalesService salesService,
            SettingsService settingsService, ReportService reportService, ReportExcelService reportExcelService,
            UserService userService, MaintenanceService maintenanceService) {
        this.service = service;
        this.excelService = excelService;
        this.salesService = salesService;
        this.settingsService = settingsService;
        this.reportService = reportService;
        this.reportExcelService = reportExcelService;
        this.userService = userService;
        this.maintenanceService = maintenanceService;
    }

    private static int page(int page) { return Math.max(0, page); }
    private static int size(int size) { return Math.max(1, Math.min(size, 100)); }

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    // ---- who am I (any signed-in user)
    @GetMapping("/me")
    public Map<String, String> me(org.springframework.security.core.Authentication auth) {
        Role role = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")) ? Role.ADMIN : Role.ACCOUNTANT;
        return Map.of("username", auth.getName(), "role", role.name(), "roleLabel", role.getLabel());
    }

    // ---- dashboard
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() { return service.dashboard(); }

    // ---- products
    /** Paged product list: page is 0-based, 25 per page by default. */
    @GetMapping("/products")
    public PageResponse<Product> products(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return service.productsPage(q, page(page), size(size));
    }

    /** Short result list for the sales search box (Inventory Admin and Accountant). */
    @GetMapping("/products/search")
    public List<CatalogItem> searchProducts(@RequestParam(required = false) String q, @RequestParam(defaultValue = "8") int limit) {
        return service.searchProducts(q, limit);
    }

    /** The full sellable catalogue without cost prices. The browser stores it so sales work offline. */
    @GetMapping("/products/catalog")
    public List<CatalogItem> catalog() { return service.catalog(); }

    /** Every product, for dropdowns (Inventory Admin). */
    @GetMapping("/products/all")
    public List<Product> allProducts() { return service.allProducts(); }

    @PostMapping(value = "/products/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportResult importProducts(@RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean debitCash) throws java.io.IOException {
        return excelService.importProducts(file, debitCash);
    }

    @GetMapping("/products/template")
    public ResponseEntity<byte[]> productTemplate() throws java.io.IOException {
        return ResponseEntity.ok().contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inventory-products-template.xlsx")
            .body(excelService.createTemplate());
    }

    @PostMapping("/products")
    public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductRequest request,
            @RequestParam(defaultValue = "true") boolean debitCash) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createProduct(request, debitCash));
    }

    @PutMapping("/products/{id}")
    public Product updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return service.updateProduct(id, request);
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        service.deleteProduct(id); return ResponseEntity.noContent().build();
    }

    // ---- cash
    @GetMapping("/cash")
    public Map<String, BigDecimal> cash() { return Map.of("balance", service.cashBalance()); }

    @PostMapping("/cash/add")
    public Map<String, BigDecimal> addCash(@Valid @RequestBody CashRequest request) {
        return Map.of("balance", service.addCash(request));
    }

    @PostMapping("/cash/withdraw")
    public Map<String, BigDecimal> withdrawCash(@Valid @RequestBody CashRequest request) {
        return Map.of("balance", service.withdrawCash(request));
    }

    @GetMapping("/cash/transactions")
    public PageResponse<CashTransaction> cashTransactions(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.cashHistory(page(page), size(size));
    }

    // ---- sales (cart checkout)
    @PostMapping("/sales")
    public ResponseEntity<SaleResponse> sale(@Valid @RequestBody SaleRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(salesService.sell(request));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // two uploads of the same offline sale arrived at the same moment: return the one that won
            if (request.clientId() != null && !request.clientId().isBlank()) {
                Optional<SaleResponse> existing = salesService.findByClientId(request.clientId().trim());
                if (existing.isPresent()) return ResponseEntity.ok(existing.get());
            }
            throw e;
        }
    }

    /** range = today | week | month | year | all | custom (custom needs from and to as yyyy-MM-dd). */
    @GetMapping("/sales")
    public SalesListResponse sales(@RequestParam(defaultValue = "today") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return salesService.history(DateRange.resolve(range, from, to), page(page), size(size));
    }

    @DeleteMapping("/sales/{id}")
    public ResponseEntity<Void> deleteSale(@PathVariable Long id) {
        salesService.deleteSale(id); return ResponseEntity.noContent().build();
    }

    // ---- purchases
    @PostMapping("/purchases")
    public ResponseEntity<BusinessTransaction> purchase(@Valid @RequestBody TradeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.purchase(request));
    }

    @GetMapping("/purchases")
    public PageResponse<BusinessTransaction> purchases(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return service.purchases(page(page), size(size));
    }

    @GetMapping("/transactions")
    public List<BusinessTransaction> transactions() { return service.allTransactions(); }

    // ---- business settings
    @GetMapping("/settings")
    public BusinessSettings settings() { return settingsService.get(); }

    @PutMapping("/settings")
    public BusinessSettings updateSettings(@Valid @RequestBody SettingsRequest request) {
        return settingsService.update(request);
    }

    // ---- users and roles (Inventory Admin)
    @GetMapping("/users")
    public List<UserView> users() { return userService.list(); }

    @PostMapping("/users")
    public ResponseEntity<UserView> createUser(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(request));
    }

    @PutMapping("/users/{id}")
    public UserView updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request, Principal principal) {
        return userService.update(id, request, principal.getName());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id, Principal principal) {
        userService.delete(id, principal.getName()); return ResponseEntity.noContent().build();
    }

    // ---- danger zone (Inventory Admin)
    @PostMapping("/admin/clear-data")
    public ResponseEntity<Void> clearData(@Valid @RequestBody ClearDataRequest request, Principal principal) {
        maintenanceService.clearAllData(principal.getName(), request.password(), request.confirmation());
        return ResponseEntity.noContent().build();
    }

    // ---- reports
    @GetMapping("/reports")
    public ReportResponse report(@RequestParam(defaultValue = "all") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reportService.build(DateRange.resolve(range, from, to));
    }

    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(@RequestParam(defaultValue = "all") String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws java.io.IOException {
        ReportResponse report = reportService.build(DateRange.resolve(range, from, to));
        byte[] bytes = reportExcelService.export(report, settingsService.get().getBusinessName());
        return ResponseEntity.ok().contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=inventory-report-" + LocalDate.now() + ".xlsx")
            .body(bytes);
    }

    // ---- errors
    @ExceptionHandler({IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(ex.getMessage())));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> duplicate(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "That value already exists (for example a duplicate SKU)."));
    }

    @ExceptionHandler({IllegalStateException.class})
    public ResponseEntity<Map<String, String>> conflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", String.valueOf(ex.getMessage())));
    }
}
