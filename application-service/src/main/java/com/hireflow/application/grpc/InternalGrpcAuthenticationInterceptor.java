package com.hireflow.application.grpc;

import java.util.concurrent.Executor;

import io.grpc.CallCredentials;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientCall.Listener;
import io.grpc.ClientInterceptors;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.Status;

public class InternalGrpcAuthenticationInterceptor implements ClientInterceptor {

    private static final Metadata.Key<String> TOKEN_HEADER = Metadata.Key.of(
            "x-hireflow-internal-token", Metadata.ASCII_STRING_MARSHALLER);
    private final String token;

    public InternalGrpcAuthenticationInterceptor(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Internal gRPC token must be configured");
        }
        this.token = token;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        ClientCall<ReqT, RespT> delegate = next.newCall(method, callOptions);
        return new ForwardingClientCall.SimpleForwardingClientCall<>(delegate) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(TOKEN_HEADER, token);
                super.start(responseListener, headers);
            }
        };
    }
}
