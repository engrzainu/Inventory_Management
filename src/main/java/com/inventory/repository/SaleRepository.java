package com.inventory.repository;

import com.inventory.model.Sale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    /** One page of sale ids (ids only, so paging happens in the database even though sales have many items). */
    @Query(value = "select s.id from Sale s where s.deleted = false and s.createdAt >= :from and s.createdAt < :to",
           countQuery = "select count(s) from Sale s where s.deleted = false and s.createdAt >= :from and s.createdAt < :to")
    Page<Long> findActiveIdsInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    @Query("select distinct s from Sale s left join fetch s.items where s.id in :ids")
    List<Sale> findAllWithItemsByIdIn(@Param("ids") Collection<Long> ids);

    /** One row: [count, revenue, cost] for the whole period (not just the page). */
    @Query("select count(s), coalesce(sum(s.total), 0), coalesce(sum(s.totalCost), 0) from Sale s "
         + "where s.deleted = false and s.createdAt >= :from and s.createdAt < :to")
    List<Object[]> totalsInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    Optional<Sale> findByClientId(String clientId);

    @Query("select coalesce(sum(s.total), 0) from Sale s where s.deleted = false")
    BigDecimal sumActiveTotal();

    long countByDeletedFalseAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime from, LocalDateTime to);
}
