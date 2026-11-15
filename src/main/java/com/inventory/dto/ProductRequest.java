package com.inventory.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductRequest(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 50) String sku,
    @NotBlank @Size(max = 80) String category,
    @Min(0) int quantity,
    @Min(0) int reorderLevel,
    @NotNull @DecimalMin("0.00") BigDecimal costPrice,
    @NotNull @DecimalMin("0.00") BigDecimal sellingPrice
) {}
