package com.inventory.dto;

import com.inventory.model.Role;
import jakarta.validation.constraints.*;

public record UserCreateRequest(
    @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Username may only use letters, numbers, dot, dash and underscore.") String username,
    @NotBlank @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters.") String password,
    @NotNull Role role
) {}
