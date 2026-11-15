package com.inventory.repository;

import com.inventory.model.AppUser;
import com.inventory.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    long countByRoleAndEnabledTrue(Role role);
    List<AppUser> findAllByOrderByUsernameAsc();
}
