package com.inventory.dto;

import com.inventory.model.Role;
import jakarta.validation.constraints.Size;

/** Every field is optional; only the ones sent are changed. */
public record UserUpdateRequest(
    Role role,
    Boolean enabled,
    @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.") String password
) {}
