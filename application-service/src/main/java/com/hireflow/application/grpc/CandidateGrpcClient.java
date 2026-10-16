package com.hireflow.application.grpc;

import java.util.UUID;

import com.hireflow.application.exception.CandidateServiceUnavailableException;
import com.hireflow.application.exception.CandidateServiceTimeoutException;
import com.hireflow.application.exception.InvalidRequestException;
import com.hireflow.application.exception.ResourceNotFoundException;
import com.hireflow.grpc.candidate.v1.CandidateGrpcServiceGrpc;
import com.hireflow.grpc.candidate.v1.GetCandidateRequest;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

public class CandidateGrpcClient {

    @FunctionalInterface
    public interface Lookup {
        com.hireflow.grpc.candidate.v1.CandidateResponse get(
                com.hireflow.grpc.candidate.v1.GetCandidateRequest request);
    }

    private final Lookup lookup;

    public CandidateGrpcClient(Lookup lookup) {
        this.lookup = lookup;
    }

    public CandidateIdentity getCandidateById(UUID candidateId) {
        if (candidateId == null) {
            throw new InvalidRequestException("candidateId must not be null");
        }
        try {
            var response = lookup.get(GetCandidateRequest.newBuilder()
                    .setCandidateId(candidateId.toString())
                    .build());
            UUID responseCandidateId = UUID.fromString(response.getCandidateId());
            UUID userId = UUID.fromString(response.getUserId());
            if (!candidateId.equals(responseCandidateId)) {
                throw new CandidateServiceUnavailableException();
            }
            return new CandidateIdentity(responseCandidateId, userId);
        } catch (StatusRuntimeException ex) {
            Status.Code code = ex.getStatus().getCode();
            if (code == Status.Code.INVALID_ARGUMENT) {
                throw new InvalidRequestException("candidateId must be a valid UUID");
            }
            if (code == Status.Code.NOT_FOUND) {
                throw new ResourceNotFoundException("No candidate found with id '" + candidateId + "'");
            }
            if (code == Status.Code.DEADLINE_EXCEEDED) {
                throw new CandidateServiceTimeoutException();
            }
            if (code == Status.Code.UNAUTHENTICATED || code == Status.Code.PERMISSION_DENIED
                    || code == Status.Code.UNAVAILABLE || code == Status.Code.INTERNAL) {
                throw new CandidateServiceUnavailableException();
            }
            throw new CandidateServiceUnavailableException();
        } catch (IllegalArgumentException ex) {
            throw new CandidateServiceUnavailableException();
        }
    }

    public record CandidateIdentity(UUID candidateId, UUID userId) {
    }
}
