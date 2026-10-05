package com.hireflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload used to update a user profile; email, role and enabled are not accepted")
public record UpdateUserRequest(
        @NotBlank(message = "firstName must not be blank")
        @Size(max = 100, message = "firstName must be at most 100 characters")
        @Schema(description = "New first name of the user",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "Janet")
        String firstName,
        @NotBlank(message = "lastName must not be blank")
        @Size(max = 100, message = "lastName must be at most 100 characters")
        @Schema(description = "New last name of the user",
                requiredMode = Schema.RequiredMode.REQUIRED, example = "Smith")
        String lastName) {
}
