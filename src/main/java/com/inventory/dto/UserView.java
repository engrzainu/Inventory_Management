package com.inventory.dto;

import com.inventory.model.AppUser;
import com.inventory.model.Role;
import java.time.LocalDateTime;

public record UserView(Long id, String username, Role role, String roleLabel, boolean enabled, LocalDateTime createdAt) {
    public static UserView from(AppUser u) {
        return new UserView(u.getId(), u.getUsername(), u.getRole(), u.getRole().getLabel(), u.isEnabled(), u.getCreatedAt());
    }
}
