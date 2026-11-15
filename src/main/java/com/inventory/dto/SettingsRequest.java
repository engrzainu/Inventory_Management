package com.inventory.dto;

import jakarta.validation.constraints.*;

public record SettingsRequest(
    @NotBlank @Size(max = 120) String businessName,
    @Size(max = 255) String address,
    @Size(max = 50) String phone,
    String logoData,
    boolean removeLogo
) {}
