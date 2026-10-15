package com.hireflow.application.grpc;

import java.util.concurrent.TimeUnit;

import com.hireflow.grpc.candidate.v1.CandidateGrpcServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CandidateGrpcClientConfiguration {

    @Bean(destroyMethod = "shutdown")
    ManagedChannel candidateGrpcChannel(
            @Value("${hireflow.grpc.candidate.target:localhost:9092}") String target,
            @Value("${hireflow.grpc.candidate.tls.enabled:false}") boolean tlsEnabled) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("Candidate gRPC target must be configured");
        }
        NettyChannelBuilder builder = NettyChannelBuilder.forTarget(target);
        if (tlsEnabled) {
            builder.useTransportSecurity();
        } else {
            builder.usePlaintext();
        }
        return builder.build();
    }

    @Bean
    CandidateGrpcServiceGrpc.CandidateGrpcServiceBlockingStub candidateGrpcStub(
            ManagedChannel channel,
            @Value("${hireflow.grpc.candidate.deadline-ms:1000}") long deadlineMilliseconds) {
        if (deadlineMilliseconds <= 0) {
            throw new IllegalArgumentException("Candidate gRPC deadline must be greater than zero");
        }
        return CandidateGrpcServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(deadlineMilliseconds, TimeUnit.MILLISECONDS);
    }
}
