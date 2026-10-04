package com.hireflow.application.dto;

import com.hireflow.application.entity.ApplicationStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Payload used to update the status of an application")
public record UpdateApplicationStatusRequest(
        @NotNull(message = "status must not be null")
        @Schema(description = "New status of the application", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "UNDER_REVIEW")
        ApplicationStatus status) {
}
