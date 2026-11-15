package com.inventory.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record TradeRequest(
    @NotNull Long productId,
    @Min(1) int quantity,
    @NotNull @DecimalMin(value = "0.01") BigDecimal unitPrice,
    @Size(max = 120) String partyName,
    @Size(max = 255) String note
) {}
