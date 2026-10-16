package com.hireflow.candidate.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.hireflow.candidate.dto.CandidateResponse;
import com.hireflow.candidate.exception.ResourceNotFoundException;
import com.hireflow.candidate.service.CandidateService;
import com.hireflow.grpc.candidate.v1.CandidateGrpcServiceGrpc;
import com.hireflow.grpc.candidate.v1.GetCandidateRequest;

import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CandidateGrpcServerTest {

    @Test
    void returnsCandidateIdentifiersForAuthenticatedLookup() {
        UUID candidateId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CandidateService service = Mockito.mock(CandidateService.class);
        when(service.getCandidateById(candidateId)).thenReturn(candidate(candidateId, userId));
        CandidateGrpcServer server = server(service);
        server.start();
        ManagedChannel channel = channel(server);

        try {
            var response = stub(channel).getCandidateById(request(candidateId));
            assertThat(response.getCandidateId()).isEqualTo(candidateId.toString());
            assertThat(response.getUserId()).isEqualTo(userId.toString());
        } finally {
            channel.shutdownNow();
            server.stop();
        }
    }

    @Test
    void mapsMalformedAndMissingCandidates() {
        CandidateService service = Mockito.mock(CandidateService.class);
        UUID missingId = UUID.randomUUID();
        when(service.getCandidateById(missingId))
                .thenThrow(new ResourceNotFoundException("missing"));
        CandidateGrpcServer server = server(service);
        server.start();
        ManagedChannel channel = channel(server);

        try {
            assertThatThrownBy(() -> stub(channel).getCandidateById(
                    GetCandidateRequest.newBuilder().setCandidateId("not-a-uuid").build()))
                    .isInstanceOfSatisfying(StatusRuntimeException.class,
                            ex -> assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
            assertThatThrownBy(() -> stub(channel).getCandidateById(request(missingId)))
                    .isInstanceOfSatisfying(StatusRuntimeException.class,
                            ex -> assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND));
        } finally {
            channel.shutdownNow();
            server.stop();
        }
    }

    @Test
    void rejectsUnauthenticatedCaller() {
        CandidateGrpcServer server = server(Mockito.mock(CandidateService.class));
        server.start();
        ManagedChannel channel = NettyChannelBuilder.forAddress("localhost", server.getPort())
                .usePlaintext().build();
        try {
            assertThatThrownBy(() -> CandidateGrpcServiceGrpc.newBlockingStub(channel)
                    .getCandidateById(request(UUID.randomUUID())))
                    .isInstanceOfSatisfying(StatusRuntimeException.class,
                            ex -> assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED));
        } finally {
            channel.shutdownNow();
            server.stop();
        }
    }

    @Test
    void rejectsTlsWithoutExternalKeyMaterial() {
        assertThatThrownBy(() -> new CandidateGrpcServer(
                9092, new CandidateGrpcEndpoint(Mockito.mock(CandidateService.class)), true, "", "",
                new InternalGrpcAuthenticationInterceptor("token")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TLS requires");
    }

    private CandidateGrpcServer server(CandidateService service) {
        return new CandidateGrpcServer(0, new CandidateGrpcEndpoint(service), false, "", "",
                new InternalGrpcAuthenticationInterceptor("token"));
    }

    private ManagedChannel channel(CandidateGrpcServer server) {
        return NettyChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
    }

    private CandidateGrpcServiceGrpc.CandidateGrpcServiceBlockingStub stub(ManagedChannel channel) {
        return CandidateGrpcServiceGrpc.newBlockingStub(channel)
                .withCallCredentials(new TokenCredentials("token"))
                .withDeadlineAfter(1, TimeUnit.SECONDS);
    }

    private GetCandidateRequest request(UUID id) {
        return GetCandidateRequest.newBuilder().setCandidateId(id.toString()).build();
    }

    private CandidateResponse candidate(UUID id, UUID userId) {
        return new CandidateResponse(id, userId, null, null, null, null, null, null,
                null, null, null, java.util.List.of(), null, null);
    }

    private static final class TokenCredentials extends CallCredentials {
        private final String token;

        private TokenCredentials(String token) {
            this.token = token;
        }

        @Override
        public void applyRequestMetadata(RequestInfo requestInfo, java.util.concurrent.Executor appExecutor,
                MetadataApplier applier) {
            Metadata metadata = new Metadata();
            metadata.put(InternalGrpcAuthenticationInterceptor.TOKEN_HEADER, token);
            applier.apply(metadata);
        }
    }
}
