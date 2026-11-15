package com.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record SaleRequest(
    @Size(max = 120) String customerName,
    @Size(max = 255) String note,
    @NotEmpty(message = "Add at least one product to the cart.") @Size(max = 200) List<@Valid Item> items,
    /** Random id made by the browser. Sending the same sale twice (a retry) records it only once. */
    @Size(max = 64) String clientId,
    /** Only for sales made offline: the real sale time as an ISO instant, for example 2026-11-14T18:30:00Z. */
    String soldAt
) {
    public record Item(
        @NotNull Long productId,
        @Min(1) int quantity,
        @NotNull @DecimalMin("0.01") BigDecimal unitPrice
    ) {}
}
