package com.inventory.repository;

import com.inventory.model.CashAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface CashAccountRepository extends JpaRepository<CashAccount, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CashAccount c where c.id = 1")
    Optional<CashAccount> findMainAccountForUpdate();
}
