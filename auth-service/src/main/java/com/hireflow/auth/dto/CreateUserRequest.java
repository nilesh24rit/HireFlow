package com.hireflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.hireflow.auth.entity.UserRole;

public record CreateUserRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a valid email address")
        String email,
        @NotBlank(message = "firstName must not be blank")
        String firstName,
        @NotBlank(message = "lastName must not be blank")
        String lastName,
        @NotNull(message = "role must not be null")
        UserRole role) {
}
