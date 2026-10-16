package com.hireflow.candidate.grpc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class InternalGrpcAuthenticationInterceptor implements ServerInterceptor {

    static final Metadata.Key<String> TOKEN_HEADER = Metadata.Key.of(
            "x-hireflow-internal-token", Metadata.ASCII_STRING_MARSHALLER);

    private final byte[] expectedToken;

    public InternalGrpcAuthenticationInterceptor(String expectedToken) {
        if (expectedToken == null || expectedToken.isBlank()) {
            throw new IllegalArgumentException("Internal gRPC token must be configured");
        }
        this.expectedToken = expectedToken.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String suppliedToken = headers.get(TOKEN_HEADER);
        if (suppliedToken == null || !MessageDigest.isEqual(
                expectedToken, suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            call.close(Status.UNAUTHENTICATED.withDescription("Internal authentication failed"),
                    new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return next.startCall(call, headers);
    }
}
