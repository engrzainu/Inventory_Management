package com.inventory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Totals cover the whole period; {@code sales} is one page of the records. */
public record SalesListResponse(String label, LocalDate from, LocalDate to, long count,
        BigDecimal revenue, BigDecimal cost, BigDecimal profit, PageResponse<SaleResponse> sales) {}
