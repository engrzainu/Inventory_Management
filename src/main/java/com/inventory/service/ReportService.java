package com.inventory.service;

import com.inventory.dto.ReportResponse;
import com.inventory.dto.ReportResponse.ProductRow;
import com.inventory.model.Product;
import com.inventory.repository.ProductRepository;
import com.inventory.repository.SaleItemRepository;
import com.inventory.repository.SaleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ReportService {
    private final ProductRepository products;
    private final SaleItemRepository saleItems;
    private final SaleRepository sales;

    public ReportService(ProductRepository products, SaleItemRepository saleItems, SaleRepository sales) {
        this.products = products;
        this.saleItems = saleItems;
        this.sales = sales;
    }

    private record Sold(long units, BigDecimal revenue, BigDecimal cost) {}

    @Transactional(readOnly = true)
    public ReportResponse build(DateRange range) {
        Map<Long, Sold> sold = new HashMap<>();
        for (Object[] row : saleItems.soldByProduct(range.from(), range.to())) {
            sold.put(((Number) row[0]).longValue(),
                new Sold(((Number) row[1]).longValue(), new BigDecimal(row[2].toString()), new BigDecimal(row[3].toString())));
        }
        List<ProductRow> rows = new ArrayList<>();
        long units = 0, unitsSold = 0;
        BigDecimal stockValue = BigDecimal.ZERO, expected = BigDecimal.ZERO;
        BigDecimal revenue = BigDecimal.ZERO, cost = BigDecimal.ZERO;
        for (Product p : products.findAll(Sort.by("name"))) {
            BigDecimal qty = BigDecimal.valueOf(p.getQuantity());
            BigDecimal value = p.getCostPrice().multiply(qty);
            BigDecimal expectedValue = p.getSellingPrice().multiply(qty);
            Sold s = sold.getOrDefault(p.getId(), new Sold(0, BigDecimal.ZERO, BigDecimal.ZERO));
            rows.add(new ProductRow(p.getId(), p.getName(), p.getSku(), p.getCategory(), p.getQuantity(), p.getReorderLevel(),
                p.getCostPrice(), p.getSellingPrice(), value, expectedValue, expectedValue.subtract(value),
                s.units(), s.revenue(), s.revenue().subtract(s.cost())));
            units += p.getQuantity();
            stockValue = stockValue.add(value);
            expected = expected.add(expectedValue);
            unitsSold += s.units();
            revenue = revenue.add(s.revenue());
            cost = cost.add(s.cost());
        }
        int salesCount = (int) sales.countByDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(range.from(), range.to());
        return new ReportResponse(range.label(), range.firstDay(), range.lastDay(), rows.size(), units,
            stockValue, expected, expected.subtract(stockValue), salesCount, unitsSold,
            revenue, cost, revenue.subtract(cost), rows);
    }
}
