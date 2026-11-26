package com.example.marketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateProductStatusRequest(
        @NotBlank(message = "status must be on or off")
        @Pattern(regexp = "on|off", message = "status must be on or off")
        String status
) {
}
