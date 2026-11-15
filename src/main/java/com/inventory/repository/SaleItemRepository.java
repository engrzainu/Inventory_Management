package com.inventory.repository;

import com.inventory.model.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    boolean existsByProductId(Long productId);

    /** Rows of [productId, totalQuantity] for one sale, ordered by product id (stable lock order). */
    @Query("select i.productId, sum(i.quantity) from SaleItem i where i.sale.id = :saleId group by i.productId order by i.productId")
    List<Object[]> sumQuantityByProduct(@Param("saleId") Long saleId);

    /** Rows of [productId, units, revenue, cost] for sales that are not deleted inside the period. */
    @Query("select i.productId, sum(i.quantity), sum(i.lineTotal), sum(i.unitCost * i.quantity) from SaleItem i "
         + "where i.sale.deleted = false and i.sale.createdAt >= :from and i.sale.createdAt < :to group by i.productId")
    List<Object[]> soldByProduct(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
