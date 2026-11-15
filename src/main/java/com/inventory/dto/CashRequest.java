package com.inventory.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CashRequest(
    @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
    @NotBlank @Size(max = 255) String description
) {}
