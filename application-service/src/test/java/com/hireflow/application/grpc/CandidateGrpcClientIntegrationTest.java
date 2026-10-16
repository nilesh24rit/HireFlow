package com.hireflow.application.grpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.hireflow.grpc.candidate.v1.CandidateGrpcServiceGrpc;
import com.hireflow.grpc.candidate.v1.CandidateResponse;

import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.ManagedChannel;

import org.junit.jupiter.api.Test;

class CandidateGrpcClientIntegrationTest {

    @Test
    void communicatesWithARealGrpcServerAndMapsTheResponse() throws Exception {
        UUID candidateId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var server = NettyServerBuilder.forPort(0)
                .addService(new CandidateGrpcServiceGrpc.CandidateGrpcServiceImplBase() {
                    @Override
                    public void getCandidateById(
                            com.hireflow.grpc.candidate.v1.GetCandidateRequest request,
                            StreamObserver<CandidateResponse> observer) {
                        observer.onNext(CandidateResponse.newBuilder()
                                .setCandidateId(candidateId.toString())
                                .setUserId(userId.toString())
                                .build());
                        observer.onCompleted();
                    }
                }).build().start();
        ManagedChannel channel = null;
        try {
            var configuration = new CandidateGrpcClientConfiguration();
            channel = configuration.candidateGrpcChannel(
                    "localhost:" + server.getPort(), false, "integration-token");
            var stub = configuration.candidateGrpcStub(channel, 1000);
            var response = new CandidateGrpcClient(stub::getCandidateById)
                    .getCandidateById(candidateId);
            assertThat(response.candidateId()).isEqualTo(candidateId);
            assertThat(response.userId()).isEqualTo(userId);
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
            server.shutdownNow();
        }
    }
}
