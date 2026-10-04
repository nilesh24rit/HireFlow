package com.hireflow.application.dto;

import com.hireflow.application.entity.ApplicationStatus;

public record UpdateApplicationStatusRequest(
        ApplicationStatus status) {
}
