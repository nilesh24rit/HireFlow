package com.hireflow.candidate.grpc;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import io.grpc.Server;
import io.grpc.ServerInterceptor;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

import org.springframework.context.SmartLifecycle;

public class CandidateGrpcServer implements SmartLifecycle {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    private final int port;
    private final CandidateGrpcEndpoint endpoint;
    private final boolean tlsEnabled;
    private final String certificatePath;
    private final String privateKeyPath;
    private final ServerInterceptor authenticationInterceptor;
    private volatile Server server;

    public CandidateGrpcServer(
            int port,
            CandidateGrpcEndpoint endpoint,
            boolean tlsEnabled,
            String certificatePath,
            String privateKeyPath,
            ServerInterceptor authenticationInterceptor) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("gRPC port must be between 0 and 65535");
        }
        if (tlsEnabled && (certificatePath == null || certificatePath.isBlank()
                || privateKeyPath == null || privateKeyPath.isBlank())) {
            throw new IllegalArgumentException("TLS requires externally configured certificate and private key paths");
        }
        this.port = port;
        this.endpoint = endpoint;
        this.tlsEnabled = tlsEnabled;
        this.certificatePath = certificatePath;
        this.privateKeyPath = privateKeyPath;
        this.authenticationInterceptor = authenticationInterceptor;
    }

    @Override
    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        try {
            NettyServerBuilder builder = NettyServerBuilder.forPort(port)
                    .addService(io.grpc.ServerInterceptors.intercept(endpoint, authenticationInterceptor));
            if (tlsEnabled) {
                builder.useTransportSecurity(new File(certificatePath), new File(privateKeyPath));
            }
            server = builder.build().start();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to start Candidate gRPC server", ex);
        }
    }

    @Override
    public synchronized void stop() {
        Server current = server;
        server = null;
        if (current != null) {
            current.shutdown();
            try {
                if (!current.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    current.shutdownNow();
                }
            } catch (InterruptedException ex) {
                current.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void stop(Runnable callback) {
        try {
            stop();
        } finally {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        Server current = server;
        return current != null && !current.isShutdown();
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    public int getPort() {
        Server current = server;
        return current == null ? port : current.getPort();
    }
}
