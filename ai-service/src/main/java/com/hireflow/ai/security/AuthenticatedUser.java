package com.hireflow.ai.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String role) {
}
