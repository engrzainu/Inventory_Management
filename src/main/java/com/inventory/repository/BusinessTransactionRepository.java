package com.inventory.repository;

import com.inventory.model.BusinessTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface BusinessTransactionRepository extends JpaRepository<BusinessTransaction, Long> {
    List<BusinessTransaction> findTop100ByOrderByCreatedAtDesc();
    List<BusinessTransaction> findByTypeOrderByCreatedAtDesc(String type);
    Page<BusinessTransaction> findByTypeOrderByCreatedAtDescIdDesc(String type, Pageable pageable);
    boolean existsByProductId(Long productId);

    @Query("select coalesce(sum(t.total), 0) from BusinessTransaction t where t.type = :type")
    BigDecimal sumTotalByType(@Param("type") String type);
}
