package com.hireflow.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank(message = "firstName must not be blank")
        String firstName,
        @NotBlank(message = "lastName must not be blank")
        String lastName) {
}
