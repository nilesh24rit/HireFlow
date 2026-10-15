package com.hireflow.candidate.grpc;

import com.hireflow.grpc.candidate.v1.CandidateGrpcServiceGrpc;

import io.grpc.stub.StreamObserver;

import org.springframework.stereotype.Component;

@Component
public class CandidateGrpcEndpoint extends CandidateGrpcServiceGrpc.CandidateGrpcServiceImplBase {

    @Override
    public void getCandidateById(
            com.hireflow.grpc.candidate.v1.GetCandidateRequest request,
            StreamObserver<com.hireflow.grpc.candidate.v1.CandidateResponse> responseObserver) {
        responseObserver.onError(io.grpc.Status.UNIMPLEMENTED
                .withDescription("Candidate lookup is not available yet")
                .asRuntimeException());
    }
}
