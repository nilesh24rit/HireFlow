package com.hireflow.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.hireflow.auth.entity.UserRole;

public record CreateUserRequest(
        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a valid email address")
        @Size(max = 320, message = "email must be at most 320 characters")
        String email,
        @NotBlank(message = "firstName must not be blank")
        @Size(max = 100, message = "firstName must be at most 100 characters")
        String firstName,
        @NotBlank(message = "lastName must not be blank")
        @Size(max = 100, message = "lastName must be at most 100 characters")
        String lastName,
        @NotNull(message = "role must not be null")
        UserRole role) {
}
