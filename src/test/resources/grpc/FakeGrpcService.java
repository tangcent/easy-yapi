package com.itangcent.grpc.fake;

import com.itangcent.grpc.EchoRequest;
import com.itangcent.grpc.EchoResponse;
import io.grpc.stub.StreamObserver;

/**
 * A class with gRPC-style method signatures but NO {@code BindableService}
 * supertype and NO {@code @GrpcService} annotation.
 *
 * Used by the line-marker characterization test to verify PR1's
 * "MUST NOT consult rule engine" contract: even with a
 * {@code class.is.grpc = true} rule-engine override, the line marker
 * must NOT mark this class's methods as gRPC (because the line marker's
 * gRPC detection is purely structural &mdash; BindableService supertype
 * or @GrpcService annotation &mdash; not rule-driven).
 */
public class FakeGrpcService {

    /**
     * gRPC unary-style signature: {@code (Req, StreamObserver<Resp>) -> void}.
     * If the line marker incorrectly consulted the rule engine, this method
     * would be marked (because resolveStreamingType recognises the signature).
     */
    public void echo(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {
        // not a real gRPC service &mdash; no BindableService supertype
    }

    /**
     * gRPC streaming-style signature:
     * {@code (StreamObserver<Resp>) -> StreamObserver<Req>}.
     */
    public StreamObserver<EchoRequest> echoStream(StreamObserver<EchoResponse> responseObserver) {
        return new StreamObserver<EchoRequest>() {
            @Override
            public void onNext(EchoRequest value) {
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
            }
        };
    }

    /**
     * A plain method with no API annotations and no gRPC signature.
     */
    public String plainMethod() {
        return "not an API method";
    }
}
