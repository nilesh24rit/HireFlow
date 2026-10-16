package com.hireflow.candidate.grpc;

import java.util.UUID;

import com.hireflow.grpc.candidate.v1.CandidateGrpcServiceGrpc;
import com.hireflow.grpc.candidate.v1.CandidateResponse;
import com.hireflow.grpc.candidate.v1.GetCandidateRequest;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import org.springframework.stereotype.Component;

@Component
public class CandidateGrpcEndpoint extends CandidateGrpcServiceGrpc.CandidateGrpcServiceImplBase {

    private final com.hireflow.candidate.service.CandidateService candidateService;

    public CandidateGrpcEndpoint(com.hireflow.candidate.service.CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    @Override
    public void getCandidateById(
            GetCandidateRequest request, StreamObserver<CandidateResponse> responseObserver) {
        UUID candidateId;
        try {
            candidateId = UUID.fromString(request.getCandidateId());
        } catch (IllegalArgumentException ex) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("candidate_id must be a valid UUID")
                    .asRuntimeException());
            return;
        }

        try {
            var candidate = candidateService.getCandidateById(candidateId);
            responseObserver.onNext(CandidateResponse.newBuilder()
                    .setCandidateId(candidate.id().toString())
                    .setUserId(candidate.userId().toString())
                    .build());
            responseObserver.onCompleted();
        } catch (com.hireflow.candidate.exception.ResourceNotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Candidate was not found")
                    .asRuntimeException());
        } catch (RuntimeException ex) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Candidate lookup failed")
                    .asRuntimeException());
        }
    }
}
