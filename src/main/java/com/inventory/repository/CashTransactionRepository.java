package com.inventory.repository;

import com.inventory.model.CashTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CashTransactionRepository extends JpaRepository<CashTransaction, Long> {
    List<CashTransaction> findTop100ByOrderByCreatedAtDesc();
    Page<CashTransaction> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
