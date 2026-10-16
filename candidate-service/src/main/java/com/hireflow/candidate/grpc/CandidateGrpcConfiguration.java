package com.hireflow.candidate.grpc;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CandidateGrpcConfiguration {

    @Bean
    CandidateGrpcServer candidateGrpcServer(
            CandidateGrpcEndpoint endpoint,
            @Value("${hireflow.grpc.candidate.port:9092}") int port,
            @Value("${hireflow.grpc.candidate.tls.enabled:false}") boolean tlsEnabled,
            @Value("${hireflow.grpc.candidate.tls.certificate:}") String certificatePath,
            @Value("${hireflow.grpc.candidate.tls.private-key:}") String privateKeyPath,
            @Value("${hireflow.grpc.internal-token:}") String internalToken) {
        return new CandidateGrpcServer(port, endpoint, tlsEnabled, certificatePath, privateKeyPath,
                new InternalGrpcAuthenticationInterceptor(internalToken));
    }
}
