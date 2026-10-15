package com.hireflow.candidate.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;

import com.hireflow.grpc.candidate.v1.CandidateGrpcServiceGrpc;
import com.hireflow.grpc.candidate.v1.GetCandidateRequest;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

import org.junit.jupiter.api.Test;

class CandidateGrpcServerTest {

    @Test
    void startsOnConfiguredPortAndReturnsUnimplementedUntilLookupIsAdded() throws Exception {
        CandidateGrpcServer server = new CandidateGrpcServer(
                0, new CandidateGrpcEndpoint(), false, "", "");
        server.start();
        ManagedChannel channel = NettyChannelBuilder
                .forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();

        try {
            StatusRuntimeException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                    () -> CandidateGrpcServiceGrpc.newBlockingStub(channel)
                            .withDeadlineAfter(1, TimeUnit.SECONDS)
                            .getCandidateById(GetCandidateRequest.newBuilder()
                                    .setCandidateId("candidate-1")
                                    .build()),
                    StatusRuntimeException.class);
            assertThat(exception).isNotNull();
            assertThat(exception.getStatus().getCode()).isEqualTo(Status.Code.UNIMPLEMENTED);
        } finally {
            channel.shutdownNow();
            server.stop();
        }
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void rejectsTlsWithoutExternalKeyMaterial() {
        assertThatThrownBy(() -> new CandidateGrpcServer(
                9092, new CandidateGrpcEndpoint(), true, "", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TLS requires");
    }
}
