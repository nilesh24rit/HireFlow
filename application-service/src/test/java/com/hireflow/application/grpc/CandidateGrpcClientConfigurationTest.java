package com.hireflow.application.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hireflow.grpc.candidate.v1.CandidateGrpcServiceGrpc;

import io.grpc.ManagedChannel;

import org.junit.jupiter.api.Test;

class CandidateGrpcClientConfigurationTest {

    private final CandidateGrpcClientConfiguration configuration = new CandidateGrpcClientConfiguration();

    @Test
    void createsPlaintextChannelAndDeadlineBoundStubForLocalDevelopment() {
        ManagedChannel channel = configuration.candidateGrpcChannel("localhost:9092", false, "test-token");
        try {
            CandidateGrpcServiceGrpc.CandidateGrpcServiceBlockingStub stub =
                    configuration.candidateGrpcStub(channel, 750);
            assertThat(channel.isShutdown()).isFalse();
            assertThat(stub.getCallOptions().getDeadline()).isNotNull();
            assertThat(stub.getCallOptions().getDeadline().timeRemaining(java.util.concurrent.TimeUnit.MILLISECONDS))
                    .isLessThanOrEqualTo(750);
        } finally {
            channel.shutdownNow();
        }
    }

    @Test
    void rejectsMissingTargetAndNonPositiveDeadline() {
        assertThatThrownBy(() -> configuration.candidateGrpcChannel(" ", false, "test-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target");

        ManagedChannel channel = configuration.candidateGrpcChannel("localhost:9092", false, "test-token");
        try {
            assertThatThrownBy(() -> configuration.candidateGrpcStub(channel, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("deadline");
        } finally {
            channel.shutdownNow();
        }
        assertThatThrownBy(() -> configuration.candidateGrpcChannel("localhost:9092", false, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token");
    }
}
