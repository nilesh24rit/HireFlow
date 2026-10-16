package com.hireflow.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.hireflow.application.dto.CreateApplicationRequest;
import com.hireflow.application.grpc.CandidateGrpcClient;
import com.hireflow.application.repository.ApplicationRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class ApplicationGrpcValidationTest {

    private final ApplicationRepository repository = Mockito.mock(ApplicationRepository.class);
    private final CandidateGrpcClient candidateClient = Mockito.mock(CandidateGrpcClient.class);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void verifiesCandidateOwnershipBeforeSaving() {
        UUID userId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        authenticate(userId, "ROLE_CANDIDATE");
        when(candidateClient.getCandidateById(candidateId))
                .thenReturn(new CandidateGrpcClient.CandidateIdentity(candidateId, userId));
        when(repository.existsByCandidateIdAndJobId(candidateId, jobId)).thenReturn(false);
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            var application = invocation.getArgument(0, com.hireflow.application.entity.Application.class);
            ReflectionTestUtils.setField(application, "id", UUID.randomUUID());
            return application;
        });

        new ApplicationService(repository, null, candidateClient)
                .createApplication(new CreateApplicationRequest(candidateId, jobId, null));

        verify(repository).saveAndFlush(any());
    }

    @Test
    void rejectsAnotherUsersCandidateBeforeAnyWrite() {
        UUID userId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID candidateId = UUID.randomUUID();
        authenticate(userId, "ROLE_CANDIDATE");
        when(candidateClient.getCandidateById(candidateId))
                .thenReturn(new CandidateGrpcClient.CandidateIdentity(candidateId, ownerId));

        assertThatThrownBy(() -> new ApplicationService(repository, null, candidateClient)
                .createApplication(new CreateApplicationRequest(candidateId, UUID.randomUUID(), null)))
                .isInstanceOf(AccessDeniedException.class);

        verify(repository, never()).saveAndFlush(any());
    }

    private void authenticate(UUID userId, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId.toString(), "n/a",
                        java.util.List.of(new SimpleGrantedAuthority(role))));
    }
}
