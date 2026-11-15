package com.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record ClearDataRequest(@NotBlank String confirmation, @NotBlank String password) {}
