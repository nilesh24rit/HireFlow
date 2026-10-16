package com.hireflow.application.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.UUID;

import com.hireflow.application.exception.CandidateServiceTimeoutException;
import com.hireflow.application.exception.InvalidRequestException;
import com.hireflow.application.exception.ResourceNotFoundException;
import com.hireflow.grpc.candidate.v1.CandidateGrpcServiceGrpc;
import com.hireflow.grpc.candidate.v1.CandidateResponse;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CandidateGrpcClientTest {

    @Test
    void mapsSuccessfulLookup() {
        UUID candidateId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CandidateGrpcClient.Lookup lookup = Mockito.mock(CandidateGrpcClient.Lookup.class);
        when(lookup.get(Mockito.any())).thenReturn(CandidateResponse.newBuilder()
                .setCandidateId(candidateId.toString()).setUserId(userId.toString()).build());

        var result = new CandidateGrpcClient(lookup).getCandidateById(candidateId);

        assertThat(result.candidateId()).isEqualTo(candidateId);
        assertThat(result.userId()).isEqualTo(userId);
    }

    @Test
    void mapsGrpcFailuresWithoutExposingDetails() {
        UUID candidateId = UUID.randomUUID();
        CandidateGrpcClient.Lookup lookup = Mockito.mock(CandidateGrpcClient.Lookup.class);
        when(lookup.get(Mockito.any()))
                .thenThrow(Status.NOT_FOUND.asRuntimeException());
        assertThatThrownBy(() -> new CandidateGrpcClient(lookup).getCandidateById(candidateId))
                .isInstanceOf(ResourceNotFoundException.class);

        Mockito.reset(lookup);
        when(lookup.get(Mockito.any()))
                .thenThrow(Status.DEADLINE_EXCEEDED.asRuntimeException());
        assertThatThrownBy(() -> new CandidateGrpcClient(lookup).getCandidateById(candidateId))
                .isInstanceOf(CandidateServiceTimeoutException.class);

        assertThatThrownBy(() -> new CandidateGrpcClient(lookup).getCandidateById(null))
                .isInstanceOf(InvalidRequestException.class);
    }
}
