package com.hireflow.notification.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

import com.hireflow.notification.repository.NotificationRepository;

class NotificationControllerTest {

    private NotificationRepository repository;
    private NotificationController controller;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        repository = org.mockito.Mockito.mock(NotificationRepository.class);
        controller = new NotificationController(repository);
        authentication = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID().toString(), "credentials",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_CANDIDATE")));
    }

    @Test
    void rejectsPaginationThatWouldOverflowTheDatabaseOffset() {
        assertThatThrownBy(() -> controller.list(authentication, Integer.MAX_VALUE, 100))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid pagination");

        verifyNoInteractions(repository);
    }
}
